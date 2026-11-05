package com.halo.app.everyday.knowledge

import com.halo.app.everyday.collectors.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class WikipediaArticleParserTest {
    private fun response() = JSONObject().put("parse", JSONObject().put("pageid", 42).put("revid", 71).put("title", "Water")
        .put("text", "<div class='mw-parser-output'><p>First paragraph.</p><figure><img src='//upload.wikimedia.org/example.png' width='640' height='480' alt='Water diagram'><figcaption>Source figure caption</figcaption></figure><h2 id='Units'>Units</h2><table><tr><th>Amount</th><th>Unit</th></tr><tr><td>10</td><td>mg/L</td></tr></table><p>Last paragraph.</p></div>")).toString().toByteArray()
    @Test fun fullArticlePreservesSectionsUnitsTablesRevisionAndInertImageReferences() {
        val rows = WikipediaArticleParser.entries(response(), "en", WikipediaArticleRequest(pageId = 42))
        assertEquals(2, rows.size)
        assertTrue(rows.last().text.contains("10 | mg/L")); assertTrue(rows.last().text.contains("Last paragraph."))
        assertEquals("71", rows.first().revision); assertTrue(rows.last().url!!.contains("oldid=71"))
        assertTrue(rows.last().url!!.endsWith("#Units"))
        assertEquals("Source figure caption", rows.first().images.single().caption)
        assertEquals("https://upload.wikimedia.org/example.png", rows.first().images.single().url)
        assertNull(rows.first().images.single().license) // Never inherit the text license for a picture.
        assertTrue(runCatching { WikipediaArticleParser.entries(response(), "en", WikipediaArticleRequest(pageId = 42, revision = 72)) }.isFailure)
    }
    @Test fun feedImageReferencesRoundTripWithoutFetchingOrAllowingInternalUrls() {
        val source = CollectorSource("fixture", "News", "Publisher", CollectorKind.FEED, SourceCategory.WORLD, "en", "https://example.org/feed")
        val xml = "<rss xmlns:media='http://search.yahoo.com/mrss/'><channel><item><title>News</title><description>&lt;p&gt;Text&lt;/p&gt;&lt;img src='https://example.org/photo.png' alt='Scene'&gt;</description><media:thumbnail url='http://127.0.0.1/private'/><enclosure type='image/jpeg' url='https://example.org/second.jpg'/></item></channel></rss>"
        val payload = CollectorPayload.Articles(FeedParser.parse(xml.toByteArray(), source).articles)
        assertEquals(2, payload.items.single().images.size)
        assertEquals(payload, com.halo.app.data.everyday.CollectorCodec.payload(com.halo.app.data.everyday.CollectorCodec.encode(payload)))
        assertTrue(payload.items.single().images.none { "127.0.0.1" in it.url })
    }
    @Test fun officialMedlineIndexCannotRedirectCatalogueSelectionToAnArbitraryArchive() {
        val url = ReferenceDownloadOwner.latestMedlineZip("<a href='https://evil.example/xml/mplus_topics_compressed_2099-01-01.zip'>wrong</a><a href='/xml/mplus_topics_compressed_2026-11-04.zip'>latest</a><a href='/xml/mplus_topics_compressed_2026-11-03.zip'>old</a>")
        assertEquals("medlineplus.gov", url.host); assertTrue(url.encodedPath.endsWith("2026-11-04.zip"))
    }
}
