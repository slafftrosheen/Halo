package com.halo.app.everyday.collectors

import com.halo.app.ui.everyday.gallery

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

    @Test fun galleryKeepsPublisherCreditOrderAndRequiresAnExplicitSaveLicense() {
        val body = ArticleExtractor.extract("""<article>
            <figure><img src="/drawing.png" data-image-kind="illustration" data-credit="Studio" data-sensitive="true"
                data-license="https://creativecommons.org/licenses/by/4.0/" width="500">
                <figcaption>Illustration of the depot</figcaption></figure>
            <aside><img src="/related.png"></aside>
            <figure><img src="/street.png" data-credit="Photographer"><figcaption>Street view</figcaption></figure>
            </article>""", "https://example.org/story", now.toString())
        val (drawing, street) = body.images
        assertEquals(listOf("https://example.org/drawing.png", "https://example.org/street.png"), body.images.map { it.url })
        assertEquals(ArticleImageKind.ILLUSTRATION, drawing.kind)
        assertEquals(ArticleImageSensitivity.SENSITIVE, drawing.sensitivity)
        assertEquals("Studio", drawing.credit)
        assertEquals(drawing.url, drawing.previewUrl)
        assertTrue(drawing.canSave)
        assertFalse(street.canSave)
        val store = ArticleContentStore(temporary.newFolder())
        assertEquals(body.images, store.read(store.save(body))?.images)
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

    @Test fun publisherEnclosuresAndArticleMetadataProvideViewingPicturesWithoutExportRights() {
        val source = CollectorSource("fixture", "Fixture", "Publisher", CollectorKind.FEED, SourceCategory.WORLD, "en", "https://example.org/feed")
        val rss = """<rss><channel><item><title>Fixture</title>
            <enclosure url="/lead.jpg" type="image/jpeg"/>
            <enclosure url="/track.mp3" type="audio/mpeg"/>
            </item></channel></rss>"""
        val picture = FeedParser.parse(rss.toByteArray(), source).articles.single().images.single()
        assertEquals("https://example.org/lead.jpg", picture.url)
        assertTrue(picture.fromArticle)
        assertFalse(picture.canSave)

        val markup = """<meta property="og:type" content="article"><meta property="og:image" content="/preview.png">
            <main>Text</main>"""
        val preview = ArticleExtractor.extract(markup, "https://example.org/story", now.toString()).images.single()
        assertEquals("https://example.org/preview.png", preview.url)
        assertTrue(preview.fromArticle)
        assertFalse(preview.canSave)
        assertTrue(ArticleExtractor.extract(markup.replace("content=\"article\"", "content=\"website\""),
            "https://example.org/", now.toString()).images.isEmpty())
    }

    @Test fun oldAttachmentsCanUseTheSmallDownloadOnMeteredConnectionsWithoutAPage() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val repository = RoomCollectorRepository(db)
            val source = repository.save(CollectorSource("metered", "Fixture", "Publisher", CollectorKind.FEED,
                SourceCategory.WORLD, "en", "https://example.org/feed", selected = true), null)
            val reservation = (repository.reserve(source.id, source.version, now, true) as CollectorAdmission.Accepted).reservation
            val store = ArticleContentStore(temporary.newFolder())
            val limited = mutableListOf<String>()
            val owner = ArticleContentCollector(repository, store, { error("Use the limited download") },
                clock = Clock.fixed(now, ZoneOffset.UTC), metered = { true }, downloadSmall = { url ->
                    limited += url
                    ArticleDownload(byteArrayOf(1, 2, 3), "image/jpeg", url)
                })
            val link = "https://example.org/feed-photo.jpg"
            val article = ArticleFact("one", "Fixture", "Excerpt", null, "Publisher", now, "en",
                images = listOf(ArticleImage(link)))
            val result = owner.enrich(reservation, CollectorPayload.Articles(listOf(article))) as CollectorPayload.Articles
            val picture = result.items.single().images.single()
            assertEquals(listOf(link), limited)
            assertTrue(picture.fromArticle)
            assertNotNull(store.imageFile(picture.assetKey))
        } finally {
            db.close()
        }
    }

    @Test fun readsCardsOnlyShowSavedPicturesAndPreserveTheChildFilter() {
        val article = ArticleFact("one", "Fixture", "Excerpt", "https://example.org/story", "Publisher", now, "en",
            images = listOf(
                ArticleImage("https://example.org/unknown.jpg", assetKey = "saved", fromArticle = true),
                ArticleImage("https://example.org/clear.jpg", assetKey = "saved-clear", fromArticle = true,
                    sensitivity = ArticleImageSensitivity.CLEAR),
                ArticleImage("https://example.org/pending.jpg", fromArticle = true),
            ))
        assertEquals(2, article.gallery(visible = true, kids = false).size)
        assertEquals("https://example.org/clear.jpg", article.gallery(visible = true, kids = true).single().url)
        assertTrue(article.gallery(visible = false, kids = false).isEmpty())
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
