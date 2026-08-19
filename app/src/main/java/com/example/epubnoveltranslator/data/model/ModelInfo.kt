package com.example.epubnoveltranslator.data.model

enum class DownloadStatus {
    NOT_DOWNLOADED,
    DOWNLOADING,
    READY,
    ERROR
}

data class ModelInfo(
    val id: String = "",
    val name: String = "No model selected",
    val description: String = "Upload a .litertlm model file to begin translating",
    val sizeBytes: Long = 0L,
    val downloadStatus: DownloadStatus = DownloadStatus.NOT_DOWNLOADED,
    val progressPercent: Int = 0,
    val localFilePath: String? = null,
    val errorMessage: String? = null
)
