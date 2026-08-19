package com.example.epubnoveltranslator.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "novels")
data class NovelEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String,
    val coverImagePath: String? = null,
    val totalChapters: Int = 0,
    val translatedChapters: Int = 0,
    val customPromptTemplate: String? = null,
    val addedTimestamp: Long = System.currentTimeMillis()
)
