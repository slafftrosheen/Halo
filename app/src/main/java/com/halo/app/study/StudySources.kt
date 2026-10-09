package com.halo.app.study

import com.halo.sdk.runtime.multimodal.MediaAssetRef

object StudySourceLimits {
    const val MAX_SOURCES = 4
    const val PASSAGE_CHARS = 1600
    const val PASSAGES_PER_SOURCE = 3
    const val TOTAL_CHARS = 8000
    const val READ_BYTES = 2 * 1024 * 1024
    const val DECODED_CHARS = 100_000
}

sealed interface StudySourceLocator {
    fun label(): String
    data class NoteBlocks(val first: Int, val last: Int) : StudySourceLocator {
        override fun label() = if (first == last) "Block $first" else "Blocks $first–$last"
    }
    data class TextRange(val start: Int, val end: Int) : StudySourceLocator {
        override fun label() = "Characters $start–$end"
    }
    data class PdfPage(val page: Int) : StudySourceLocator { override fun label() = "Page $page" }
    data class Image(val label: String = "Image") : StudySourceLocator { override fun label() = label }
}
data class StudyPassage(val text: String, val locator: StudySourceLocator)
sealed interface ResolvedStudySource {
    val objectId: String
    val revision: String?
    val title: String
    data class Text(override val objectId: String, override val revision: String?, override val title: String,
        val passages: List<StudyPassage>, val totalCharacters: Int, val coverage: SourceCoverage) : ResolvedStudySource
    data class Visual(override val objectId: String, override val revision: String?, override val title: String,
        val asset: MediaAssetRef, val locator: StudySourceLocator) : ResolvedStudySource
    data class Pdf(override val objectId: String, override val revision: String?, override val title: String,
        val pageCount: Int, val selectedPage: Int) : ResolvedStudySource
    data class Unsupported(override val objectId: String, override val title: String, val reason: String,
        override val revision: String? = null) : ResolvedStudySource
}
fun interface StudySourceResolver {
    suspend fun resolve(objectId: String, query: String): ResolvedStudySource
    /** Explicit conversation reference requires an independently verifiable source revision. */
    suspend fun resolveExplicitReference(objectId: String, query: String): ResolvedStudySource = resolve(objectId,query)
    suspend fun resolveForTurn(objectId: String, query: String, budget: Int, includeBeginning: Boolean): ResolvedStudySource = resolve(objectId, query)
}

internal object StudyTextRevisionVerifier {
    fun matches(bytes: ByteArray, expectedSha256: String?, complete: Boolean): Boolean = complete && expectedSha256 != null &&
        java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) } == expectedSha256
}

/** Ranking is lexical and deterministic; the selected excerpts retain document order. */
object StudyPassageSelector {
    fun chunks(text: String, locator: (Int, Int) -> StudySourceLocator): List<StudyPassage> =
        text.chunked(StudySourceLimits.PASSAGE_CHARS).mapIndexed { index, part ->
            val start = index * StudySourceLimits.PASSAGE_CHARS
            StudyPassage(part, locator(start, start + part.length))
        }
    fun notePassages(blocks: List<String>): List<StudyPassage> {
        val result = mutableListOf<StudyPassage>()
        var text = ""
        var first = 0
        var last = 0
        fun flush() {
            if (text.isNotBlank()) result += StudyPassage(text, StudySourceLocator.NoteBlocks(first, last))
            text = ""
        }
        blocks.forEachIndexed { index, block ->
            block.chunked(StudySourceLimits.PASSAGE_CHARS).filter { it.isNotBlank() }.forEach { part ->
                val cost = part.length + if (text.isNotEmpty()) 1 else 0
                if (text.length + cost > StudySourceLimits.PASSAGE_CHARS) flush()
                if (text.isEmpty()) first = index + 1
                else text += "\n"
                text += part
                last = index + 1
            }
        }
        flush()
        return result
    }
    fun select(passages: List<StudyPassage>, query: String, budget: Int = StudySourceLimits.TOTAL_CHARS,
        includeBeginning: Boolean = false): List<StudyPassage> {
        val words = Regex("[\\p{L}\\p{N}]{2,}").findAll(query.lowercase()).map { it.value }.toSet()
        val ranked = passages.indices.sortedWith(compareByDescending<Int> { index ->
            words.count { word -> passages[index].text.contains(word, ignoreCase = true) }
        }.thenBy { it })
        val order = if (includeBeginning && passages.isNotEmpty()) (listOf(0) + ranked).distinct() else ranked
        var remaining = budget
        val chosen = mutableListOf<Int>()
        for (index in order) {
            if (chosen.size == StudySourceLimits.PASSAGES_PER_SOURCE) break
            val cost = passages[index].text.length + if (chosen.isNotEmpty()) 1 else 0
            if (cost <= remaining) { remaining -= cost; chosen += index }
        }
        return chosen.sorted().map { passages[it] }
    }
}

/** Optional future OCR/PDF-text seam. No extractor implementation is installed. */
fun interface StudyDocumentTextExtractor {
    suspend fun extract(objectId: String): ResolvedStudySource.Text?
}
