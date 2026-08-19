package com.example.epubnoveltranslator.data.model

enum class DownloadStatus {
    NOT_DOWNLOADED,
    DOWNLOADING,
    READY,
    ERROR
}

data class ModelInfo(
    val id: String = "gemma-3n-e4b",
    val name: String = "Gemma 3n E4B",
    val description: String = "Lightweight on-device LLM optimized for translation",
    val sizeBytes: Long = 2_400_000_000L,
    val downloadStatus: DownloadStatus = DownloadStatus.NOT_DOWNLOADED,
    val progressPercent: Int = 0,
    val localFilePath: String? = null,
    val errorMessage: String? = null
)
