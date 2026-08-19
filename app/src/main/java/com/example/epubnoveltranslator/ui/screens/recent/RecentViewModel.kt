package com.example.epubnoveltranslator.ui.screens.recent

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.epubnoveltranslator.data.db.NovelEntity
import com.example.epubnoveltranslator.data.repository.NovelRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class UploadState {
    object Idle : UploadState()
    object Processing : UploadState()
    data class Success(val novelTitle: String) : UploadState()
    data class Error(val message: String) : UploadState()
}

class RecentViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = NovelRepository(application)

    val novels: StateFlow<List<NovelEntity>> = repository.allNovels
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _uploadState = MutableStateFlow<UploadState>(UploadState.Idle)
    val uploadState: StateFlow<UploadState> = _uploadState.asStateFlow()

    fun onEpubFileSelected(uri: Uri) {
        viewModelScope.launch {
            _uploadState.value = UploadState.Processing
            val result = repository.importEpubUri(uri)
            if (result.isSuccess) {
                _uploadState.value = UploadState.Success(result.getOrNull()?.title ?: "Novel")
            } else {
                _uploadState.value = UploadState.Error(result.exceptionOrNull()?.localizedMessage ?: "Import failed")
            }
        }
    }

    fun resetUploadState() {
        _uploadState.value = UploadState.Idle
    }

    fun deleteNovel(novel: NovelEntity) {
        viewModelScope.launch { repository.deleteNovel(novel) }
    }
}
