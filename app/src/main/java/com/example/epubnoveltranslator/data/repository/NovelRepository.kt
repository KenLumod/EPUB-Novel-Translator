package com.example.epubnoveltranslator.data.repository

import android.content.Context
import android.net.Uri
import com.example.epubnoveltranslator.data.db.AppDatabase
import com.example.epubnoveltranslator.data.db.ChapterEntity
import com.example.epubnoveltranslator.data.db.GlossaryTermEntity
import com.example.epubnoveltranslator.data.db.GlossaryKind
import com.example.epubnoveltranslator.data.db.NovelEntity
import com.example.epubnoveltranslator.data.parser.EpubParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class NovelRepository(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val novelDao = db.novelDao()
    private val epubParser = EpubParser(context)

    val allNovels: Flow<List<NovelEntity>> = novelDao.getAllNovels()

    fun getChaptersForNovel(novelId: String): Flow<List<ChapterEntity>> {
        return novelDao.getChaptersForNovel(novelId)
    }

    fun getGlossaryForNovel(novelId: String, kind: String): Flow<List<GlossaryTermEntity>> {
        return novelDao.getGlossaryForNovel(novelId, kind)
    }

    suspend fun getGlossaryListForNovel(novelId: String, kind: String): List<GlossaryTermEntity> {
        return novelDao.getGlossaryListForNovel(novelId, kind)
    }

    suspend fun getNovelById(novelId: String): NovelEntity? {
        return novelDao.getNovelById(novelId)
    }

    suspend fun getChapterById(chapterId: String): ChapterEntity? {
        return novelDao.getChapterById(chapterId)
    }

    suspend fun addGlossaryTerm(term: GlossaryTermEntity) {
        novelDao.insertGlossaryTerm(term)
    }

    suspend fun deleteGlossaryTerm(term: GlossaryTermEntity) {
        novelDao.deleteGlossaryTerm(term)
    }

    suspend fun deleteNovel(novel: NovelEntity) {
        // Room removes chapters and glossary terms through their foreign-key cascades.
        novelDao.deleteNovel(novel)
        novel.coverImagePath?.let { path ->
            val cover = java.io.File(path)
            if (cover.parentFile == java.io.File(context.filesDir, "covers") && cover.exists()) cover.delete()
        }
    }

    suspend fun updateNovelPrompt(novelId: String, prompt: String) {
        val novel = novelDao.getNovelById(novelId)
        if (novel != null) {
            novelDao.updateNovel(novel.copy(customPromptTemplate = prompt))
        }
    }

    suspend fun updateChapterTranslation(chapterId: String, translatedText: String) {
        val chapter = novelDao.getChapterById(chapterId) ?: return
        val wasAlreadyTranslated = chapter.isTranslated

        val updatedChapter = chapter.copy(
            translatedText = translatedText,
            isTranslated = true
        )
        novelDao.updateChapter(updatedChapter)

        if (!wasAlreadyTranslated) {
            val novel = novelDao.getNovelById(chapter.novelId)
            if (novel != null) {
                novelDao.updateNovel(novel.copy(translatedChapters = novel.translatedChapters + 1))
            }
        }
    }

    suspend fun importEpubUri(uri: Uri): Result<NovelEntity> = withContext(Dispatchers.IO) {
        try {
            val parsed = epubParser.parseEpub(uri)
            novelDao.insertNovel(parsed.novel)
            novelDao.insertChapters(parsed.chapters)
            Result.success(parsed.novel)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
