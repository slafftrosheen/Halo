package com.halo.app.everyday.knowledge

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.OutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class ReferenceDownloadTest {
    @Test fun latestMedicalFileIsResolvedOnlyFromTheOfficialDataPath() {
        val html = "<a href='/xml/mplus_topics_compressed_2026-11-03.zip'>old</a>" +
            "<a href='https://evil.example/xml/mplus_topics_compressed_2099-01-01.zip'>bad</a>" +
            "<a href='/xml/mplus_topics_compressed_2026-11-04.zip'>current</a>"
        assertEquals("https://medlineplus.gov/xml/mplus_topics_compressed_2026-11-04.zip", ReferenceDownloadOwner.latestMedlineZip(html).toString())
        assertTrue(runCatching { ReferenceDownloadOwner.latestMedlineZip("<a href='https://evil.example/file.zip'>mirror</a>") }.isFailure)
    }
    @Test fun localModeMakesZeroTransferCalls() = runBlocking {
        var calls = 0
        val transfer = object : ReferenceTransfer {
            override suspend fun fetch(url: HttpUrl, maxBytes: Long, sink: OutputStream, checkIndexing: Boolean, imageOnly: Boolean): String? { calls++; error("Unexpected transfer") }
        }
        val owner = ReferenceDownloadOwner(ApplicationProvider.getApplicationContext(), transfer, { {} }, { false })
        assertTrue(runCatching { owner.downloadForReview(ReferenceStarterId.MEDLINEPLUS) { error("No preview expected in Local") } }.isFailure)
        assertEquals(0, calls)
    }
    @Test fun wikiReadPinsRevisionAndKeepsEveryTableCell() {
        val request = WikipediaArticleRequest(pageId = 42, revision = 7)
        val bytes = """{"parse":{"title":"Owned article","pageid":42,"revid":7,"text":"<div class='mw-parser-output'><p>Introduction.</p><h2 id='Measurements'>Measurements</h2><table><tr><th>Amount</th><th>Unit</th></tr><tr><td>2</td><td>mg</td></tr></table></div>"}}""".toByteArray()
        val entries = WikipediaArticleParser.entries(bytes, "en", request)
        assertEquals(2, entries.size); assertTrue(entries.all { it.revision == "7" && it.url!!.contains("oldid=7") })
        assertTrue(entries.last().text.contains("Amount | Unit")); assertTrue(entries.last().text.contains("2 | mg"))
        assertTrue(runCatching { WikipediaArticleParser.entries(bytes, "en", request.copy(revision = 8)) }.isFailure)
    }
    @Test fun publicPageNoarchiveAndMissingArticleBodyDoNotBecomePretendLibraries() {
        assertTrue(runCatching { ReferenceDownloadOwner.cdcEntry("<meta name='robots' content='noarchive'><main>text</main>".toByteArray(), ReferenceDownloadOwner.preparednessUrls.first()) }.isFailure)
        assertTrue(runCatching { ReferenceDownloadOwner.cdcEntry("<body>not an article</body>".toByteArray(), ReferenceDownloadOwner.preparednessUrls.first()) }.isFailure)
    }
    @Test fun recoveryDeletesUnreferencedTempsButKeepsCommittedPreviewInputsOnCancellation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val directory = java.io.File(context.filesDir, "reference-downloads").apply { mkdirs() }
        val orphan = java.io.File(directory, "${java.util.UUID.randomUUID()}.zip.part").apply { writeText("orphan") }
        val retained = mutableSetOf<String>()
        val transfer = object : ReferenceTransfer {
            override suspend fun fetch(url: HttpUrl, maxBytes: Long, sink: OutputStream, checkIndexing: Boolean, imageOnly: Boolean): String? {
                sink.write(if (url.encodedPath.endsWith(".html")) "<a href='/xml/mplus_topics_compressed_2026-11-04.zip'>Owned fixture</a>".toByteArray() else byteArrayOf(1, 2, 3))
                return null
            }
        }
        val owner = ReferenceDownloadOwner(context, transfer, { {} }, { true }, { retained.toSet() },
            ownedUri = { file -> android.net.Uri.parse("content://${context.packageName}.captures/reference-libraries/${file.name}") })
        var preserved: java.io.File? = null
        try {
            assertTrue(runCatching { owner.downloadForReview(ReferenceStarterId.MEDLINEPLUS) { downloaded ->
                retained.add(downloaded.uri.toString()); preserved = java.io.File(directory, downloaded.uri.lastPathSegment!!)
                throw kotlinx.coroutines.CancellationException("Preview commit returned after cancellation")
            } }.isFailure)
            assertFalse(orphan.exists()); assertTrue(preserved!!.isFile)
            assertTrue(runCatching { owner.downloadForReview(ReferenceStarterId.MEDLINEPLUS) { error("Preview rejected before commit") } }.isFailure)
            assertTrue(preserved!!.isFile)
            assertEquals(listOf(preserved!!.name), directory.listFiles().orEmpty().map { it.name })
        } finally { directory.deleteRecursively() }
    }

}
