package com.example.epubnoveltranslator.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NovelDao {

    // Novel operations
    @Query("SELECT * FROM novels ORDER BY addedTimestamp DESC")
    fun getAllNovels(): Flow<List<NovelEntity>>

    @Query("SELECT * FROM novels WHERE id = :novelId")
    suspend fun getNovelById(novelId: String): NovelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNovel(novel: NovelEntity)

    @Update
    suspend fun updateNovel(novel: NovelEntity)

    @Delete
    suspend fun deleteNovel(novel: NovelEntity)

    // Chapter operations
    @Query("SELECT * FROM chapters WHERE novelId = :novelId ORDER BY chapterOrder ASC")
    fun getChaptersForNovel(novelId: String): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE id = :chapterId")
    suspend fun getChapterById(chapterId: String): ChapterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapters(chapters: List<ChapterEntity>)

    @Update
    suspend fun updateChapter(chapter: ChapterEntity)

    // Glossary operations
    @Query("SELECT * FROM glossary_terms WHERE novelId = :novelId AND kind = :kind")
    fun getGlossaryForNovel(novelId: String, kind: String): Flow<List<GlossaryTermEntity>>

    @Query("SELECT * FROM glossary_terms WHERE novelId = :novelId AND kind = :kind")
    suspend fun getGlossaryListForNovel(novelId: String, kind: String): List<GlossaryTermEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGlossaryTerm(term: GlossaryTermEntity)

    @Delete
    suspend fun deleteGlossaryTerm(term: GlossaryTermEntity)
}
