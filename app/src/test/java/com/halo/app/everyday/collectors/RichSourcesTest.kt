package com.halo.app.everyday.collectors

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import com.halo.app.data.everyday.CollectorCodec
import com.halo.app.data.everyday.RoomCollectorRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class RichSourcesTest {
    @get:Rule val temporary = TemporaryFolder()
    private val now = Instant.parse("2026-11-16T10:00:00Z")
    @Test fun extractsBodyAndImagesWithoutExecutingOrRetainingActiveMarkup() {
        val page = """<html><nav>Menu only</nav><article><header><h1>Article heading</h1></header><p>First paragraph.</p><p>Second paragraph.</p>
            <figure><img srcset="/photo.png 640w, /large.png 1280w" alt="A red kite"><figcaption>The kite above the lake</figcaption></figure>
            <img src="javascript:alert(1)"><img src="https://127.0.0.1/private"><img src="https://example.org/pixel" width="1" height="1">
            <script>secretJavascript()</script><iframe src="https://example.org/frame"></iframe></article><footer>Footer only</footer></html>"""
        val body = ArticleExtractor.extract(page, "https://example.org/story", now.toString())
        assertTrue(body.text.contains("First paragraph.")); assertTrue(body.text.contains("Second paragraph."))
        assertTrue(body.text.contains("Article heading"))
        assertFalse(body.text.contains("Menu only")); assertFalse(body.text.contains("secretJavascript"))
        assertEquals("https://example.org/photo.png", body.images.single().url)
        assertEquals("The kite above the lake", body.images.single().caption)
        assertEquals("PAGE_CAPTURED", body.status)
        assertEquals("RESTRICTED_PAGE", ArticleExtractor.extract("<meta itemprop='isAccessibleForFree' content='false'><article>Preview only</article>", "https://example.org/story", now.toString()).status)
        assertEquals("TEXT_LIMIT", ArticleExtractor.extract("<article>${"x".repeat(200010)}</article>", "https://example.org/story", now.toString()).status)
    }
    @Test fun feedImagesAndFullArticleSurviveReopenAndEveryTextCharacterCanBeRead() {
        val source = CollectorSource("fixture", "Fixture", "Publisher", CollectorKind.FEED, SourceCategory.WORLD, "en", "https://example.org/feed")
        val parsed = FeedParser.parse("""<rss><channel><item><title>Fixture</title><description><![CDATA[<p>Excerpt</p><img src="/photo.png" alt="Kite">]]></description></item></channel></rss>""".toByteArray(), source).articles.single()
        assertEquals("https://example.org/photo.png", parsed.images.single().url)
        val directory = temporary.newFolder()
        val store = ArticleContentStore(directory)
        val text = "First paragraph.\n".repeat(1600) + "UNIQUE LAST PARAGRAPH"
        val body = ArticleContent(text, parsed.images, "PAGE_CAPTURED", "https://example.org/story", now.toString())
        val key = store.save(body)
        val stored = parsed.copy(contentKey = key, contentStatus = body.status)
        val reopened = CollectorCodec.payload(CollectorCodec.encode(CollectorPayload.Articles(listOf(stored)))) as CollectorPayload.Articles
        val afterRestart = ArticleContentStore(directory)
        val reconstructed = StringBuilder()
        var offset = 0
        do {
            val page = SavedSourcePages.read(reopened, mapOf("articleId" to parsed.id, "textOffset" to offset), afterRestart::read)
            reconstructed.append(page.getJSONArray("items").getJSONObject(0).getString("text"))
            if (page.isNull("nextTextOffset")) break
            offset = page.getInt("nextTextOffset")
        } while (true)
        assertEquals(text, reconstructed.toString())
        assertNull(afterRestart.read("../../secret")); assertNull(afterRestart.imageFile("../../secret"))
        val legacy = CollectorCodec.payload("""{"schema":1,"type":"articles","items":[{"id":"old","title":"Old","text":"Body","publisher":"P","language":"en","categories":[]}]}""") as CollectorPayload.Articles
        assertEquals("FEED_ONLY", legacy.items.single().contentStatus)
    }
    @Test fun enrichmentUsesReservationBudgetKeepsImagesAndRefusesRevokedSource() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Application>(), AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val repository = RoomCollectorRepository(db)
            val source = repository.save(CollectorSource("fixture", "Fixture", "Publisher", CollectorKind.FEED, SourceCategory.WORLD, "en", "https://example.org/feed", selected = true), null)
            val reservation = (repository.reserve(source.id, source.version, now, true) as CollectorAdmission.Accepted).reservation
            val store = ArticleContentStore(temporary.newFolder())
            val calls = mutableListOf<String>()
            val owner = ArticleContentCollector(repository, store, { url ->
                calls += url
                if (url.endsWith(".png")) ArticleDownload(byteArrayOf(1, 2, 3), "image/png", url)
                else ArticleDownload("<article><p>${"A full paragraph. ".repeat(1000)}</p><img src='/photo.png' alt='Kite'></article>".toByteArray(), "text/html", url)
            }, Clock.fixed(now, ZoneOffset.UTC))
            val feed = CollectorPayload.Articles(listOf(ArticleFact("one", "Fixture", "Excerpt", "https://example.org/story", "P", now, "en")))
            val result = owner.enrich(reservation, feed) as CollectorPayload.Articles
            val article = result.items.single()
            assertEquals(2, calls.size); assertEquals(3, db.collectorDao().requestCount(0))
            assertTrue(store.read(article.contentKey)!!.text.length > 10000)
            assertNotNull(store.imageFile(article.images.single().assetKey))
            repeat(CollectorRefreshPolicy.MAX_SOURCE_REQUESTS_PER_DAY - 3) { assertTrue(repository.reserveContent(reservation, now)) }
            assertFalse(repository.reserveContent(reservation, now))
            repository.save(source.copy(selected = false), source.version)
            assertFalse(repository.reserveContent(reservation, now.plusSeconds(90000)))
            assertFalse(repository.finish(reservation, null, CollectorAttempt(source.id, now, CollectorAttemptState.CURRENT)))
        } finally { db.close() }
    }
}
