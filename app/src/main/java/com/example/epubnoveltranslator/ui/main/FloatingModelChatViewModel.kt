package com.example.epubnoveltranslator.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.epubnoveltranslator.data.llm.LiteRtLmSession
import com.example.epubnoveltranslator.data.model.ModelManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FloatingChatMessage(
    val id: Long,
    val text: String,
    val isModel: Boolean
)

data class FloatingChatState(
    val messages: List<FloatingChatMessage> = emptyList(),
    val isGenerating: Boolean = false,
    val errorMessage: String? = null
)

/** App-scoped conversation for the draggable assistant chat head. */
class FloatingModelChatViewModel(application: Application) : AndroidViewModel(application) {
    private val modelManager = ModelManager.getInstance(application)
    private val session = LiteRtLmSession(application)
    private var loadedModelPath: String? = null

    private val _state = MutableStateFlow(FloatingChatState())
    val state: StateFlow<FloatingChatState> = _state.asStateFlow()

    fun send(text: String) {
        val prompt = text.trim()
        if (prompt.isBlank() || _state.value.isGenerating) return

        viewModelScope.launch {
            val path = modelManager.modelInfo.value.localFilePath
            if (path.isNullOrBlank()) {
                _state.value = _state.value.copy(errorMessage = "Upload and select a model before starting a chat.")
                return@launch
            }
            if (loadedModelPath != path || !session.isLoaded()) {
                session.close()
                val loadResult = session.loadModelSession(path)
                if (loadResult.isFailure) {
                    _state.value = _state.value.copy(
                        errorMessage = loadResult.exceptionOrNull()?.localizedMessage
                            ?: "The model could not be loaded for chat."
                    )
                    return@launch
                }
                loadedModelPath = path
            }

            val id = System.currentTimeMillis()
            val userMessage = FloatingChatMessage(id, prompt, isModel = false)
            val modelMessage = FloatingChatMessage(id + 1, "Thinking…", isModel = true)
            val messages = _state.value.messages + userMessage + modelMessage
            _state.value = FloatingChatState(messages = messages, isGenerating = true)

            val response = StringBuilder()
            try {
                session.generateResponseStream(prompt).collect { token ->
                    response.append(token)
                    _state.value = FloatingChatState(
                        messages = messages.map { message ->
                            if (message.id == modelMessage.id) message.copy(text = response.toString()) else message
                        },
                        isGenerating = true
                    )
                }
                _state.value = FloatingChatState(
                    messages = messages.map { message ->
                        if (message.id == modelMessage.id) {
                            message.copy(text = response.toString().trim().ifBlank { "No response generated." })
                        } else message
                    }
                )
            } catch (error: Throwable) {
                _state.value = FloatingChatState(
                    messages = messages.map { message ->
                        if (message.id == modelMessage.id) {
                            message.copy(text = "Unable to respond.")
                        } else message
                    },
                    errorMessage = error.localizedMessage ?: error.message ?: "The model response failed."
                )
            }
        }
    }

    fun clear() {
        if (_state.value.isGenerating) return
        viewModelScope.launch { session.startNewConversation() }
        _state.value = FloatingChatState()
    }

    override fun onCleared() {
        session.close()
        super.onCleared()
    }
}
