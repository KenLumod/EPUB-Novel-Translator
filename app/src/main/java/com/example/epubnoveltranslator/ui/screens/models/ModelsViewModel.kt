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

data class ModelTestState(
    val response: String? = null,
    val isTesting: Boolean = false,
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

    fun runModelTest() {
        if (_testState.value.isTesting) return

        viewModelScope.launch {
            val path = modelManager.modelInfo.value.localFilePath
            if (path.isNullOrBlank()) {
                _testState.value = ModelTestState(errorMessage = "Upload and validate a model before testing it.")
                return@launch
            }

            if (loadedModelPath != path || !testSession.isLoaded()) {
                testSession.close()
                val loadResult = testSession.loadModelSession(path)
                if (loadResult.isFailure) {
                    _testState.value = ModelTestState(
                        errorMessage = loadResult.exceptionOrNull()?.localizedMessage
                            ?: "The model could not be initialized."
                    )
                    return@launch
                }
                loadedModelPath = path
            }

            val newConversation = testSession.startNewConversation()
            if (newConversation.isFailure) {
                _testState.value = ModelTestState(
                    errorMessage = newConversation.exceptionOrNull()?.localizedMessage
                        ?: "The model test session could not be started."
                )
                return@launch
            }

            _testState.value = ModelTestState(isTesting = true)

            val response = StringBuilder()
            try {
                testSession.generateResponseStream("Reply with exactly: Model test successful.").collect { token ->
                    response.append(token)
                    _testState.value = ModelTestState(response = response.toString(), isTesting = true)
                }
                _testState.value = ModelTestState(
                    response = response.toString().trim().ifBlank { "No response generated." }
                )
            } catch (error: Throwable) {
                _testState.value = ModelTestState(
                    errorMessage = error.localizedMessage ?: error.message ?: "Model response failed."
                )
            }
        }
    }

    fun clearTestResult() {
        _testState.value = ModelTestState()
    }

    override fun onCleared() {
        testSession.close()
        super.onCleared()
    }
}
