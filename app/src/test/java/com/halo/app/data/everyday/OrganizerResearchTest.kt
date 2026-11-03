package com.halo.app.data.everyday

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import com.halo.app.everyday.collectors.*
import com.halo.sdk.provider.*
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OrganizerResearchTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).allowMainThreadQueries().build()
    private val repository = RoomCollectorRepository(db)
    private val now = Instant.parse("2026-11-03T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    @After fun close() = db.close()
    private fun source(provider: String, query: String) = CollectorSource("source", query, "Research", CollectorKind.RESEARCH,
        SourceCategory.CUSTOM, "en", ResearchProviderRegistry.public.require(provider).endpoint(query, "en").toString(),
        selected = true, research = ResearchSourceQuery(query, provider))

    @Test fun wikipediaEvidenceKeepsStableCitationsAndDoesNotCallAnUpdateAPublicationDate() {
        val body = """{"query":{"search":[{"pageid":12,"title":"Printer &amp; paper","snippet":"<b>Printer</b> facts<script>execute()</script>","timestamp":"2026-11-02T10:00:00Z"},{"pageid":12,"title":"Duplicate"}]}}"""
        val facts = WikipediaResearchProvider.parse(body.toByteArray(), "printer", "en")
        assertEquals(1, facts.size); val fact = facts.single()
        assertEquals("Printer & paper", fact.title); assertEquals("Printer facts", fact.text)
        assertEquals("https://en.wikipedia.org/w/index.php?curid=12", fact.url)
        assertNull(fact.publishedAt); assertEquals(Instant.parse("2026-11-02T10:00:00Z"), fact.updatedAt)
        assertEquals(fact.id, WikipediaResearchProvider.parse(body.toByteArray(), "other", "en").single().id)
        val restored = CollectorCodec.payload(CollectorCodec.encode(CollectorPayload.Articles(facts))) as CollectorPayload.Articles
        assertEquals(fact, restored.items.single())
    }
    @Test fun publicDocumentProviderRejectsInjectedIdentifiersAndBoundsProviderJsonBeforeParsing() {
        val valid = """{"response":{"docs":[{"identifier":"printer_manual","title":"Manual","description":["Paper","<b>sizes</b>"],"date":"2025-01-01T00:00:00Z"}]}}"""
        val evidence = ArchiveResearchProvider.parse(valid.toByteArray(), "printer", "en").single()
        assertEquals("https://archive.org/details/printer_manual", evidence.url); assertEquals("Paper sizes", evidence.text)
        assertThrows(IllegalArgumentException::class.java) { ArchiveResearchProvider.parse(valid.replace("printer_manual", "../../private").toByteArray(), "printer", "en") }
        val deep = "{\"response\":" + "[".repeat(70) + "0" + "]".repeat(70) + "}"
        assertThrows(IllegalArgumentException::class.java) { ArchiveResearchProvider.parse(deep.toByteArray(), "x", "en") }
        assertThrows(IllegalArgumentException::class.java) { WikipediaResearchProvider.parse(ByteArray(CollectorRefreshPolicy.MAX_BODY_BYTES + 1) { 'x'.code.toByte() }, "x", "en") }
    }
    @Test fun queryEncodingCannotChangeTheProviderOrAddAnEndpoint() {
        val query = "printer & limit=9000 / 日本語"
        val endpoint = WikipediaResearchProvider.endpoint(query, "ru")
        assertEquals("ru.wikipedia.org", endpoint.host); assertEquals(query, endpoint.queryParameter("srsearch"))
        assertEquals("10", endpoint.queryParameter("srlimit"))
        assertThrows(IllegalArgumentException::class.java) { source("wikipedia", query).copy(endpoint = "https://evil.example/query") }
        listOf("https://127.0.0.1/private", "https://192.168.1.1/", "file:///secret", "http://example.org/page").forEach { url ->
            assertThrows(IllegalArgumentException::class.java) { PublicPageResearchProvider.endpoint(url, "en") }
        }
    }
    @Test fun savedResearchAndRetainedEvidenceWorkLocallyWithoutAnAdapterOrModel() = runBlocking {
        val service = OrganizerResearch(repository, clock)
        val saved = service.saveSource(ResearchSourceQuery("printer", "wikipedia"), "en", true)
        val reservation = (repository.reserve(saved.id, saved.version, now, true) as CollectorAdmission.Accepted).reservation
        val payload = CollectorPayload.Articles(listOf(ArticleFact("a", "Printer paper", "Printer manual evidence", "https://example.org/manual", "Publisher", null, "en")))
        assertTrue(repository.finish(reservation, CollectorSnapshot(saved.id, saved.version, payload, "revision", now, now, now.plusSeconds(3600)),
            CollectorAttempt(saved.id, now, CollectorAttemptState.CURRENT)))
        val evidence = OrganizerResearch(RoomCollectorRepository(db), clock).search("printer paper")
        assertEquals(1, evidence.size); assertEquals(1.0, evidence.single().lexicalRelevance, 0.0)
        assertTrue(evidence.single().stale) // Local explicitly labels external retained evidence as cached.
        assertEquals(now, evidence.single().retrievedAt)
        repository.save(saved.copy(selected = false), saved.version)
        assertTrue(service.search("printer").isEmpty())
    }
    @Test fun researchQueriesOnTheSameOriginShareThePersistentRateBudget() = runBlocking {
        val service = OrganizerResearch(repository, clock)
        val first = service.saveSource(ResearchSourceQuery("printer", "wikipedia"), "en", true)
        val second = service.saveSource(ResearchSourceQuery("paper", "wikipedia"), "en", true)
        assertTrue(repository.reserve(first.id, first.version, now, true) is CollectorAdmission.Accepted)
        assertTrue(repository.reserve(second.id, second.version, now, true) is CollectorAdmission.Deferred)
        val recreated = RoomCollectorRepository(db)
        assertTrue(recreated.reserve(second.id, second.version, now.plusSeconds(61), true) is CollectorAdmission.Accepted)
    }
    @Test fun localResearchRefreshDoesNotReachTheAdapterOrConsumeAnOutgoingBudget() = runBlocking {
        val saved = repository.save(source("wikipedia", "printer"), null); var calls = 0
        val adapter = object : Collector { override val kind = CollectorKind.RESEARCH
            override suspend fun fetch(source: CollectorSource, cached: CollectorSnapshot?): CollectorFetchResult { calls++; error("Local must not fetch") } }
        CollectorCoordinator(repository, CollectorRegistry(listOf(adapter)), DenyRemoteDispatchPolicy, clock).refresh(saved.id, true)
        assertEquals(0, calls); assertEquals(0, db.collectorDao().requestCount(0))
        assertEquals(CollectorAttemptState.LOCAL_ONLY, repository.state(saved.id)!!.attempt.state)
    }
    @Test fun robotsUsesProductGroupsLongestPathAllowTiesAndEncodedPaths() {
        val rules = ResearchRobotsPolicy.parse("User-agent: *\nDisallow: /\nUser-agent: Halo\nDisallow: /private\nAllow: /private/open\nDisallow: /private/open\nDisallow: /a%62c\nCrawl-delay: 4")
        assertFalse(rules.allows("/private/secret")); assertTrue(rules.allows("/private/open"))
        assertTrue(rules.allows("/public")); assertFalse(rules.allows("/abc")); assertEquals(4L, rules.crawlDelaySeconds)
        assertThrows(IllegalArgumentException::class.java) { ResearchRobotsPolicy.parse("x".repeat(65537)) }
    }
    private class Policy : RemoteDispatchPolicy {
        var allowed = true; override val revision = 0L
        override fun acquire(request: RemoteDispatchRequest, expectedRevision: Long, recordActivity: Boolean, cancel: () -> Unit): RemoteDispatchLease {
            if (!allowed || expectedRevision != revision) throw RemoteDispatchBlocked()
            return object : RemoteDispatchLease {
                override fun requireCurrent() { if (!allowed) throw RemoteDispatchBlocked() }
                override fun finish(outcome: RemoteDispatchOutcome, providerReportedCostUsd: String?) {}
            }
        }
    }
    @Test fun publicPageHonorsRobotsBeforeThePageAndNeverFollowsPageRedirects() = runBlocking {
        val visited = mutableListOf<String>(); var denied = true; var redirect = false
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val path = chain.request().url.encodedPath; visited += path
            val body = if (path == "/robots.txt") "User-agent: Halo\nDisallow: ${if (denied) "/" else "/private"}" else "<title>Printer</title><p>Useful printer evidence</p><script>track()</script>"
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(if (path != "/robots.txt" && redirect) 302 else 200)
                .message("Fixture").apply { if (redirect) header("Location", "https://other.example/private") }
                .body(body.toResponseBody((if (path == "/robots.txt") "text/plain" else "text/html").toMediaType())).build()
        }.build()
        val adapter = PublicHttpCollector(CollectorKind.RESEARCH, Policy(), client, clock)
        val page = source("public_page", "https://example.org/page")
        assertTrue(adapter.fetch(page, null) is CollectorFetchResult.Unavailable)
        assertEquals(listOf("/robots.txt"), visited)
        visited.clear(); denied = false
        val result = adapter.fetch(page, null) as CollectorFetchResult.Updated
        assertEquals(listOf("/robots.txt", "/page"), visited)
        assertEquals("Useful printer evidence", (result.payload as CollectorPayload.Articles).items.single().text)
        visited.clear(); redirect = true
        assertTrue(adapter.fetch(page, null) is CollectorFetchResult.Unavailable)
        assertEquals(listOf("/robots.txt", "/page"), visited)
    }
    @Test fun noindexAndRobotsFailureCannotBecomeRetainedEvidence() = runBlocking {
        val page = source("public_page", "https://example.org/page")
        assertThrows(IllegalArgumentException::class.java) { PublicPageResearchProvider.parse("<meta name='robots' content='noindex'><p>Private indexing policy</p>".toByteArray(), page.research!!.query, "en") }
        var pageCalls = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            if (chain.request().url.encodedPath != "/robots.txt") pageCalls++
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(503).message("Unavailable").body("error".toResponseBody()).build()
        }.build()
        assertTrue(PublicHttpCollector(CollectorKind.RESEARCH, Policy(), client, clock).fetch(page, null) is CollectorFetchResult.Unavailable)
        assertEquals(0, pageCalls)
    }
    @Test fun revocationAfterRobotsDoesNotPermitAPageRequestOrEvidencePublication() = runBlocking {
        val policy = Policy(); var pages = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val robots = chain.request().url.encodedPath == "/robots.txt"
            if (!robots) pages++
            val response = Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("Fixture")
                .body((if (robots) "User-agent: Halo\nAllow: /" else "<p>Evidence</p>").toResponseBody("text/plain".toMediaType())).build()
            if (robots) policy.allowed = false
            response
        }.build()
        assertTrue(runCatching { PublicHttpCollector(CollectorKind.RESEARCH, policy, client, clock).fetch(source("public_page", "https://example.org/page"), null) }.isFailure)
        assertEquals(0, pages)
    }
    @Test fun readablePageRespectsDeclaredCharsetAndRobotsWildcardsCannotActAsRegularExpressions() {
        val bytes = "<html><head><meta charset='windows-1251'><title>Заказ</title></head><body>Проверить заказ</body></html>".toByteArray(charset("windows-1251"))
        val article = PublicPageResearchProvider.parsePage(bytes, "https://example.org/page", "ru", null).single()
        assertEquals("Заказ", article.title); assertEquals("Проверить заказ", article.text)
        val policy = ResearchRobotsPolicy.parse("User-agent: Halo\nDisallow: /orders/*/private$\nDisallow: /file[1]\nDisallow: /заказ")
        assertFalse(policy.allows("/orders/abc/private")); assertTrue(policy.allows("/orders/abc/private/extra"))
        assertFalse(policy.allows("/file[1]")); assertTrue(policy.allows("/file1"))
        assertFalse(policy.allows("/%D0%B7%D0%B0%D0%BA%D0%B0%D0%B7"))
    }
    @Test fun aChosenResearchSourceIsFilteredBeforeGlobalRankingAndPagination() = runBlocking {
        val service = OrganizerResearch(repository, clock)
        for (index in 0..11) {
            val selected = service.saveSource(ResearchSourceQuery("printer $index", "wikipedia"), "en", true)
            // Bypass outgoing admission only to populate independent historical cache fixtures.
            db.collectorDao().putSnapshot(CollectorCodec.entity(CollectorSnapshot(selected.id, selected.version,
                CollectorPayload.Articles(listOf(ArticleFact("item:$index", "Printer $index", "Printer evidence", "https://example.org/$index", "Publisher", null, "en"))),
                "revision:$index", now, now.plusSeconds(index.toLong()), now.plusSeconds(3600))))
        }
        val chosen = repository.states().first().source
        assertTrue(service.search("printer", 8, chosen.id).all { it.sourceId == chosen.id })
        assertEquals(1, service.search("printer", 8, chosen.id).size)
    }
    @Test fun positiveSiteCrawlDelayFailsClosedBeforeASecondRequestAndReservesBothRequestUnits() = runBlocking {
        val page = repository.save(source("public_page", "https://example.org/page"), null)
        assertTrue(repository.reserve(page.id, page.version, now, true) is CollectorAdmission.Accepted)
        assertEquals(2, db.collectorDao().requestCount(0))
        val visited = mutableListOf<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            visited += chain.request().url.encodedPath
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("Fixture")
                .body("User-agent: Halo\nAllow: /\nCrawl-delay: 1".toResponseBody("text/plain".toMediaType())).build()
        }.build()
        assertTrue(PublicHttpCollector(CollectorKind.RESEARCH, Policy(), client, clock).fetch(page, null) is CollectorFetchResult.Unavailable)
        assertEquals(listOf("/robots.txt"), visited)
    }
    @Test fun aPageCannotConsumeTheLastSingleRequestUnitOfTheDailyBudget() = runBlocking {
        val page = repository.save(source("public_page", "https://example.org/page"), null)
        repeat(CollectorRefreshPolicy.MAX_EXTERNAL_REQUESTS_PER_DAY - 1) { index ->
            db.collectorDao().request(CollectorRequestEntity("previous:$index", "other-origin", now.minusSeconds(3600).toEpochMilli()))
        }
        assertTrue(repository.reserve(page.id, page.version, now, true) is CollectorAdmission.Deferred)
        assertEquals(CollectorRefreshPolicy.MAX_EXTERNAL_REQUESTS_PER_DAY - 1, db.collectorDao().requestCount(0))
    }
}
