package com.example.epubnoveltranslator.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "glossary_terms",
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
data class GlossaryTermEntity(
    @PrimaryKey val id: String,
    val novelId: String,
    val sourceTerm: String,
    val targetTerm: String,
    val note: String = "",
    /** PROMPT terms guide the model; REPLACEMENT terms are applied after translation. */
    val kind: String = GlossaryKind.PROMPT
)

object GlossaryKind {
    const val PROMPT = "PROMPT"
    const val REPLACEMENT = "REPLACEMENT"
}
