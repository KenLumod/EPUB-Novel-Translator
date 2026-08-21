package com.example.epubnoveltranslator.data.queue

import android.content.Context
import com.example.epubnoveltranslator.data.db.GlossaryKind
import com.example.epubnoveltranslator.data.db.GlossaryTermEntity
import com.example.epubnoveltranslator.data.llm.LiteRtLmSession
import com.example.epubnoveltranslator.data.model.ModelManager
import com.example.epubnoveltranslator.data.repository.NovelRepository
import com.example.epubnoveltranslator.ui.screens.conversation.TranslationForegroundService
import com.example.epubnoveltranslator.ui.screens.conversation.TranslationProgress
import com.example.epubnoveltranslator.ui.screens.detail.DEFAULT_PROMPT_TEMPLATE
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.ArrayDeque

data class QueueTask(
    val novelId: String,
    val chapterId: String,
    val forceRefresh: Boolean = false
)

sealed class TranslationEvent {
    data class Queued(val chapterId: String) : TranslationEvent()
    data class Started(
        val novelId: String,
        val chapterId: String,
        val novelTitle: String,
        val chapterTitle: String
    ) : TranslationEvent()
    data class Progress(
        val chapterId: String,
        val currentPart: Int,
        val totalParts: Int,
        val streamedText: String
    ) : TranslationEvent()
    data class Completed(val chapterId: String, val finalText: String) : TranslationEvent()
    data class Failed(val chapterId: String, val error: String) : TranslationEvent()
    data class Cancelled(val chapterId: String) : TranslationEvent()
}

/**
 * Headless singleton FIFO queue manager for chapter translation.
 * Enforces strict single-threaded inference for LiteRT-LM and automatically
 * advances to the next queued chapter upon completion or cancellation.
 */
class TranslationQueueManager private constructor(private val context: Context) {

    private val repository = NovelRepository(context)
    private val modelManager = ModelManager.getInstance(context)
    private val llmSession = LiteRtLmSession(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val queueLock = Any()
    private val taskQueue = ArrayDeque<QueueTask>()
    private var activeTask: QueueTask? = null
    private var activeJob: Job? = null
    private var loadedModelPath: String? = null

    // Cache latest streamed progress for active chapter so opening its screen shows ongoing state
    private val latestProgressMap = mutableMapOf<String, String>()

    private val _activeChapterIds = kotlinx.coroutines.flow.MutableStateFlow<Set<String>>(emptySet())
    val activeChapterIds: kotlinx.coroutines.flow.StateFlow<Set<String>> = _activeChapterIds.asStateFlow()

    private val _queuedChapterIds = kotlinx.coroutines.flow.MutableStateFlow<Set<String>>(emptySet())
    val queuedChapterIds: kotlinx.coroutines.flow.StateFlow<Set<String>> = _queuedChapterIds.asStateFlow()

    private val _translationEvents = MutableSharedFlow<TranslationEvent>(replay = 1, extraBufferCapacity = 64)
    val translationEvents: SharedFlow<TranslationEvent> = _translationEvents.asSharedFlow()

    companion object {
        @Volatile
        private var instance: TranslationQueueManager? = null

        fun getInstance(context: Context): TranslationQueueManager {
            return instance ?: synchronized(this) {
                instance ?: TranslationQueueManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private fun updateQueueStateFlowsLocked() {
        val active = activeTask?.chapterId
        val activeSet = if (active != null) setOf(active) else emptySet()
        val queuedSet = taskQueue.map { it.chapterId }.toSet()
        _activeChapterIds.value = activeSet
        _queuedChapterIds.value = queuedSet
        TranslationProgress.setActive(activeSet + queuedSet)
    }

    fun enqueue(novelId: String, chapterId: String, forceRefresh: Boolean = false) {
        synchronized(queueLock) {
            if (activeTask?.chapterId == chapterId) {
                if (!forceRefresh) return
                // Force refresh on active task: cancel and re-queue
                activeJob?.cancel()
                activeJob = null
                activeTask = null
            } else if (taskQueue.any { it.chapterId == chapterId }) {
                return
            }

            val task = QueueTask(novelId, chapterId, forceRefresh)
            taskQueue.addLast(task)
            updateQueueStateFlowsLocked()
            _translationEvents.tryEmit(TranslationEvent.Queued(chapterId))
            processNextIfNeededLocked()
        }
    }

    fun cancel(chapterId: String) {
        synchronized(queueLock) {
            if (activeTask?.chapterId == chapterId) {
                activeJob?.cancel()
                activeJob = null
                activeTask = null
                latestProgressMap.remove(chapterId)
                updateQueueStateFlowsLocked()
                _translationEvents.tryEmit(TranslationEvent.Cancelled(chapterId))
                processNextIfNeededLocked()
            } else {
                val removed = taskQueue.removeIf { it.chapterId == chapterId }
                if (removed) {
                    latestProgressMap.remove(chapterId)
                    updateQueueStateFlowsLocked()
                    _translationEvents.tryEmit(TranslationEvent.Cancelled(chapterId))
                }
                if (activeTask == null && taskQueue.isEmpty()) {
                    context.startService(TranslationForegroundService.buildStopIntent(context))
                }
            }
        }
    }

    fun isChapterActive(chapterId: String): Boolean = synchronized(queueLock) {
        activeTask?.chapterId == chapterId
    }

    fun isChapterActiveOrQueued(chapterId: String): Boolean = synchronized(queueLock) {
        activeTask?.chapterId == chapterId || taskQueue.any { it.chapterId == chapterId }
    }

    fun getActiveStreamedText(chapterId: String): String? = synchronized(queueLock) {
        latestProgressMap[chapterId]
    }

    private fun processNextIfNeededLocked() {
        if (activeTask != null) return

        if (taskQueue.isEmpty()) {
            updateQueueStateFlowsLocked()
            context.startService(TranslationForegroundService.buildStopIntent(context))
            return
        }

        val nextTask = taskQueue.removeFirst()
        activeTask = nextTask
        updateQueueStateFlowsLocked()

        activeJob = scope.launch {
            try {
                executeTranslation(nextTask)
            } finally {
                synchronized(queueLock) {
                    latestProgressMap.remove(nextTask.chapterId)
                    activeTask = null
                    activeJob = null
                    updateQueueStateFlowsLocked()
                    processNextIfNeededLocked()
                }
            }
        }
    }

    private suspend fun executeTranslation(task: QueueTask) {
        val novel = repository.getNovelById(task.novelId)
        val chapter = repository.getChapterById(task.chapterId)

        if (novel == null || chapter == null) {
            _translationEvents.emit(
                TranslationEvent.Failed(task.chapterId, "Chapter or Novel not found.")
            )
            return
        }

        // Start / update foreground service notification
        context.startForegroundService(
            TranslationForegroundService.buildStartIntent(context, novel.title, chapter.title)
        )

        // Check if already translated and not force-refreshed
        if (chapter.isTranslated && !chapter.translatedText.isNullOrBlank() && !task.forceRefresh) {
            _translationEvents.emit(
                TranslationEvent.Completed(chapter.id, chapter.translatedText)
            )
            return
        }

        val path = modelManager.modelInfo.value.localFilePath
        if (path == null || !File(path).exists()) {
            _translationEvents.emit(
                TranslationEvent.Failed(
                    chapter.id,
                    "Error: No .litertlm model file uploaded. Please upload a model file in the Models tab first."
                )
            )
            return
        }

        if (!llmSession.isLoaded() || loadedModelPath != path) {
            if (llmSession.isLoaded()) llmSession.close()
            val loadResult = llmSession.loadModelSession(path)
            if (loadResult.isFailure) {
                val err = loadResult.exceptionOrNull()?.localizedMessage ?: "Failed to initialize .litertlm model."
                _translationEvents.emit(TranslationEvent.Failed(chapter.id, "Error initializing model: $err"))
                return
            }
            loadedModelPath = path
        }

        val promptGlossaryTerms = repository.getGlossaryListForNovel(novel.id, GlossaryKind.PROMPT)
        val promptTemplate = novel.customPromptTemplate ?: DEFAULT_PROMPT_TEMPLATE.trimIndent()
        val chapterChunks = splitChapterIntoChunks(chapter.rawText)

        if (chapterChunks.isEmpty()) {
            _translationEvents.emit(TranslationEvent.Failed(chapter.id, "This chapter has no readable text to translate."))
            return
        }

        val newConversation = llmSession.startNewConversation()
        if (newConversation.isFailure) {
            _translationEvents.emit(
                TranslationEvent.Failed(
                    chapter.id,
                    newConversation.exceptionOrNull()?.localizedMessage ?: "Unable to start translation session."
                )
            )
            return
        }

        _translationEvents.emit(
            TranslationEvent.Started(novel.id, chapter.id, novel.title, chapter.title)
        )

        val translatedChunks = mutableListOf<String>()
        var translationError: String? = null

        try {
            chapterChunks.forEachIndexed { index, chunk ->
                if (index > 0) {
                    val resetResult = llmSession.startNewConversation()
                    if (resetResult.isFailure) {
                        throw resetResult.exceptionOrNull()
                            ?: IllegalStateException("Unable to start the next translation chunk.")
                    }
                }

                val responseForChunk = StringBuilder()
                val livePromptTerms = repository.getGlossaryListForNovel(novel.id, GlossaryKind.PROMPT)
                val formattedPrompt = buildFinalPrompt(promptTemplate, livePromptTerms, chunk)

                llmSession.generateResponseStream(formattedPrompt).collect { token ->
                    responseForChunk.append(token)
                    val visibleTranslation = (translatedChunks + responseForChunk.toString()).joinToString("\n\n")
                    synchronized(queueLock) {
                        latestProgressMap[chapter.id] = visibleTranslation
                    }
                    _translationEvents.emit(
                        TranslationEvent.Progress(
                            chapterId = chapter.id,
                            currentPart = index + 1,
                            totalParts = chapterChunks.size,
                            streamedText = visibleTranslation
                        )
                    )
                }

                val completedChunk = cleanModelText(responseForChunk.toString())
                if (completedChunk.isBlank()) {
                    throw IllegalStateException("The model returned no text for part ${index + 1}.")
                }
                translatedChunks += completedChunk
            }
        } catch (ce: CancellationException) {
            _translationEvents.emit(TranslationEvent.Cancelled(chapter.id))
            throw ce
        } catch (error: Throwable) {
            translationError = error.localizedMessage ?: error.message ?: "Unknown inference error"
        }

        val finalText = translatedChunks.joinToString("\n\n").trim()
        val isError = translationError != null || finalText.isBlank()

        if (!isError) {
            repository.updateChapterTranslation(chapter.id, finalText)
            _translationEvents.emit(TranslationEvent.Completed(chapter.id, finalText))
        } else {
            _translationEvents.emit(
                TranslationEvent.Failed(
                    chapter.id,
                    "Translation error: ${translationError ?: "No translated text was returned."}"
                )
            )
        }
    }

    private fun buildFinalPrompt(
        template: String,
        glossary: List<GlossaryTermEntity>,
        chapterText: String
    ): String {
        val glossaryBlock = if (glossary.isEmpty()) {
            "No custom glossary rules provided."
        } else {
            glossary.joinToString("\n") { term ->
                "- ${term.sourceTerm} ➔ ${term.targetTerm}${if (term.note.isNotEmpty()) " (${term.note})" else ""}"
            }
        }

        val prompt = template.replace("{glossary_block}", glossaryBlock)
        return if (prompt.contains("{chapter_text}")) {
            prompt.replace("{chapter_text}", chapterText)
        } else {
            "$prompt\n\n<source_chapter>\n$chapterText\n</source_chapter>"
        }
    }

    private fun cleanModelText(text: String): String = text
        .replace(Regex("```[a-zA-Z]*\\n?"), "")
        .replace("```", "")
        .replace("\\$", "")
        .trim()

    private fun splitChapterIntoChunks(text: String, maxCharacters: Int = 1_800): List<String> {
        val paragraphs = text
            .replace("\r\n", "\n")
            .split(Regex("\\n\\s*\\n"))
            .map(String::trim)
            .filter(String::isNotBlank)

        val chunks = mutableListOf<String>()
        val currentChunk = StringBuilder()

        fun addCurrentChunk() {
            if (currentChunk.isNotBlank()) {
                chunks += currentChunk.toString().trim()
                currentChunk.clear()
            }
        }

        paragraphs.forEach { paragraph ->
            if (paragraph.length > maxCharacters) {
                addCurrentChunk()
                paragraph.chunked(maxCharacters).forEach { chunks += it }
            } else if (currentChunk.isNotEmpty() && currentChunk.length + paragraph.length + 2 > maxCharacters) {
                addCurrentChunk()
                currentChunk.append(paragraph)
            } else {
                if (currentChunk.isNotEmpty()) currentChunk.append("\n\n")
                currentChunk.append(paragraph)
            }
        }
        addCurrentChunk()
        return chunks
    }
}
