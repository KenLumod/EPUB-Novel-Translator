package com.example.epubnoveltranslator.ui.screens.conversation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Shared UI-only progress for work that has not yet been persisted to Room. */
object TranslationProgress {
    private val _activeChapterIds = MutableStateFlow<Set<String>>(emptySet())
    val activeChapterIds: StateFlow<Set<String>> = _activeChapterIds.asStateFlow()

    fun start(chapterId: String) {
        _activeChapterIds.value = _activeChapterIds.value + chapterId
    }

    fun finish(chapterId: String) {
        _activeChapterIds.value = _activeChapterIds.value - chapterId
    }
}
