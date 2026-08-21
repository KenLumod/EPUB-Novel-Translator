package com.example.epubnoveltranslator.ui.screens.conversation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.epubnoveltranslator.data.UserPreferences
import com.example.epubnoveltranslator.data.db.ChapterEntity
import com.example.epubnoveltranslator.data.db.GlossaryKind
import com.example.epubnoveltranslator.data.db.GlossaryTermEntity
import com.example.epubnoveltranslator.data.db.NovelEntity
import com.example.epubnoveltranslator.data.model.ModelManager
import com.example.epubnoveltranslator.data.queue.TranslationEvent
import com.example.epubnoveltranslator.data.queue.TranslationQueueManager
import com.example.epubnoveltranslator.data.repository.NovelRepository
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
        val replacementGlossaryTerms: List<GlossaryTermEntity> = emptyList(),
        val novelNotes: String = ""
    ) : ConversationUiState()
    data class Error(val message: String) : ConversationUiState()
}

class ConversationViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = NovelRepository(application)
    private val modelManager = ModelManager.getInstance(application)
    private val preferences = UserPreferences(application)
    private val queueManager = TranslationQueueManager.getInstance(application)

    private val _uiState = MutableStateFlow<ConversationUiState>(ConversationUiState.Initializing)
    val uiState: StateFlow<ConversationUiState> = _uiState.asStateFlow()

    private var currentNovelId: String? = null
    private var currentChapterId: String? = null
    private var novelEntity: NovelEntity? = null
    private var chapterEntity: ChapterEntity? = null

    init {
        viewModelScope.launch {
            queueManager.translationEvents.collect { event ->
                handleTranslationEvent(event)
            }
        }
    }

    fun loadConversation(novelId: String, chapterId: String) {
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

            val replacements = repository.getGlossaryListForNovel(novel.id, GlossaryKind.REPLACEMENT)
            val promptTerms = repository.getGlossaryListForNovel(novel.id, GlossaryKind.PROMPT)

            if (queueManager.isChapterActiveOrQueued(chapterId)) {
                val streamed = queueManager.getActiveStreamedText(chapterId) ?: ""
                val isActivelyTranslating = queueManager.isChapterActive(chapterId)
                val statusMsg = if (isActivelyTranslating) "Translating on-device..." else "Queued in FIFO translation queue..."
                _uiState.value = ConversationUiState.Content(
                    novelTitle = novel.title,
                    chapterTitle = chapter.title,
                    messages = listOf(
                        ChatItem("1", "System", "Loaded chapter: ${chapter.title}", false),
                        ChatItem("2", currentModelName(), streamed, true, replacements)
                    ),
                    isStreaming = true,
                    isCached = false,
                    statusMessage = statusMsg,
                    meaningSearchEnabled = preferences.meaningSearchEnabled,
                    promptGlossaryTerms = promptTerms,
                    replacementGlossaryTerms = replacements,
                    novelNotes = novel.notes
                )
            } else if (chapter.isTranslated && !chapter.translatedText.isNullOrBlank()) {
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
                    replacementGlossaryTerms = replacements,
                    novelNotes = novel.notes
                )
            } else {
                startTranslation(forceRefresh = false)
            }
        }
    }

    fun startTranslation(forceRefresh: Boolean = false) {
        val novel = novelEntity ?: return
        val chapter = chapterEntity ?: return
        queueManager.enqueue(novel.id, chapter.id, forceRefresh)
    }

    fun cancelTranslation() {
        val chapterId = currentChapterId ?: return
        queueManager.cancel(chapterId)
    }

    private suspend fun handleTranslationEvent(event: TranslationEvent) {
        val activeChapter = currentChapterId ?: return
        val novel = novelEntity ?: return
        val chapter = chapterEntity ?: return

        when (event) {
            is TranslationEvent.Queued -> {
                if (event.chapterId == activeChapter) {
                    val replacements = repository.getGlossaryListForNovel(novel.id, GlossaryKind.REPLACEMENT)
                    val promptTerms = repository.getGlossaryListForNovel(novel.id, GlossaryKind.PROMPT)
                    _uiState.value = ConversationUiState.Content(
                        novelTitle = novel.title,
                        chapterTitle = chapter.title,
                        messages = listOf(
                            ChatItem("1", "System", "Loaded chapter: ${chapter.title}", false),
                            ChatItem("2", currentModelName(), "Queued in FIFO translation queue...", true, replacements)
                        ),
                        isStreaming = true,
                        isCached = false,
                        statusMessage = "Queued in FIFO translation queue...",
                        meaningSearchEnabled = preferences.meaningSearchEnabled,
                        promptGlossaryTerms = promptTerms,
                        replacementGlossaryTerms = replacements,
                        novelNotes = novelEntity?.notes ?: novel.notes
                    )
                }
            }

            is TranslationEvent.Started -> {
                if (event.chapterId == activeChapter) {
                    val replacements = repository.getGlossaryListForNovel(novel.id, GlossaryKind.REPLACEMENT)
                    val promptTerms = repository.getGlossaryListForNovel(novel.id, GlossaryKind.PROMPT)
                    _uiState.value = ConversationUiState.Content(
                        novelTitle = novel.title,
                        chapterTitle = chapter.title,
                        messages = listOf(
                            ChatItem("1", "System", "Chapter loaded: ${chapter.title}", false),
                            ChatItem("2", currentModelName(), "", true, replacements)
                        ),
                        isStreaming = true,
                        isCached = false,
                        statusMessage = "Translating on-device...",
                        meaningSearchEnabled = preferences.meaningSearchEnabled,
                        promptGlossaryTerms = promptTerms,
                        replacementGlossaryTerms = replacements,
                        novelNotes = novelEntity?.notes ?: novel.notes
                    )
                }
            }

            is TranslationEvent.Progress -> {
                if (event.chapterId == activeChapter) {
                    val liveReplacements = repository.getGlossaryListForNovel(novel.id, GlossaryKind.REPLACEMENT)
                    val livePromptTerms = repository.getGlossaryListForNovel(novel.id, GlossaryKind.PROMPT)
                    val updatedMessages = listOf(
                        ChatItem("1", "System", "Chapter loaded: ${chapter.title}", false),
                        ChatItem("2", currentModelName(), event.streamedText, true, liveReplacements)
                    )
                    _uiState.value = ConversationUiState.Content(
                        novelTitle = novel.title,
                        chapterTitle = chapter.title,
                        messages = updatedMessages,
                        isStreaming = true,
                        isCached = false,
                        statusMessage = "Translating part ${event.currentPart} of ${event.totalParts}...",
                        meaningSearchEnabled = preferences.meaningSearchEnabled,
                        promptGlossaryTerms = livePromptTerms,
                        replacementGlossaryTerms = liveReplacements,
                        novelNotes = novelEntity?.notes ?: novel.notes
                    )
                }
            }

            is TranslationEvent.Completed -> {
                if (event.chapterId == activeChapter) {
                    chapterEntity = chapter.copy(translatedText = event.finalText, isTranslated = true)
                    val latestReplacements = repository.getGlossaryListForNovel(novel.id, GlossaryKind.REPLACEMENT)
                    val latestPromptTerms = repository.getGlossaryListForNovel(novel.id, GlossaryKind.PROMPT)
                    _uiState.value = ConversationUiState.Content(
                        novelTitle = novel.title,
                        chapterTitle = chapter.title,
                        messages = listOf(
                            ChatItem("1", "System", "Chapter loaded: ${chapter.title}", false),
                            ChatItem("2", currentModelName(), event.finalText, true, latestReplacements)
                        ),
                        isStreaming = false,
                        isCached = true,
                        statusMessage = "Translation complete • Saved to Room cache",
                        meaningSearchEnabled = preferences.meaningSearchEnabled,
                        promptGlossaryTerms = latestPromptTerms,
                        replacementGlossaryTerms = latestReplacements,
                        novelNotes = novelEntity?.notes ?: novel.notes
                    )
                }
            }

            is TranslationEvent.Failed -> {
                if (event.chapterId == activeChapter) {
                    val replacements = repository.getGlossaryListForNovel(novel.id, GlossaryKind.REPLACEMENT)
                    val promptTerms = repository.getGlossaryListForNovel(novel.id, GlossaryKind.PROMPT)
                    _uiState.value = ConversationUiState.Content(
                        novelTitle = novel.title,
                        chapterTitle = chapter.title,
                        messages = listOf(
                            ChatItem("1", "System", "Loaded chapter: ${chapter.title}", false),
                            ChatItem("2", currentModelName(), "Translation error: ${event.error}", true, replacements)
                        ),
                        isStreaming = false,
                        isCached = false,
                        statusMessage = "Translation failed",
                        meaningSearchEnabled = preferences.meaningSearchEnabled,
                        promptGlossaryTerms = promptTerms,
                        replacementGlossaryTerms = replacements,
                        novelNotes = novelEntity?.notes ?: novel.notes
                    )
                }
            }

            is TranslationEvent.Cancelled -> {
                if (event.chapterId == activeChapter) {
                    val current = _uiState.value as? ConversationUiState.Content ?: return
                    _uiState.value = current.copy(
                        isStreaming = false,
                        statusMessage = "Translation stopped"
                    )
                }
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
            refreshGlossaryTerms(novelId)
        }
    }

    fun addGlossaryTerm(sourceTerm: String, targetTerm: String, note: String, kind: String) {
        val novelId = currentNovelId ?: return
        viewModelScope.launch {
            repository.addGlossaryTerm(
                GlossaryTermEntity(
                    id = UUID.randomUUID().toString(),
                    novelId = novelId,
                    sourceTerm = sourceTerm.trim(),
                    targetTerm = targetTerm.trim(),
                    note = note.trim(),
                    kind = kind
                )
            )
            refreshGlossaryTerms(novelId)
        }
    }

    fun updateGlossaryTerm(term: GlossaryTermEntity) {
        val novelId = currentNovelId ?: return
        viewModelScope.launch {
            repository.addGlossaryTerm(term)
            refreshGlossaryTerms(novelId)
        }
    }

    fun deleteGlossaryTerm(term: GlossaryTermEntity) {
        val novelId = currentNovelId ?: return
        viewModelScope.launch {
            repository.deleteGlossaryTerm(term)
            refreshGlossaryTerms(novelId)
        }
    }

    private suspend fun refreshGlossaryTerms(novelId: String) {
        val current = _uiState.value as? ConversationUiState.Content ?: return
        val replacementTerms = repository.getGlossaryListForNovel(novelId, GlossaryKind.REPLACEMENT)
        val promptTerms = repository.getGlossaryListForNovel(novelId, GlossaryKind.PROMPT)
        _uiState.value = current.copy(
            messages = current.messages.map {
                if (it.isModel) it.copy(replacementTerms = replacementTerms) else it
            },
            promptGlossaryTerms = promptTerms,
            replacementGlossaryTerms = replacementTerms
        )
    }

    fun refreshInteractionPreferences() {
        val current = _uiState.value as? ConversationUiState.Content ?: return
        _uiState.value = current.copy(meaningSearchEnabled = preferences.meaningSearchEnabled)
    }

    fun updateNotes(notes: String) {
        val novelId = currentNovelId ?: return
        novelEntity = novelEntity?.copy(notes = notes)
        viewModelScope.launch {
            repository.updateNovelNotes(novelId, notes)
        }
        val current = _uiState.value as? ConversationUiState.Content ?: return
        _uiState.value = current.copy(novelNotes = notes)
    }

    private fun currentModelName(): String = modelManager.modelInfo.value.localFilePath
        ?.let { modelManager.modelInfo.value.name }
        ?: "Model"
}
