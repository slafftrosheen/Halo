package com.halo.app.everyday.collectors

import java.time.Duration
import java.time.Instant

enum class CollectorKind { LOCAL_ORGANIZER, LOCAL_WORKSPACE, LOCAL_ACTIVITY, LOCAL_NOTIFICATIONS, LOCAL_DEVICE, FEED, WEATHER, ALERTS, PUBLIC_CALENDAR }
enum class SourceCategory { PERSONAL, LATVIA, WORLD, SCIENCE, TECHNOLOGY, WEATHER, OFFICIAL, CALENDAR, CUSTOM }
enum class CollectorAttemptState { NEVER, FETCHING, CURRENT, NOT_MODIFIED, LOCAL_ONLY, OFFLINE, DISABLED, BACKOFF, QUOTA, FAILED }

data class WeatherPlace(val name: String, val latitude: Double, val longitude: Double, val zoneId: String) {
    init {
        require(name.isNotBlank() && name.length <= 120)
        require(latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0)
        java.time.ZoneId.of(zoneId)
    }
}
data class CollectorSource(
    val id: String,
    val title: String,
    val publisher: String,
    val kind: CollectorKind,
    val category: SourceCategory,
    val language: String,
    val endpoint: String? = null,
    val attributionUrl: String? = null,
    val selected: Boolean = false,
    val enabled: Boolean = true,
    /** Muted sources still update when selected, but do not enter briefs or proactive delivery. */
    val muted: Boolean = false,
    val refreshMinutes: Int = 60,
    val place: WeatherPlace? = null,
    val childApproved: Boolean = false,
    val version: Long = 1,
) {
    val external: Boolean get() = kind in setOf(CollectorKind.FEED, CollectorKind.WEATHER, CollectorKind.ALERTS, CollectorKind.PUBLIC_CALENDAR)
    init {
        require(id.matches(Regex("[A-Za-z0-9_.:-]{1,160}")))
        require(title.isNotBlank() && title.length <= 200 && publisher.isNotBlank() && publisher.length <= 200)
        require(language.matches(Regex("[a-z]{2,3}(-[A-Za-z0-9]{2,8})*")))
        require(refreshMinutes in 15..1440 && version > 0)
        require(!external || !endpoint.isNullOrBlank())
        require(kind != CollectorKind.WEATHER || place != null)
        require(endpoint == null || endpoint.length <= 4096)
    }
}

data class ArticleFact(val id: String, val title: String, val text: String, val url: String?, val publisher: String,
    val publishedAt: Instant?, val language: String, val categories: List<String> = emptyList())
data class WeatherDay(val date: java.time.LocalDate, val maximumC: Double?, val minimumC: Double?,
    val precipitationProbability: Int?, val weatherCode: Int?)
data class WeatherFact(val place: WeatherPlace, val observedAt: Instant?, val temperatureC: Double?, val weatherCode: Int?,
    val days: List<WeatherDay>, val attribution: String = "Open-Meteo", val attributionUrl: String = "https://open-meteo.com/")
data class WarningFact(val id: String, val title: String, val text: String, val area: String?, val severity: String?,
    val onset: Instant?, val expires: Instant?, val url: String?, val publisher: String)
data class LocalFact(val id: String, val kind: String, val title: String, val summary: String,
    val route: String, val revision: String?, val occurredAt: Instant?)

sealed interface CollectorPayload {
    data class Articles(val items: List<ArticleFact>) : CollectorPayload
    data class Weather(val fact: WeatherFact) : CollectorPayload
    data class Warnings(val items: List<WarningFact>) : CollectorPayload
    data class Calendar(val preview: com.halo.app.everyday.organizer.CalendarImportPreview) : CollectorPayload
    data class Local(val items: List<LocalFact>) : CollectorPayload
}

data class CollectorSnapshot(
    val sourceId: String,
    val sourceVersion: Long,
    val payload: CollectorPayload,
    val contentRevision: String,
    /** Time this body was actually retrieved. A failed attempt or HTTP 304 never rewrites it. */
    val retrievedAt: Instant,
    /** A successful conditional response can confirm that the cached body remains current. */
    val verifiedAt: Instant,
    val expiresAt: Instant,
    val etag: String? = null,
    val lastModified: String? = null,
    val effectiveUrl: String? = null,
) {
    fun stale(now: Instant, external: Boolean, power: Boolean) = external && (!power || now >= expiresAt)
}
data class CollectorAttempt(val sourceId: String, val at: Instant?, val state: CollectorAttemptState,
    val consecutiveFailures: Int = 0, val nextAllowedAt: Instant? = null, val message: String? = null)

sealed interface CollectorFetchResult {
    data class Updated(val payload: CollectorPayload, val contentRevision: String, val etag: String?, val lastModified: String?, val effectiveUrl: String?) : CollectorFetchResult
    data object NotModified : CollectorFetchResult
    data class Unavailable(val reason: String, val retryAfter: Duration? = null, val offline: Boolean = false) : CollectorFetchResult
}

interface Collector {
    val kind: CollectorKind
    suspend fun fetch(source: CollectorSource, cached: CollectorSnapshot?): CollectorFetchResult
}

/** Registry of real adapters; unsupported optional integrations are never exposed as available. */
class CollectorRegistry(adapters: List<Collector>) {
    private val owners = adapters.associateBy { it.kind }.also { require(it.size == adapters.size) { "Duplicate collector owner" } }
    fun supports(kind: CollectorKind): Boolean = kind in owners
    fun require(kind: CollectorKind): Collector = requireNotNull(owners[kind]) { "This source type is not installed" }
    fun kinds(): Set<CollectorKind> = owners.keys
}

object CollectorRefreshPolicy {
    const val MAX_BODY_BYTES = 2 * 1024 * 1024
    const val MAX_ITEMS = 100
    const val MAX_SOURCE_COUNT = 100
    const val MAX_EXTERNAL_REQUESTS_PER_DAY = 500
    const val MAX_SOURCE_REQUESTS_PER_DAY = 96
    fun nextAttempt(source: CollectorSource, now: Instant, failures: Int, serverDelay: Duration? = null): Instant {
        require(failures >= 0)
        val base = source.refreshMinutes.toLong() * 60
        val exponential = (base * (1L shl failures.coerceAtMost(8))).coerceAtMost(86400)
        // Stable bounded jitter spreads different sources without unpredictably changing after restart.
        val jitter = if (failures == 0) 0 else (source.id.hashCode().toLong().and(0x7fffffff) % 60)
        val requested = serverDelay?.seconds?.coerceIn(0, 86400) ?: 0
        return now.plusSeconds(maxOf(exponential + jitter, requested).coerceAtMost(86400))
    }
}
