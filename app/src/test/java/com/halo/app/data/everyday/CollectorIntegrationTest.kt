package com.halo.app.data.everyday

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import com.halo.app.everyday.collectors.*
import com.halo.app.everyday.collectors.android.CollectorBootstrap
import com.halo.app.everyday.organizer.*
import com.halo.sdk.provider.*
import java.time.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CollectorIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val repository = RoomCollectorRepository(db)
    private val start = Instant.parse("2026-11-01T10:00:00Z")
    private val clock = MutableClock(start)
    private val policy = TestPolicy()
    private fun source(id: String = "news") = CollectorSource(id, "News", "Publisher", CollectorKind.FEED,
        SourceCategory.WORLD, "en", "https://example.org/feed", selected = true)
    private fun snapshot(source: CollectorSource) = CollectorSnapshot(source.id, source.version,
        CollectorPayload.Articles(listOf(ArticleFact("article", "Real fixture", "Text", "https://example.org/story", "Publisher", start, "en"))),
        "revision", start, start, start.plusSeconds(3600), "v1", "Sun, 01 Nov 2026 10:00:00 GMT", source.endpoint)
    private fun adapter(block: suspend (CollectorSource, CollectorSnapshot?) -> CollectorFetchResult) = object : Collector {
        override val kind = CollectorKind.FEED
        override suspend fun fetch(source: CollectorSource, cached: CollectorSnapshot?) = block(source, cached)
    }
    private fun owner(collector: Collector) = CollectorCoordinator(repository, CollectorRegistry(listOf(collector)), policy, clock)
    private suspend fun reserve(source: CollectorSource, now: Instant = clock.instant()) =
        (repository.reserve(source.id, source.version, now, true) as CollectorAdmission.Accepted).reservation
    @After fun close() { db.close(); context.getSharedPreferences("everyday-catalogue", 0).edit().clear().commit() }

    @Test fun localBlocksBeforeAdapterAndBudgetButInternalFactsStillWork() = runBlocking {
        val selected = repository.save(source(), null)
        var calls = 0
        owner(adapter { _, _ -> calls++; error("Local reached adapter") }).refresh(selected.id, true)
        assertEquals(0, calls); assertEquals(0, db.collectorDao().requestCount(0))
        assertEquals(CollectorAttemptState.LOCAL_ONLY, repository.state(selected.id)!!.attempt.state)
        val local = repository.save(StarterSourceCatalogue.local().first(), null)
        val localOwner = owner(LocalStateCollector(local.kind) { listOf(LocalFact("one", "TASK", "Plan", "", "halo://organizer", "1", start)) })
        localOwner.refresh(local.id)
        assertEquals(CollectorAttemptState.CURRENT, repository.state(local.id)!!.attempt.state)
        assertEquals(0, db.collectorDao().requestCount(0))
    }
    @Test fun conditionalSuccessChangesVerificationNotRetrievalAndFailureKeepsBody() = runBlocking {
        policy.change(true)
        val selected = repository.save(source(), null)
        var phase = 0
        val coordinator = owner(adapter { _, _ -> when (phase++) {
            0 -> snapshot(selected).let { CollectorFetchResult.Updated(it.payload, it.contentRevision, it.etag, it.lastModified, it.effectiveUrl) }
            1 -> CollectorFetchResult.NotModified
            else -> CollectorFetchResult.Unavailable("offline", offline = true)
        } })
        coordinator.refresh(selected.id, true); clock.advance(3700)
        coordinator.refresh(selected.id, true)
        val confirmed = repository.state(selected.id)!!.snapshot!!
        assertEquals(start, confirmed.retrievedAt); assertEquals(clock.instant(), confirmed.verifiedAt)
        clock.advance(3700); coordinator.refresh(selected.id, true)
        assertEquals(confirmed, repository.state(selected.id)!!.snapshot)
        assertEquals(CollectorAttemptState.OFFLINE, repository.state(selected.id)!!.attempt.state)
    }
    @Test fun editedOrRemovedSourceRejectsOldPublicationAndMetadataEditsRetainBackoff() = runBlocking {
        val selected = repository.save(source(), null)
        val reserved = reserve(selected)
        assertTrue(repository.finish(reserved, snapshot(selected), CollectorAttempt(selected.id, start, CollectorAttemptState.BACKOFF,
            1, start.plusSeconds(7200), "Server retry delay")))
        val edited = repository.save(selected.copy(title = "Renamed", selected = false), selected.version)
        val enabled = repository.save(edited.copy(selected = true), edited.version)
        val recreated = RoomCollectorRepository(db)
        assertEquals(CollectorAttemptState.BACKOFF, recreated.state(enabled.id)!!.attempt.state)
        assertEquals(start.plusSeconds(7200), recreated.state(enabled.id)!!.attempt.nextAllowedAt)
        assertTrue(recreated.reserve(enabled.id, enabled.version, start.plusSeconds(600), true) is CollectorAdmission.Deferred)
        assertFalse(recreated.finish(reserved, snapshot(selected), CollectorAttempt(selected.id, start, CollectorAttemptState.CURRENT)))
        assertEquals(enabled.version, recreated.state(enabled.id)!!.snapshot!!.sourceVersion)
        val moved = recreated.save(enabled.copy(endpoint = "https://example.org/other"), enabled.version)
        assertNull(recreated.state(moved.id)!!.snapshot)
        recreated.remove(moved.id, moved.version)
        assertFalse(recreated.finish(reserved, snapshot(selected), CollectorAttempt(selected.id, start, CollectorAttemptState.CURRENT)))
    }
    @Test fun processRecoveryFencesInterruptedTokenAndPreservesSnapshot() = runBlocking {
        val selected = repository.save(source(), null)
        val first = reserve(selected)
        repository.finish(first, snapshot(selected), CollectorAttempt(selected.id, start, CollectorAttemptState.CURRENT))
        clock.advance(61); val interrupted = reserve(selected)
        RoomCollectorRepository(db).recoverInterrupted(clock.instant())
        assertEquals(CollectorAttemptState.CANCELLED, repository.state(selected.id)!!.attempt.state)
        assertEquals(snapshot(selected), repository.state(selected.id)!!.snapshot)
        assertFalse(repository.finish(interrupted, snapshot(selected), CollectorAttempt(selected.id, clock.instant(), CollectorAttemptState.CURRENT)))
    }
    @Test fun sourceQuotaSurvivesDeleteReAddAndPreviewHasCooldown() = runBlocking {
        var selected = repository.save(source(), null)
        repeat(CollectorRefreshPolicy.MAX_SOURCE_REQUESTS_PER_DAY) {
            val reservation = reserve(selected)
            repository.finish(reservation, null, CollectorAttempt(selected.id, clock.instant(), CollectorAttemptState.CURRENT))
            clock.advance(61)
        }
        repository.remove(selected.id, selected.version); selected = repository.save(source(), null)
        val deferred = repository.reserve(selected.id, selected.version, clock.instant(), true) as CollectorAdmission.Deferred
        assertEquals(CollectorAttemptState.QUOTA, deferred.attempt.state)
        assertTrue(repository.reservePreview("preview:one", clock.instant()))
        assertFalse(repository.reservePreview("preview:one", clock.instant().plusSeconds(59)))
        assertTrue(repository.reservePreview("preview:one", clock.instant().plusSeconds(61)))
        clock.advance(86401); assertTrue(repository.reserve(selected.id, selected.version, clock.instant(), true) is CollectorAdmission.Accepted)
    }
    @Test fun globalQuotaCapsDifferentSourcesAndRollbackDoesNotResetIt() = runBlocking {
        repeat(CollectorRefreshPolicy.MAX_EXTERNAL_REQUESTS_PER_DAY) { assertTrue(repository.reservePreview("preview:$it", start)) }
        assertFalse(repository.reservePreview("preview:extra", start))
        assertFalse(repository.reservePreview("preview:rollback", start.minusSeconds(3600)))
    }
    @Test fun modeChangeCancelsAdapterWithoutCancellingWorkerOrPublishingLateBody() = runBlocking {
        policy.change(true); val selected = repository.save(source(), null)
        val began = CompletableDeferred<Unit>(); var cancelled = false
        val coordinator = owner(adapter { _, _ -> began.complete(Unit); try { awaitCancellation() } finally { cancelled = true } })
        val refresh = async { coordinator.refresh(selected.id, true) }
        began.await(); policy.change(false); refresh.await()
        assertTrue(cancelled); assertTrue(currentCoroutineContext().isActive)
        assertEquals(CollectorAttemptState.CANCELLED, repository.state(selected.id)!!.attempt.state)
        assertNull(repository.state(selected.id)!!.snapshot)
        assertEquals(1, db.collectorDao().requestCount(0))
    }
    @Test fun sourceVersionWatcherCancelsAnActiveRead() = runBlocking {
        policy.change(true); val selected = repository.save(source(), null)
        val began = CompletableDeferred<Unit>(); val cancelled = CompletableDeferred<Unit>()
        val coordinator = owner(adapter { _, _ -> began.complete(Unit); try { awaitCancellation() } finally { cancelled.complete(Unit) } })
        val refresh = async { coordinator.refresh(selected.id, true) }
        began.await(); repository.save(selected.copy(enabled = false), selected.version)
        withTimeout(5000) { cancelled.await(); refresh.await() }
        assertNull(repository.state(selected.id)!!.snapshot)
        assertFalse(repository.state(selected.id)!!.source.enabled)
    }
    @Test fun parentCancellationFinalizesDurableAttemptBeforePropagating() = runBlocking {
        policy.change(true); val selected = repository.save(source(), null)
        val began = CompletableDeferred<Unit>()
        val coordinator = owner(adapter { _, _ -> began.complete(Unit); awaitCancellation() })
        val refresh = launch { coordinator.refresh(selected.id, true) }
        began.await(); refresh.cancelAndJoin()
        assertEquals(CollectorAttemptState.CANCELLED, repository.state(selected.id)!!.attempt.state)
        assertNull(db.collectorDao().attempt(selected.id)!!.token)
    }
    @Test fun serverBackoffSurvivesLocalModeAttemptAndPreventsManualBypass() = runBlocking {
        policy.change(true); val selected = repository.save(source(), null); var calls = 0
        val coordinator = owner(adapter { _, _ -> calls++; CollectorFetchResult.Unavailable("Rate limited", Duration.ofHours(4)) })
        coordinator.refresh(selected.id, true)
        val delay = repository.state(selected.id)!!.attempt.nextAllowedAt
        policy.change(false); clock.advance(120); coordinator.refresh(selected.id, true)
        assertEquals(delay, repository.state(selected.id)!!.attempt.nextAllowedAt)
        policy.change(true); coordinator.refresh(selected.id, true)
        assertEquals(1, calls)
    }
    @Test fun catalogueStartsLocalAndDeletedSourcesDoNotReturnAfterRestart() = runBlocking {
        val bootstrap = CollectorBootstrap(context, repository); bootstrap.ensureInitialized()
        assertEquals(5, repository.states().count { it.source.selected })
        assertTrue(repository.states().filter { it.source.external }.none { it.source.selected })
        val removed = repository.states().first { it.source.external }.source
        repository.remove(removed.id, removed.version)
        CollectorBootstrap(context, RoomCollectorRepository(db)).ensureInitialized()
        assertNull(repository.state(removed.id))
        assertEquals(0, db.collectorDao().requestCount(0))
    }
    @Test fun typedCodecRoundTripsAndRejectsDeepExternalJson() {
        val weather = CollectorPayload.Weather(WeatherFact(StarterSourceCatalogue.riga, start, 2.5, 3,
            listOf(WeatherDay(LocalDate.parse("2026-11-01"), 5.0, -1.0, 80, 61))))
        assertEquals(weather, CollectorCodec.payload(CollectorCodec.encode(weather)))
        assertEquals(source(), CollectorCodec.source(CollectorCodec.encode(source())))
        assertThrows(IllegalArgumentException::class.java) { SafeCollectorJson.objectValue("[".repeat(70) + "0" + "]".repeat(70)) }
        assertThrows(IllegalArgumentException::class.java) { SafeCollectorJson.objectValue("{]") }
    }
    @Test fun httpAdapterSendsValidatorsAndParsesBoundedSanitizedFeed() = runBlocking {
        policy.change(true); val selected = source(); val requests = CopyOnWriteArrayList<Request>()
        var conditional = false
        val base = OkHttpClient.Builder().addInterceptor { chain ->
            requests += chain.request()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).message("fixture")
                .code(if (conditional) 304 else 200).header("ETag", "v2")
                .body("<rss><channel><item><guid>1</guid><title>Headline</title><description><![CDATA[<p>Story</p><img src='https://tracker.test/a'>]]></description></item></channel></rss>".toResponseBody()).build()
        }.build()
        val adapter = PublicHttpCollector(CollectorKind.FEED, policy, base, clock)
        val reply = adapter.fetch(selected, null) as CollectorFetchResult.Updated
        assertEquals("Story", (reply.payload as CollectorPayload.Articles).items.single().text)
        conditional = true
        assertEquals(CollectorFetchResult.NotModified, adapter.fetch(selected, snapshot(selected)))
        assertEquals("v1", requests.last().header("If-None-Match"))
        assertEquals(snapshot(selected).lastModified, requests.last().header("If-Modified-Since"))
        assertTrue(adapter.fetch(selected, null) is CollectorFetchResult.Unavailable)
        policy.change(false)
        assertThrows(RemoteDispatchBlocked::class.java) { runBlocking { adapter.fetch(selected, null) } }
        assertEquals(3, requests.size)
    }
    @Test fun changedConditionalDestinationKeepsCachedBodyButDropsOldValidatorsBeforeRetry() = runBlocking {
        policy.change(true)
        val selected = repository.save(source(), null)
        val existing = snapshot(selected)
        val initial = reserve(selected)
        assertTrue(repository.finish(initial, existing, CollectorAttempt(selected.id, start, CollectorAttemptState.CURRENT)))
        clock.advance(3700)

        val requests = CopyOnWriteArrayList<Request>()
        val movedUrl = "https://example.org/redirected"
        val base = OkHttpClient.Builder().addInterceptor { chain ->
            requests += chain.request()
            // Deterministic fixture for OkHttp's final URL after an HTTP redirect.
            val finalRequest = chain.request().newBuilder().url(movedUrl).build()
            Response.Builder().request(finalRequest).protocol(Protocol.HTTP_1_1).message("fixture")
                .code(if (requests.size == 1) 304 else 200)
                .body("<rss><channel><item><guid>two</guid><title>Moved feed</title></item></channel></rss>".toResponseBody())
                .build()
        }.build()
        val coordinator = owner(PublicHttpCollector(CollectorKind.FEED, policy, base, clock))
        coordinator.refresh(selected.id, manual = true)

        val withheld = requireNotNull(repository.state(selected.id))
        assertEquals(CollectorAttemptState.FAILED, withheld.attempt.state)
        assertEquals(existing.payload, withheld.snapshot?.payload)
        assertEquals(existing.retrievedAt, withheld.snapshot?.retrievedAt)
        assertEquals(existing.verifiedAt, withheld.snapshot?.verifiedAt)
        assertNull(withheld.snapshot?.etag)
        assertNull(withheld.snapshot?.lastModified)
        assertEquals("v1", requests.single().header("If-None-Match"))

        // After the bounded backoff, a new permitted request is unconditional.
        clock.advance(86_401)
        coordinator.refresh(selected.id, manual = true)
        val refreshed = requireNotNull(repository.state(selected.id)).snapshot!!
        assertEquals(movedUrl, refreshed.effectiveUrl)
        assertTrue(refreshed.retrievedAt > existing.retrievedAt)
        assertEquals("Moved feed", (refreshed.payload as CollectorPayload.Articles).items.single().title)
        assertEquals(2, requests.size)
        assertNull(requests.last().header("If-None-Match"))
        assertNull(requests.last().header("If-Modified-Since"))
    }

    @Test fun httpAdapterRejectsOversizeAndReturnsRetryAfterWithoutClobberingCache() = runBlocking {
        policy.change(true); var status = 429
        val base = OkHttpClient.Builder().addInterceptor { chain -> Response.Builder().request(chain.request())
            .protocol(Protocol.HTTP_1_1).message("fixture").code(status).header("Retry-After", "1800")
            .body((if (status == 200) "x".repeat(CollectorRefreshPolicy.MAX_BODY_BYTES + 1) else "").toResponseBody()).build() }.build()
        val adapter = PublicHttpCollector(CollectorKind.FEED, policy, base, clock)
        assertEquals(Duration.ofMinutes(30), (adapter.fetch(source(), null) as CollectorFetchResult.Unavailable).retryAfter)
        status = 200; assertTrue(adapter.fetch(source(), snapshot(source())) is CollectorFetchResult.Unavailable)
    }
    @Test fun weatherRequiresDeclaredUnitsAndAlignedForecastArrays() {
        val body = """{"current_units":{"time":"unixtime","temperature_2m":"°C"},"current":{"time":1793520000,"temperature_2m":4.5,"weather_code":3},"daily_units":{"time":"unixtime","temperature_2m_max":"°C","temperature_2m_min":"°C","precipitation_probability_max":"%"},"daily":{"time":[1793484000],"temperature_2m_max":[7],"temperature_2m_min":[2],"precipitation_probability_max":[65],"weather_code":[61]}}"""
        val fact = OpenMeteoParser.parse(body.toByteArray(), StarterSourceCatalogue.riga)
        assertEquals(4.5, fact.temperatureC!!, 0.01); assertEquals(65, fact.days.single().precipitationProbability)
        assertThrows(IllegalArgumentException::class.java) { OpenMeteoParser.parse(body.replace("°C", "°F").toByteArray(), StarterSourceCatalogue.riga) }
        assertThrows(IllegalArgumentException::class.java) { OpenMeteoParser.parse(body.replace("[7]", "[]").toByteArray(), StarterSourceCatalogue.riga) }
        assertTrue(OpenMeteoParser.endpoint(StarterSourceCatalogue.riga).contains("latitude=56.95"))
    }

    @Test fun oversizedCalendarIsRejectedBeforePublishAndUnreadableCacheDoesNotPoisonOtherSources() = runBlocking {
        val selected = repository.save(source(), null)
        val second = repository.save(source("second").copy(endpoint = "https://example.org/second"), null)
        val event = OrganizerRecord("event", OrganizerKind.EVENT, "Event", time = OrganizerTime(LocalDateTime.parse("2026-11-01T10:00"),
            LocalDateTime.parse("2026-11-01T11:00"), "UTC"), createdAt = start)
        val oversized = CollectorPayload.Calendar(CalendarImportPreview(listOf(event), (0..2000).map {
            OccurrenceOverride(event.id, start.plusSeconds(it * 86400L), OccurrenceDisposition.CANCELLED)
        }, emptyList()))
        assertThrows(IllegalArgumentException::class.java) { CollectorCodec.encode(oversized) }
        val saved = CollectorCodec.entity(snapshot(selected))
        db.collectorDao().putSnapshot(saved.copy(payload = "{broken"))
        assertEquals(2, repository.states().size)
        assertNull(repository.state(selected.id)!!.snapshot)
        assertEquals(CollectorAttemptState.FAILED, repository.state(selected.id)!!.attempt.state)
        assertNotNull(repository.state(second.id))
    }
    @Test fun duplicateSourcesAndPreviewShareEndpointCooldownBudgetAndServerBackoff() = runBlocking {
        val first = repository.save(source(), null)
        val duplicate = repository.save(source("duplicate"), null)
        val previewId = "preview:${CollectorEndpointIdentity.fingerprint(first.endpoint!!)}"
        assertTrue(repository.reservePreview(previewId, start))
        repository.finishPreview(previewId, start, start, CollectorFetchResult.Unavailable("429", Duration.ofHours(4)))
        assertFalse(repository.reservePreview(previewId, start.plusSeconds(61)))
        assertTrue(repository.reserve(duplicate.id, duplicate.version, start.plusSeconds(61), true) is CollectorAdmission.Deferred)
        repository.remove(first.id, first.version)
        val readded = repository.save(source("new-uuid"), null)
        assertTrue(repository.reserve(readded.id, readded.version, start.plusSeconds(61), true) is CollectorAdmission.Deferred)
        clock.advance(14401)
        val accepted = reserve(readded)
        assertFalse(repository.reservePreview(previewId, clock.instant().plusSeconds(1)))
        assertTrue(repository.finish(accepted, null, CollectorAttempt(readded.id, clock.instant(), CollectorAttemptState.CURRENT)))
    }
    @Test fun manualRefreshReturnsCooldownReasonInsteadOfPreviousSuccess() = runBlocking {
        policy.change(true); val selected = repository.save(source(), null)
        val coordinator = owner(adapter { _, _ -> CollectorFetchResult.Updated(CollectorPayload.Articles(emptyList()), "one", null, null, null) })
        coordinator.refresh(selected.id, true)
        clock.advance(1)
        val deferred = coordinator.refreshDetailed(selected.id, true)
        assertFalse(deferred.admitted)
        assertEquals(CollectorAttemptState.BACKOFF, deferred.state!!.attempt.state)
        assertTrue(deferred.state!!.attempt.message!!.contains("minute"))
        assertEquals(1, db.collectorDao().requestCount(0))
    }
    @Test fun deniedWeatherDoesNotConsumeTransferSlotsAheadOfAllowedNews() = runBlocking {
        policy.change(true); val news = repository.save(source(), null)
        val previous = reserve(news)
        repository.finish(previous, snapshot(news), CollectorAttempt(news.id, start, CollectorAttemptState.CURRENT))
        clock.advance(3700)
        repeat(5) { repository.save(StarterSourceCatalogue.weather(StarterSourceCatalogue.riga, "weather:$it"), null) }
        policy.deniedCategory = RemoteDataCategory.LOCATION
        var newsCalls = 0; var weatherCalls = 0
        val weather = object : Collector {
            override val kind = CollectorKind.WEATHER
            override suspend fun fetch(source: CollectorSource, cached: CollectorSnapshot?): CollectorFetchResult { weatherCalls++; error("Denied location reached adapter") }
        }
        val coordinator = CollectorCoordinator(repository, CollectorRegistry(listOf(weather,
            adapter { _, _ -> newsCalls++; CollectorFetchResult.NotModified })), policy, clock)
        coordinator.refreshDue(maximum = 1, external = true)
        assertEquals(0, weatherCalls); assertEquals(1, newsCalls)
        assertEquals(clock.instant(), repository.state(news.id)!!.snapshot!!.verifiedAt)
        assertEquals(2, db.collectorDao().requestCount(0))
    }

    @Test fun endpointFailureLineageSurvivesNewSourceAndCancellationUntilAnActualSuccess() = runBlocking {
        policy.change(true)
        val first = repository.save(source(), null)
        val key = CollectorEndpointIdentity.budgetKey(first.endpoint!!)
        val failing = adapter { _, _ -> CollectorFetchResult.Unavailable("Server unavailable") }
        owner(failing).refresh(first.id, true)
        assertEquals(1, db.collectorDao().endpoint(key)!!.failures)
        clock.now = Instant.ofEpochMilli(db.collectorDao().endpoint(key)!!.blockedUntil!! + 1)
        val duplicate = repository.save(source("duplicate"), null)
        owner(failing).refresh(duplicate.id, true)
        assertEquals(2, db.collectorDao().endpoint(key)!!.failures)
        clock.now = Instant.ofEpochMilli(db.collectorDao().endpoint(key)!!.blockedUntil!! + 1)
        val began = CompletableDeferred<Unit>()
        val cancelling = owner(adapter { _, _ -> began.complete(Unit); awaitCancellation() })
        val task = launch { cancelling.refresh(first.id, true) }
        began.await(); task.cancelAndJoin()
        assertEquals(2, db.collectorDao().endpoint(key)!!.failures)
        clock.advance(61)
        owner(failing).refresh(first.id, true)
        assertEquals(3, db.collectorDao().endpoint(key)!!.failures)
        clock.now = Instant.ofEpochMilli(db.collectorDao().endpoint(key)!!.blockedUntil!! + 1)
        owner(adapter { _, _ -> CollectorFetchResult.Updated(CollectorPayload.Articles(emptyList()), "success", null, null, null) }).refresh(duplicate.id, true)
        assertEquals(0, db.collectorDao().endpoint(key)!!.failures)
        clock.advance(61); owner(failing).refresh(first.id, true)
        assertEquals(1, db.collectorDao().endpoint(key)!!.failures)
    }

    private class MutableClock(var now: Instant) : Clock() {
        fun advance(seconds: Long) { now = now.plusSeconds(seconds) }
        override fun instant() = now
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: ZoneId) = this
    }
    private class TestPolicy : RemoteDispatchPolicy {
        var deniedCategory: RemoteDataCategory? = null
        @Volatile override var revision = 0L
        private var allowed = false
        private val ids = AtomicInteger()
        private val active = mutableMapOf<Int, () -> Unit>()
        fun change(next: Boolean) {
            val callbacks = synchronized(this) { allowed = next; revision++; active.values.toList().also { active.clear() } }
            callbacks.forEach { it() }
        }
        @Synchronized override fun acquire(request: RemoteDispatchRequest, expectedRevision: Long, recordActivity: Boolean, cancel: () -> Unit): RemoteDispatchLease {
            if (!allowed || revision != expectedRevision || deniedCategory in request.categories) throw RemoteDispatchBlocked()
            val id = ids.incrementAndGet(); active[id] = cancel
            return object : RemoteDispatchLease {
                override fun requireCurrent() { synchronized(this@TestPolicy) { if (!allowed || revision != expectedRevision) throw RemoteDispatchBlocked() } }
                override fun finish(outcome: RemoteDispatchOutcome, providerReportedCostUsd: String?) { synchronized(this@TestPolicy) { active.remove(id) } }
            }
        }
    }
}
