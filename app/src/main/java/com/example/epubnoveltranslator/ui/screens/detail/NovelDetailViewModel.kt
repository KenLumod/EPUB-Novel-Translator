package com.example.epubnoveltranslator.ui.screens.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.epubnoveltranslator.data.db.ChapterEntity
import com.example.epubnoveltranslator.data.db.GlossaryTermEntity
import com.example.epubnoveltranslator.data.db.GlossaryKind
import com.example.epubnoveltranslator.data.db.NovelEntity
import com.example.epubnoveltranslator.data.repository.NovelRepository
import com.example.epubnoveltranslator.ui.screens.conversation.TranslationProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

const val DEFAULT_PROMPT_TEMPLATE = """
You are an expert light novel translator. Translate the following Japanese light novel chapter into fluent, engaging English while preserving character tone and nuances.

### Glossary Rules:
{glossary_block}

Return only the translated chapter. Preserve paragraph breaks and dialogue. Do not add commentary, explanations, headings, Markdown, asterisks, dollar signs, or code fences.

<source_chapter>
{chapter_text}
</source_chapter>
"""

class NovelDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = NovelRepository(application)

    private val _novel = MutableStateFlow<NovelEntity?>(null)
    val novel: StateFlow<NovelEntity?> = _novel.asStateFlow()

    private val _chapters = MutableStateFlow<List<ChapterEntity>>(emptyList())
    val chapters: StateFlow<List<ChapterEntity>> = _chapters.asStateFlow()
    val translatingChapterIds: StateFlow<Set<String>> = TranslationProgress.activeChapterIds

    private val _promptGlossaryTerms = MutableStateFlow<List<GlossaryTermEntity>>(emptyList())
    val promptGlossaryTerms: StateFlow<List<GlossaryTermEntity>> = _promptGlossaryTerms.asStateFlow()
    private val _replacementGlossaryTerms = MutableStateFlow<List<GlossaryTermEntity>>(emptyList())
    val replacementGlossaryTerms: StateFlow<List<GlossaryTermEntity>> = _replacementGlossaryTerms.asStateFlow()

    private val _sortByStatus = MutableStateFlow(false)
    val sortByStatus: StateFlow<Boolean> = _sortByStatus.asStateFlow()

    private var currentNovelId: String? = null

    fun loadNovel(novelId: String) {
        currentNovelId = novelId
        viewModelScope.launch {
            _novel.value = repository.getNovelById(novelId)

            repository.getChaptersForNovel(novelId).collect {
                _chapters.value = it
            }
        }
        viewModelScope.launch {
            repository.getGlossaryForNovel(novelId, GlossaryKind.PROMPT).collect {
                _promptGlossaryTerms.value = it
            }
        }
        viewModelScope.launch {
            repository.getGlossaryForNovel(novelId, GlossaryKind.REPLACEMENT).collect {
                _replacementGlossaryTerms.value = it
            }
        }
    }

    fun addGlossaryTerm(sourceTerm: String, targetTerm: String, note: String, kind: String) {
        val novelId = currentNovelId ?: return
        viewModelScope.launch {
            val term = GlossaryTermEntity(
                id = UUID.randomUUID().toString(),
                novelId = novelId,
                sourceTerm = sourceTerm.trim(),
                targetTerm = targetTerm.trim(),
                note = note.trim(),
                kind = kind
            )
            repository.addGlossaryTerm(term)
        }
    }

    fun deleteGlossaryTerm(term: GlossaryTermEntity) {
        viewModelScope.launch {
            repository.deleteGlossaryTerm(term)
        }
    }

    fun updatePromptTemplate(newTemplate: String) {
        val novelId = currentNovelId ?: return
        viewModelScope.launch {
            repository.updateNovelPrompt(novelId, newTemplate)
            _novel.value = _novel.value?.copy(customPromptTemplate = newTemplate)
        }
    }

    fun updateNotes(notes: String) {
        val novelId = currentNovelId ?: return
        viewModelScope.launch {
            repository.updateNovelNotes(novelId, notes)
            _novel.value = _novel.value?.copy(notes = notes)
        }
    }

    fun toggleSortByStatus() {
        _sortByStatus.value = !_sortByStatus.value
    }

    fun resetPromptTemplateToDefault() {
        updatePromptTemplate(DEFAULT_PROMPT_TEMPLATE.trimIndent())
    }
}
