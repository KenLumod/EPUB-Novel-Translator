package com.example.epubnoveltranslator.data.model

import android.content.Context
import android.provider.OpenableColumns
import com.example.epubnoveltranslator.data.llm.LiteRtLmSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

data class UploadedModel(val path: String, val name: String, val sizeBytes: Long, val isActive: Boolean)

/** Owns imported files and remembers which one the translator should use. */
class ModelManager private constructor(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val preferences = context.getSharedPreferences("model_preferences", Context.MODE_PRIVATE)
    private val _modelInfo = MutableStateFlow(ModelInfo())
    val modelInfo: StateFlow<ModelInfo> = _modelInfo.asStateFlow()
    private val _uploadedModels = MutableStateFlow<List<UploadedModel>>(emptyList())
    val uploadedModels: StateFlow<List<UploadedModel>> = _uploadedModels.asStateFlow()

    init { checkExistingModelFile() }

    fun checkExistingModelFile() = scope.launch { refreshModels(validateActive = true) }

    fun importModelFile(uri: android.net.Uri) = scope.launch {
        var temporaryFile: File? = null
        try {
            _modelInfo.value = _modelInfo.value.copy(downloadStatus = DownloadStatus.DOWNLOADING, progressPercent = 0, errorMessage = null)
            val directory = modelsDirectory().also { it.mkdirs() }
            val displayName = displayNameFor(uri).ifBlank { "model.litertlm" }
            val baseName = displayName.removeSuffix(".litertlm").replace(Regex("[^A-Za-z0-9._ -]"), "_")
            val finalFile = File(directory, "${baseName.take(72)}-${UUID.randomUUID()}.litertlm")
            val temp = File(directory, ".${UUID.randomUUID()}.importing.litertlm")
            temporaryFile = temp
            val resolver = context.contentResolver
            val totalBytes = resolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
            resolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var copied = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        copied += count
                        if (totalBytes > 0) _modelInfo.value = _modelInfo.value.copy(progressPercent = ((copied * 100 / totalBytes).toInt()).coerceIn(0, 99))
                    }
                }
            } ?: throw java.io.IOException("Unable to open the selected file")
            validate(temp).getOrElse { throw it }
            if (!temp.renameTo(finalFile)) throw java.io.IOException("Unable to finish importing the model")
            preferences.edit().putString(ACTIVE_MODEL_PATH, finalFile.absolutePath).apply()
            refreshModels(validateActive = false)
        } catch (error: Throwable) {
            temporaryFile?.delete()
            _modelInfo.value = _modelInfo.value.copy(downloadStatus = DownloadStatus.ERROR, errorMessage = error.localizedMessage ?: "Failed to import .litertlm model file")
        }
    }

    fun selectModel(path: String) = scope.launch {
        val file = File(path)
        if (!file.exists()) return@launch
        preferences.edit().putString(ACTIVE_MODEL_PATH, file.absolutePath).apply()
        refreshModels(validateActive = true)
    }

    fun deleteModel(path: String) = scope.launch {
        val file = File(path)
        if (file.parentFile?.canonicalFile != modelsDirectory().canonicalFile) return@launch
        val wasActive = file.absolutePath == activePath()
        if (file.exists() && !file.delete()) {
            _modelInfo.value = _modelInfo.value.copy(errorMessage = "Could not delete ${file.name}")
            return@launch
        }
        if (wasActive) preferences.edit().remove(ACTIVE_MODEL_PATH).apply()
        refreshModels(validateActive = wasActive)
    }

    fun deleteModel() { activePath()?.let(::deleteModel) }

    private suspend fun refreshModels(validateActive: Boolean) {
        val files = modelsDirectory().listFiles { file -> file.isFile && file.extension.equals("litertlm", true) && !file.name.contains(".importing") }
            ?.sortedByDescending(File::lastModified).orEmpty()
        var active = activePath()?.let(::File)?.takeIf(File::exists)
        if (active == null) {
            active = files.firstOrNull()
            preferences.edit().putString(ACTIVE_MODEL_PATH, active?.absolutePath).apply()
        }
        _uploadedModels.value = files.map { UploadedModel(it.absolutePath, it.name.removeSuffix(".litertlm"), it.length(), it == active) }
        if (active == null) {
            _modelInfo.value = ModelInfo()
        } else if (validateActive) {
            _modelInfo.value = ModelInfo(name = active.name.removeSuffix(".litertlm"), sizeBytes = active.length(), downloadStatus = DownloadStatus.DOWNLOADING)
            val result = validate(active)
            _modelInfo.value = ModelInfo(
                id = active.name, name = active.name.removeSuffix(".litertlm"), sizeBytes = active.length(),
                localFilePath = active.absolutePath, downloadStatus = if (result.isSuccess) DownloadStatus.READY else DownloadStatus.ERROR,
                progressPercent = if (result.isSuccess) 100 else 0, errorMessage = result.exceptionOrNull()?.localizedMessage
            )
        } else {
            _modelInfo.value = ModelInfo(id = active.name, name = active.name.removeSuffix(".litertlm"), sizeBytes = active.length(), localFilePath = active.absolutePath, downloadStatus = DownloadStatus.READY, progressPercent = 100)
        }
    }

    private suspend fun validate(file: File) = LiteRtLmSession(context).let { session -> try { session.loadModelSession(file.absolutePath) } finally { session.close() } }
    private fun modelsDirectory() = File(context.filesDir, "models")
    private fun activePath() = preferences.getString(ACTIVE_MODEL_PATH, null)
    private fun displayNameFor(uri: android.net.Uri): String = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else "" }.orEmpty()

    companion object {
        private const val ACTIVE_MODEL_PATH = "active_model_path"
        @Volatile private var INSTANCE: ModelManager? = null
        fun getInstance(context: Context): ModelManager = INSTANCE ?: synchronized(this) { INSTANCE ?: ModelManager(context.applicationContext).also { INSTANCE = it } }
    }
}
