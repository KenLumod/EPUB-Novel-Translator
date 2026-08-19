package com.example.epubnoveltranslator.data.parser

import android.content.Context
import android.net.Uri
import com.example.epubnoveltranslator.data.db.ChapterEntity
import com.example.epubnoveltranslator.data.db.NovelEntity
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.zip.ZipInputStream

sealed class EpubParseException(message: String) : Exception(message) {
    class CorruptZip(message: String = "Failed to open or read EPUB archive file.") : EpubParseException(message)
    class MissingManifest(message: String = "Corrupt EPUB: META-INF/container.xml or OPF manifest missing.") : EpubParseException(message)
    class EmptyToc(message: String = "No valid chapter text content found in EPUB spine.") : EpubParseException(message)
}

data class ParsedEpub(
    val novel: NovelEntity,
    val chapters: List<ChapterEntity>
)

private data class OpfContents(
    val title: String,
    val author: String,
    val spineHrefs: List<String>,
    val navigationHref: String?,
    val coverHref: String?
)

class EpubParser(private val context: Context) {

    fun parseEpub(uri: Uri): ParsedEpub {
        val zipEntries = mutableMapOf<String, ByteArray>()

        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val name = entry.name.replace("\\", "/")
                            zipEntries[name] = zip.readBytes()
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            } ?: throw EpubParseException.CorruptZip("Cannot open file stream for selected EPUB.")
        } catch (e: EpubParseException) {
            throw e
        } catch (e: Exception) {
            throw EpubParseException.CorruptZip("Failed to extract EPUB zip archive: ${e.localizedMessage}")
        }

        if (zipEntries.isEmpty()) {
            throw EpubParseException.CorruptZip("EPUB archive appears to be empty or corrupted.")
        }

        // 1. Locate root OPF file path from META-INF/container.xml
        val containerBytes = zipEntries["META-INF/container.xml"]
            ?: throw EpubParseException.MissingManifest("META-INF/container.xml is missing inside the EPUB.")
        val opfPath = parseContainerXml(containerBytes)

        // 2. Read OPF file
        val opfBytes = zipEntries[opfPath]
            ?: throw EpubParseException.MissingManifest("OPF manifest file not found at path: $opfPath")
        val opf = parseOpfXml(opfBytes)

        val novelId = UUID.randomUUID().toString()

        // 3. Extract plain text content for each chapter in spine order
        val opfDir = if (opfPath.contains("/")) opfPath.substringBeforeLast("/") + "/" else ""
        val tocTitles = loadTocTitles(zipEntries, opfDir, opf.navigationHref)
        val coverImagePath = opf.coverHref
            ?.let { zipEntries[resolveZipPath(opfDir, it)] }
            ?.let { bytes -> saveCoverImage(novelId, bytes, opf.coverHref) }

        val chapters = mutableListOf<ChapterEntity>()
        opf.spineHrefs.forEachIndexed { index, href ->
            val chapterZipPath = resolveZipPath(opfDir, href)
            val chapterBytes = zipEntries[chapterZipPath]
            val rawText = if (chapterBytes != null) {
                htmlToPlainText(String(chapterBytes, Charsets.UTF_8))
            } else {
                ""
            }

            if (rawText.isNotBlank()) {
                chapters.add(
                    ChapterEntity(
                        id = UUID.randomUUID().toString(),
                        novelId = novelId,
                        chapterOrder = index + 1,
                        title = tocTitles[chapterZipPath]
                            ?: extractDocumentTitle(String(chapterBytes ?: ByteArray(0), Charsets.UTF_8))
                            ?: "Chapter ${index + 1}",
                        rawText = rawText,
                        translatedText = null,
                        isTranslated = false
                    )
                )
            }
        }

        if (chapters.isEmpty()) {
            throw EpubParseException.EmptyToc("EPUB contains no readable chapter text.")
        }

        val finalTitle = opf.title.ifBlank { "Untitled EPUB Novel" }
        val finalAuthor = opf.author.ifBlank { "Unknown Author" }

        val novel = NovelEntity(
            id = novelId,
            title = finalTitle,
            author = finalAuthor,
            coverImagePath = coverImagePath,
            totalChapters = chapters.size,
            translatedChapters = 0
        )

        return ParsedEpub(novel = novel, chapters = chapters)
    }

    private fun parseContainerXml(xmlBytes: ByteArray): String {
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "rootfile") {
                val fullPath = parser.getAttributeValue(null, "full-path")
                if (!fullPath.isNullOrEmpty()) {
                    return fullPath
                }
            }
            eventType = parser.next()
        }
        throw EpubParseException.MissingManifest("container.xml missing full-path attribute")
    }

    private fun parseOpfXml(xmlBytes: ByteArray): OpfContents {
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")

        var title = ""
        var author = ""
        val manifest = mutableMapOf<String, String>()
        val spineIdrefs = mutableListOf<String>()
        var navigationHref: String? = null
        var coverHref: String? = null
        var coverId: String? = null
        var ncxId: String? = null

        var eventType = parser.eventType
        var currentTag = ""

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name
                    if (currentTag == "item") {
                        val id = parser.getAttributeValue(null, "id")
                        val href = parser.getAttributeValue(null, "href")
                        if (id != null && href != null) {
                            manifest[id] = href
                            if (parser.getAttributeValue(null, "properties")
                                    ?.split(Regex("\\s+"))
                                    ?.contains("nav") == true) {
                                navigationHref = href
                            }
                            if (parser.getAttributeValue(null, "properties")
                                    ?.split(Regex("\\s+"))
                                    ?.contains("cover-image") == true || id.equals("cover", true) || id.equals("cover-image", true)) {
                                coverHref = href
                            }
                            if (parser.getAttributeValue(null, "media-type") == "application/x-dtbncx+xml") {
                                ncxId = id
                            }
                        }
                    } else if (currentTag == "itemref") {
                        val idref = parser.getAttributeValue(null, "idref")
                        if (idref != null) {
                            spineIdrefs.add(idref)
                        }
                    } else if (currentTag == "spine") {
                        ncxId = parser.getAttributeValue(null, "toc") ?: ncxId
                    } else if (currentTag == "meta" && parser.getAttributeValue(null, "name") == "cover") {
                        coverId = parser.getAttributeValue(null, "content")
                    }
                }
                XmlPullParser.TEXT -> {
                    val text = parser.text.trim()
                    if (text.isNotEmpty()) {
                        if (currentTag.lowercase().endsWith("title")) {
                            title = text
                        } else if (currentTag.lowercase().endsWith("creator")) {
                            author = text
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        val spineHrefs = spineIdrefs.mapNotNull { manifest[it] }
        return OpfContents(
            title, author, spineHrefs, navigationHref ?: ncxId?.let(manifest::get),
            coverHref ?: coverId?.let(manifest::get)
        )
    }

    private fun saveCoverImage(novelId: String, image: ByteArray, originalHref: String): String? = try {
        val extension = originalHref.substringAfterLast('.', "jpg")
            .lowercase()
            .takeIf { it in setOf("jpg", "jpeg", "png", "webp") } ?: "jpg"
        val file = java.io.File(context.filesDir, "covers/$novelId.$extension")
        file.parentFile?.mkdirs()
        file.writeBytes(image)
        file.absolutePath
    } catch (_: Exception) { null }

    private fun loadTocTitles(
        zipEntries: Map<String, ByteArray>,
        opfDir: String,
        navigationHref: String?
    ): Map<String, String> {
        if (navigationHref.isNullOrBlank()) return emptyMap()
        val navPath = resolveZipPath(opfDir, navigationHref)
        val navBytes = zipEntries[navPath] ?: return emptyMap()
        val navDirectory = navPath.substringBeforeLast("/", "")
            .let { if (it.isEmpty()) "" else "$it/" }
        val navDocument = String(navBytes, Charsets.UTF_8)

        return if (navPath.endsWith(".ncx", ignoreCase = true)) {
            parseNcxToc(navDocument, navDirectory)
        } else {
            parseHtmlToc(navDocument, navDirectory)
        }
    }

    private fun parseHtmlToc(document: String, baseDirectory: String): Map<String, String> {
        val result = linkedMapOf<String, String>()
        val tocDocument = Regex(
            """(?is)<nav\b(?=[^>]*(?:epub:type|type|role)\s*=\s*["'][^"']*\btoc\b[^"']*["'])[^>]*>(.*?)</nav>"""
        ).find(document)?.groupValues?.get(1) ?: document
        val anchorPattern = Regex(
            """(?is)<a\b[^>]*?href\s*=\s*(["'])(.*?)\1[^>]*>(.*?)</a>"""
        )
        anchorPattern.findAll(tocDocument).forEach { match ->
            val target = match.groupValues[2].substringBefore('#').substringBefore('?')
            val label = htmlToPlainText(match.groupValues[3]).replace('\n', ' ').trim()
            if (target.isNotBlank() && label.isNotBlank()) {
                result.putIfAbsent(resolveZipPath(baseDirectory, target), label)
            }
        }
        return result
    }

    private fun parseNcxToc(document: String, baseDirectory: String): Map<String, String> {
        val result = linkedMapOf<String, String>()
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(ByteArrayInputStream(document.toByteArray(Charsets.UTF_8)), "UTF-8")
        var readingLabel = false
        val label = StringBuilder()
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "navLabel" -> {
                        readingLabel = true
                        label.clear()
                    }
                    "content" -> {
                        val target = parser.getAttributeValue(null, "src")
                            ?.substringBefore('#')
                            ?.substringBefore('?')
                        if (!target.isNullOrBlank() && label.isNotBlank()) {
                            result.putIfAbsent(resolveZipPath(baseDirectory, target), label.toString().trim())
                        }
                    }
                }
                XmlPullParser.TEXT -> if (readingLabel) label.append(parser.text)
                XmlPullParser.END_TAG -> if (parser.name == "navLabel") readingLabel = false
            }
            eventType = parser.next()
        }
        return result
    }

    private fun resolveZipPath(baseDirectory: String, href: String): String {
        val decodedHref = URLDecoder.decode(href, StandardCharsets.UTF_8.name())
        val pathParts = mutableListOf<String>()
        (baseDirectory + decodedHref).replace("\\", "/").split('/').forEach { part ->
            when (part) {
                "", "." -> Unit
                ".." -> if (pathParts.isNotEmpty()) pathParts.removeAt(pathParts.lastIndex)
                else -> pathParts += part
            }
        }
        return pathParts.joinToString("/")
    }

    private fun extractDocumentTitle(html: String): String? {
        val heading = Regex("""(?is)<h[1-3]\b[^>]*>(.*?)</h[1-3]>""")
            .find(html)
            ?.groupValues
            ?.get(1)
            ?.let(::htmlToPlainText)
            ?.trim()
        if (!heading.isNullOrBlank()) return heading

        return Regex("""(?is)<title\b[^>]*>(.*?)</title>""")
            .find(html)
            ?.groupValues
            ?.get(1)
            ?.let(::htmlToPlainText)
            ?.trim()
            ?.takeIf(String::isNotBlank)
    }

    private fun htmlToPlainText(html: String): String {
        return html
            .replace(Regex("(?s)<script.*?>.*?</script>"), "")
            .replace(Regex("(?s)<style.*?>.*?</style>"), "")
            .replace(Regex("<br\\s*/?>"), "\n")
            .replace(Regex("</p>"), "\n\n")
            .replace(Regex("<[^>]*>"), "")
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n\n")
    }
}
