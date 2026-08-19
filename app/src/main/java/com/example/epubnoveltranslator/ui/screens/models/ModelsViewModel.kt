package com.example.epubnoveltranslator.ui.screens.models

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.epubnoveltranslator.data.llm.LiteRtLmSession
import com.example.epubnoveltranslator.data.model.ModelInfo
import com.example.epubnoveltranslator.data.model.ModelManager
import com.example.epubnoveltranslator.data.model.UploadedModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ModelTestMessage(
    val id: String,
    val text: String,
    val isModel: Boolean
)

data class ModelTestState(
    val messages: List<ModelTestMessage> = emptyList(),
    val isGenerating: Boolean = false,
    val errorMessage: String? = null
)

class ModelsViewModel(application: Application) : AndroidViewModel(application) {

    private val modelManager = ModelManager.getInstance(application)
    private val testSession = LiteRtLmSession(application)
    private var loadedModelPath: String? = null
    val modelInfo: StateFlow<ModelInfo> = modelManager.modelInfo
    val uploadedModels: StateFlow<List<UploadedModel>> = modelManager.uploadedModels

    private val _testState = MutableStateFlow(ModelTestState())
    val testState: StateFlow<ModelTestState> = _testState.asStateFlow()

    fun importModelFile(uri: android.net.Uri) {
        testSession.close()
        loadedModelPath = null
        _testState.value = ModelTestState()
        modelManager.importModelFile(uri)
    }

    fun deleteModel() {
        testSession.close()
        loadedModelPath = null
        _testState.value = ModelTestState()
        modelManager.deleteModel()
    }

    fun selectModel(model: UploadedModel) {
        testSession.close()
        loadedModelPath = null
        _testState.value = ModelTestState()
        modelManager.selectModel(model.path)
    }

    fun deleteModel(model: UploadedModel) {
        testSession.close()
        loadedModelPath = null
        _testState.value = ModelTestState()
        modelManager.deleteModel(model.path)
    }

    fun sendTestPrompt(input: String) {
        val prompt = input.trim()
        if (prompt.isBlank() || _testState.value.isGenerating) return

        viewModelScope.launch {
            val path = modelManager.modelInfo.value.localFilePath
            if (path.isNullOrBlank()) {
                _testState.value = _testState.value.copy(errorMessage = "Upload and validate a model before testing it.")
                return@launch
            }

            if (loadedModelPath != path || !testSession.isLoaded()) {
                testSession.close()
                val loadResult = testSession.loadModelSession(path)
                if (loadResult.isFailure) {
                    _testState.value = _testState.value.copy(
                        errorMessage = loadResult.exceptionOrNull()?.localizedMessage
                            ?: "The model could not be initialized."
                    )
                    return@launch
                }
                loadedModelPath = path
            }

            val replyId = "model-${System.currentTimeMillis()}"
            val messages = _testState.value.messages +
                ModelTestMessage("user-${System.currentTimeMillis()}", prompt, false) +
                ModelTestMessage(replyId, "Thinking…", true)
            _testState.value = ModelTestState(messages = messages, isGenerating = true)

            val response = StringBuilder()
            try {
                testSession.generateResponseStream(prompt).collect { token ->
                    response.append(token)
                    _testState.value = ModelTestState(
                        messages = messages.map { message ->
                            if (message.id == replyId) message.copy(text = response.toString()) else message
                        },
                        isGenerating = true
                    )
                }
                _testState.value = ModelTestState(
                    messages = messages.map { message ->
                        if (message.id == replyId) {
                            message.copy(text = response.toString().trim().ifBlank { "No response generated." })
                        } else {
                            message
                        }
                    }
                )
            } catch (error: Throwable) {
                _testState.value = ModelTestState(
                    messages = messages.map { message ->
                        if (message.id == replyId) {
                            message.copy(text = "Error: ${error.localizedMessage ?: error.message}")
                        } else {
                            message
                        }
                    },
                    errorMessage = "Model response failed."
                )
            }
        }
    }

    fun clearTestConversation() {
        testSession.close()
        loadedModelPath = null
        _testState.value = ModelTestState()
    }

    override fun onCleared() {
        testSession.close()
        super.onCleared()
    }
}
