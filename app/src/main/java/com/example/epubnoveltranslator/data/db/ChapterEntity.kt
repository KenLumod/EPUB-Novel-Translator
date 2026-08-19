package com.example.epubnoveltranslator.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chapters",
    foreignKeys = [
        ForeignKey(
            entity = NovelEntity::class,
            parentColumns = ["id"],
            childColumns = ["novelId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["novelId"])]
)
data class ChapterEntity(
    @PrimaryKey val id: String,
    val novelId: String,
    val chapterOrder: Int,
    val title: String,
    val rawText: String,
    val translatedText: String? = null,
    val isTranslated: Boolean = false
)
