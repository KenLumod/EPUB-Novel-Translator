package com.example.epubnoveltranslator.ui.screens.conversation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.epubnoveltranslator.data.db.ChapterEntity
import com.example.epubnoveltranslator.data.db.GlossaryTermEntity
import com.example.epubnoveltranslator.data.db.GlossaryKind
import com.example.epubnoveltranslator.data.UserPreferences
import com.example.epubnoveltranslator.data.db.NovelEntity
import com.example.epubnoveltranslator.data.llm.LiteRtLmSession
import com.example.epubnoveltranslator.data.model.ModelManager
import com.example.epubnoveltranslator.data.repository.NovelRepository
import com.example.epubnoveltranslator.ui.screens.detail.DEFAULT_PROMPT_TEMPLATE
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class ChatItem(
    val id: String,
    val sender: String,
    val content: String,
    val isModel: Boolean,
    val replacementTerms: List<GlossaryTermEntity> = emptyList()
)

sealed class ConversationUiState {
    object Initializing : ConversationUiState()
    data class Content(
        val novelTitle: String,
        val chapterTitle: String,
        val messages: List<ChatItem>,
        val isStreaming: Boolean = false,
        val isCached: Boolean = false,
        val statusMessage: String = "",
        val meaningSearchEnabled: Boolean = false,
        val promptGlossaryTerms: List<GlossaryTermEntity> = emptyList(),
        val replacementGlossaryTerms: List<GlossaryTermEntity> = emptyList()
    ) : ConversationUiState()
    data class Error(val message: String) : ConversationUiState()
}

class ConversationViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = NovelRepository(application)
    private val modelManager = ModelManager.getInstance(application)
    private val llmSession = LiteRtLmSession(application)
    private val preferences = UserPreferences(application)

    private val _uiState = MutableStateFlow<ConversationUiState>(ConversationUiState.Initializing)
    val uiState: StateFlow<ConversationUiState> = _uiState.asStateFlow()

    private var currentNovelId: String? = null
    private var currentChapterId: String? = null
    private var novelEntity: NovelEntity? = null
    private var chapterEntity: ChapterEntity? = null
    private var loadedModelPath: String? = null

    fun loadConversation(novelId: String, chapterId: String) {
        val currentContent = _uiState.value as? ConversationUiState.Content
        if (
            currentNovelId == novelId && currentChapterId == chapterId &&
            currentContent?.isStreaming == true
        ) {
            // Returning to this chapter reuses the still-running activity-scoped job.
            return
        }
        currentNovelId = novelId
        currentChapterId = chapterId

        viewModelScope.launch {
            novelEntity = repository.getNovelById(novelId)
            chapterEntity = repository.getChapterById(chapterId)

            val chapter = chapterEntity
            val novel = novelEntity

            if (chapter == null || novel == null) {
                _uiState.value = ConversationUiState.Error("Chapter or Novel not found.")
                return@launch
            }

            // Check if translation is already cached in Room DB
            if (chapter.isTranslated && !chapter.translatedText.isNullOrBlank()) {
                val replacements = repository.getGlossaryListForNovel(novel.id, GlossaryKind.REPLACEMENT)
                val promptTerms = repository.getGlossaryListForNovel(novel.id, GlossaryKind.PROMPT)
                val cachedMessages = listOf(
                    ChatItem("1", "System", "Loaded chapter: ${chapter.title}", false),
                    ChatItem("2", currentModelName(), chapter.translatedText, true, replacements)
                )
                _uiState.value = ConversationUiState.Content(
                    novelTitle = novel.title,
                    chapterTitle = chapter.title,
                    messages = cachedMessages,
                    isStreaming = false,
                    isCached = true,
                    statusMessage = "Loaded from Room cache",
                    meaningSearchEnabled = preferences.meaningSearchEnabled,
                    promptGlossaryTerms = promptTerms,
                    replacementGlossaryTerms = replacements
                )
            } else {
                startTranslation(forceRefresh = false)
            }
        }
    }

    fun startTranslation(forceRefresh: Boolean = false) {
        val novel = novelEntity ?: return
        val chapter = chapterEntity ?: return

        viewModelScope.launch {
            TranslationProgress.start(chapter.id)
            try {
            val path = modelManager.modelInfo.value.localFilePath
            if (!llmSession.isLoaded() || loadedModelPath != path) {
                if (llmSession.isLoaded()) llmSession.close()

                if (path == null || !java.io.File(path).exists()) {
                    _uiState.value = ConversationUiState.Content(
                        novelTitle = novel.title,
                        chapterTitle = chapter.title,
                        messages = listOf(
                            ChatItem("1", "System", "Loaded chapter: ${chapter.title}", false),
                            ChatItem("2", "Model", "Error: No .litertlm model file uploaded. Please upload a model file in the Models tab first.", true)
                        ),
                        isStreaming = false,
                        isCached = false,
                        statusMessage = "Missing .litertlm model"
                    )
                    return@launch
                }

                _uiState.value = ConversationUiState.Content(
                    novelTitle = novel.title,
                    chapterTitle = chapter.title,
                    messages = listOf(
                        ChatItem("1", "System", "Loaded chapter: ${chapter.title}", false),
                        ChatItem("2", currentModelName(), "Loading model into memory...", true)
                    ),
                    isStreaming = true,
                    isCached = false,
                    statusMessage = "Loading model session..."
                )

                val loadResult = llmSession.loadModelSession(path)
                if (loadResult.isFailure) {
                    val err = loadResult.exceptionOrNull()?.localizedMessage ?: "Failed to initialize .litertlm model."
                    _uiState.value = ConversationUiState.Content(
                        novelTitle = novel.title,
                        chapterTitle = chapter.title,
                        messages = listOf(
                            ChatItem("1", "System", "Loaded chapter: ${chapter.title}", false),
                            ChatItem("2", currentModelName(), "Error initializing model: $err", true)
                        ),
                        isStreaming = false,
                        isCached = false,
                        statusMessage = "Model load failed"
                    )
                    return@launch
                }
                loadedModelPath = path
            }

            val promptGlossaryTerms = repository.getGlossaryListForNovel(novel.id, GlossaryKind.PROMPT)
            val replacementTerms = repository.getGlossaryListForNovel(novel.id, GlossaryKind.REPLACEMENT)
            val promptTemplate = novel.customPromptTemplate ?: DEFAULT_PROMPT_TEMPLATE.trimIndent()

            val chapterChunks = splitChapterIntoChunks(chapter.rawText)
            if (chapterChunks.isEmpty()) {
                _uiState.value = ConversationUiState.Error("This chapter has no readable text to translate.")
                return@launch
            }

            // A chapter must not inherit turns from a previously opened chapter.
            val newConversation = llmSession.startNewConversation()
            if (newConversation.isFailure) {
                _uiState.value = ConversationUiState.Error(
                    newConversation.exceptionOrNull()?.localizedMessage
                        ?: "Unable to start a translation conversation."
                )
                return@launch
            }

            val messages = mutableListOf(
                ChatItem(
                    id = "1",
                    sender = "System",
                    content = "Chapter loaded: ${chapter.title}\nApplied ${promptGlossaryTerms.size} prompt rules and ${replacementTerms.size} replacement rules.",
                    isModel = false
                ),
                ChatItem(
                    id = "2",
                    sender = currentModelName(),
                    content = "",
                    isModel = true
                )
            )

            _uiState.value = ConversationUiState.Content(
                novelTitle = novel.title,
                chapterTitle = chapter.title,
                messages = messages,
                isStreaming = true,
                isCached = false,
                statusMessage = "Translating on-device...",
                meaningSearchEnabled = preferences.meaningSearchEnabled,
                promptGlossaryTerms = promptGlossaryTerms,
                replacementGlossaryTerms = replacementTerms
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
                    val formattedPrompt = buildFinalPrompt(promptTemplate, promptGlossaryTerms, chunk)
                    llmSession.generateResponseStream(formattedPrompt).collect { token ->
                        responseForChunk.append(token)
                        val visibleTranslation = (translatedChunks + responseForChunk.toString())
                            .joinToString("\n\n")
                        val updatedMessages = messages.toMutableList()
                        updatedMessages[1] = ChatItem("2", currentModelName(), visibleTranslation, true, replacementTerms)

                        _uiState.value = ConversationUiState.Content(
                            novelTitle = novel.title,
                            chapterTitle = chapter.title,
                            messages = updatedMessages,
                            isStreaming = true,
                            isCached = false,
                            statusMessage = "Translating part ${index + 1} of ${chapterChunks.size}...",
                            meaningSearchEnabled = preferences.meaningSearchEnabled,
                            promptGlossaryTerms = promptGlossaryTerms,
                            replacementGlossaryTerms = replacementTerms
                        )
                    }

                    val completedChunk = cleanModelText(responseForChunk.toString())
                    if (completedChunk.isBlank()) {
                        throw IllegalStateException("The model returned no text for part ${index + 1}.")
                    }
                    translatedChunks += completedChunk
                }
            } catch (error: Throwable) {
                translationError = error.localizedMessage ?: error.message ?: "Unknown inference error"
            }

            val finalText = translatedChunks.joinToString("\n\n").trim()
            val isError = translationError != null || finalText.isBlank()

            if (!isError) {
                // Save completed successful translation into Room DB cache
                repository.updateChapterTranslation(chapter.id, finalText)

                _uiState.value = ConversationUiState.Content(
                    novelTitle = novel.title,
                    chapterTitle = chapter.title,
                    messages = listOf(
                        messages[0],
                        ChatItem("2", currentModelName(), finalText, true, replacementTerms)
                    ),
                    isStreaming = false,
                    isCached = true,
                    statusMessage = "Translation complete • Saved to Room cache",
                    meaningSearchEnabled = preferences.meaningSearchEnabled,
                    promptGlossaryTerms = promptGlossaryTerms,
                    replacementGlossaryTerms = replacementTerms
                )
            } else {
                _uiState.value = ConversationUiState.Content(
                    novelTitle = novel.title,
                    chapterTitle = chapter.title,
                    messages = listOf(
                        messages[0],
                        ChatItem(
                            "2",
                            currentModelName(),
                            "Translation error: ${translationError ?: "No translated text was returned."}",
                            true
                        )
                    ),
                    isStreaming = false,
                    isCached = false,
                    statusMessage = "Translation failed"
                )
            }
            } finally {
                TranslationProgress.finish(chapter.id)
            }
        }
    }

    /** Called by the selection toolbar after the reader supplies a preferred replacement. */
    fun addReplacementFromOutput(source: String, target: String) {
        val novelId = currentNovelId ?: return
        viewModelScope.launch {
            repository.addGlossaryTerm(
                GlossaryTermEntity(
                    id = UUID.randomUUID().toString(),
                    novelId = novelId,
                    sourceTerm = source,
                    targetTerm = target,
                    kind = GlossaryKind.REPLACEMENT
                )
            )
            // Re-render a cached/finished response immediately with the new replacement.
            val current = _uiState.value as? ConversationUiState.Content ?: return@launch
            val terms = repository.getGlossaryListForNovel(novelId, GlossaryKind.REPLACEMENT)
            _uiState.value = current.copy(messages = current.messages.map {
                if (it.isModel) it.copy(replacementTerms = terms) else it
            }, replacementGlossaryTerms = terms)
        }
    }

    fun refreshInteractionPreferences() {
        val current = _uiState.value as? ConversationUiState.Content ?: return
        _uiState.value = current.copy(meaningSearchEnabled = preferences.meaningSearchEnabled)
    }

    override fun onCleared() {
        // Closing LiteRT-LM while its native generator is active can crash the process.
        // The process will reclaim it if the activity is actually destroyed mid-stream.
        if ((_uiState.value as? ConversationUiState.Content)?.isStreaming != true) {
            llmSession.close()
        }
        loadedModelPath = null
        super.onCleared()
    }

    private fun buildFinalPrompt(
        template: String,
        glossary: List<GlossaryTermEntity>,
        chapterText: String
    ): String {
        val glossaryBlock = if (glossary.isEmpty()) {
            "No custom glossary terms provided."
        } else {
            glossary.joinToString("\n") { term ->
                "- ${term.sourceTerm} ➔ ${term.targetTerm}${if (term.note.isNotEmpty()) " (${term.note})" else ""}"
            }
        }

        val prompt = template
            .replace("{glossary_block}", glossaryBlock)
        return if (prompt.contains("{chapter_text}")) {
            prompt.replace("{chapter_text}", chapterText)
        } else {
            // Custom templates may omit the placeholder. Never let that make the model
            // translate an instruction with no chapter source.
            "$prompt\n\n<source_chapter>\n$chapterText\n</source_chapter>"
        }
    }

    private fun cleanModelText(text: String): String = text
        .replace(Regex("```[a-zA-Z]*\\n?"), "")
        .replace("```", "")
        .replace("\\$", "")
        .trim()

    private fun currentModelName(): String = modelManager.modelInfo.value.localFilePath
        ?.let { modelManager.modelInfo.value.name }
        ?: "Model"

    /**
     * LiteRT-LM counts both prompt and generated text in its context window.  Keeping
     * source chunks near 1,800 characters leaves room for English output on 4K windows.
     */
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
