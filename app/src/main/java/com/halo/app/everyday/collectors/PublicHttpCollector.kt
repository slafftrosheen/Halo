package com.halo.app.everyday.collectors

import com.halo.app.everyday.organizer.OrganizerIcs
import com.halo.sdk.provider.*
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import okhttp3.*

private data class CollectorRequestScope(val categories: Set<RemoteDataCategory>)

/** All public adapters share one governed transfer owner; parsing and reader rendering never fetch resources. */
class PublicHttpCollector(
    override val kind: CollectorKind,
    private val policy: RemoteDispatchPolicy = DenyRemoteDispatchPolicy,
    baseClient: OkHttpClient = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build(),
    private val clock: Clock = Clock.systemUTC(),
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) : Collector {
    init { require(kind in setOf(CollectorKind.FEED, CollectorKind.WEATHER, CollectorKind.ALERTS, CollectorKind.PUBLIC_CALENDAR)) }
    private val client = baseClient.withRemoteDispatchPolicy(policy, publicGetRedirects = true) { request ->
        RemoteDispatchRequest(PUBLIC_COLLECTOR_OWNER, RemoteOperation.CONNECTED_SERVICE,
            request.tag(CollectorRequestScope::class.java)?.categories ?: setOf(RemoteDataCategory.ACCOUNT_METADATA))
    }
    override suspend fun fetch(source: CollectorSource, cached: CollectorSnapshot?): CollectorFetchResult = coroutineScope {
        require(source.kind == kind && source.external && source.selected && source.enabled) { "Select and enable this source before fetching it" }
        val url = CollectorUrlPolicy.validate(requireNotNull(source.endpoint))
        val revision = RemoteDispatchContext.provenance.get()?.revision ?: RemoteDispatchContext.intent.get()?.revision ?: policy.revision
        val categories = if (kind == CollectorKind.WEATHER) setOf(RemoteDataCategory.LOCATION) else setOf(RemoteDataCategory.ACCOUNT_METADATA)
        val request = Request.Builder().url(url).get().header("User-Agent", "Halo/Everyday")
            .header("Accept", when (kind) { CollectorKind.WEATHER -> "application/json"; CollectorKind.PUBLIC_CALENDAR -> "text/calendar"; else -> "application/atom+xml, application/rss+xml, application/xml, text/xml" })
            .tag(RemoteRequestRevision::class.java, RemoteRequestRevision(revision))
            .tag(CollectorRequestScope::class.java, CollectorRequestScope(categories))
        val cache = cached?.takeIf { it.sourceId == source.id && it.sourceVersion == source.version }
        cache?.etag?.takeIf(::safeValidator)?.let { request.header("If-None-Match", it) }
        cache?.lastModified?.takeIf(::safeValidator)?.let { request.header("If-Modified-Since", it) }
        val call = client.newCall(request.build())
        val job = currentCoroutineContext().job
        val lease = policy.acquire(RemoteDispatchRequest(PUBLIC_COLLECTOR_OWNER, RemoteOperation.CONNECTED_SERVICE, categories),
            revision, recordActivity = false, cancel = { call.cancel(); job.cancel(CancellationException("Collector mode authority changed")) })
        val cancellation = launch(start = CoroutineStart.UNDISPATCHED) { try { awaitCancellation() } finally { call.cancel() } }
        try {
            val response = withContext(Dispatchers.IO) {
                call.execute().use { reply ->
                    if (reply.code == 304) {
                        val finalEtag = reply.request.header("If-None-Match")
                        val finalModified = reply.request.header("If-Modified-Since")
                        // If-None-Match takes precedence over If-Modified-Since. An
                        // unrelated replacement validator cannot verify this cached body.
                        val conditional = if (finalEtag != null) cache?.etag?.takeIf(::safeValidator)?.trim() == finalEtag
                            else finalModified != null && cache?.lastModified?.takeIf(::safeValidator)?.trim() == finalModified
                        return@withContext RawReply(reply.code, null, null, null, reply.request.url.toString(), null, conditional)
                    }
                    if (!reply.isSuccessful) return@withContext RawReply(reply.code, null, null, null, reply.request.url.toString(), retryAfter(reply.header("Retry-After")))
                    val body = reply.body ?: throw IOException("Source response was empty")
                    require(body.contentLength() <= CollectorRefreshPolicy.MAX_BODY_BYTES) { "Source exceeds the download size limit" }
                    val buffer = ByteArray(8192)
                    val output = ByteArrayOutputStream()
                    body.byteStream().use { stream ->
                        while (true) {
                            ensureActive(); lease.requireCurrent()
                            val count = stream.read(buffer)
                            if (count < 0) break
                            require(output.size() + count <= CollectorRefreshPolicy.MAX_BODY_BYTES) { "Source exceeds the download size limit" }
                            output.write(buffer, 0, count)
                        }
                    }
                    RawReply(reply.code, output.toByteArray(), reply.header("ETag")?.takeIf(::safeValidator),
                        reply.header("Last-Modified")?.takeIf(::safeValidator), reply.request.url.toString(), null)
                }
            }
            lease.requireCurrent()
            if (response.code == 304) {
                val previous = cache ?: return@coroutineScope CollectorFetchResult.Unavailable(
                    "Source reported unchanged content, but no matching snapshot is stored")
                // A 304 only verifies a cached body when the final request was conditional.
                // Redirects/interceptors can remove headers even if the initial request had them.
                if (!response.conditional) return@coroutineScope CollectorFetchResult.Unavailable(
                    "Source returned unchanged content without a conditional request. Cached data was not revalidated",
                    resetValidators = true)
                // Conditional validators belong to the actual final resource that supplied
                // the body, not merely to an unchanged source ID or a redirecting entry URL.
                // Never use another final URL's 304 as proof that this cached body is fresh.
                val resourceUnchanged = runCatching {
                    val expected = CollectorUrlPolicy.validate(previous.effectiveUrl ?: requireNotNull(source.endpoint))
                        .newBuilder().fragment(null).build()
                    val actual = CollectorUrlPolicy.validate(response.url).newBuilder().fragment(null).build()
                    expected == actual
                }.getOrDefault(false)
                if (!resourceUnchanged) return@coroutineScope CollectorFetchResult.Unavailable(
                    "The feed destination changed. Cached news remains available; refresh will fetch the new destination",
                    resetValidators = true)
                return@coroutineScope CollectorFetchResult.NotModified
            }
            if (response.bytes == null) return@coroutineScope CollectorFetchResult.Unavailable(
                if (response.code == 429) "Source request limit reached" else "Source returned HTTP ${response.code}", response.retryAfter)
            val payload = withContext(Dispatchers.Default) {
                when (kind) {
                    CollectorKind.FEED -> CollectorPayload.Articles(FeedParser.parse(response.bytes, source.copy(endpoint = response.url)).articles)
                    CollectorKind.ALERTS -> CollectorPayload.Warnings(FeedParser.parse(response.bytes, source.copy(endpoint = response.url)).warnings
                        .filter { it.expires == null || it.expires > clock.instant() })
                    CollectorKind.WEATHER -> CollectorPayload.Weather(OpenMeteoParser.parse(response.bytes, requireNotNull(source.place)))
                    CollectorKind.PUBLIC_CALENDAR -> CollectorPayload.Calendar(OrganizerIcs.preview(response.bytes, source.id, zone(), clock.instant()).also {
                        require(it.records.size <= CollectorRefreshPolicy.MAX_ITEMS) { "This public calendar is too large; choose a narrower calendar feed" }
                    })
                    else -> error("Unsupported public collector")
                }
            }
            CollectorPayloadLimits.validate(payload)
            lease.requireCurrent(); ensureActive()
            CollectorFetchResult.Updated(payload, MessageDigest.getInstance("SHA-256").digest(response.bytes).joinToString("") { "%02x".format(it) },
                response.etag, response.lastModified, response.url)
        } catch (cancelled: CancellationException) { throw cancelled }
          catch (blocked: RemoteDispatchBlocked) { throw blocked }
          catch (_: IOException) { CollectorFetchResult.Unavailable("Source could not be reached. The previous snapshot is retained", offline = true) }
          catch (_: IllegalArgumentException) { CollectorFetchResult.Unavailable("Source data is invalid, unsupported, or exceeds the safety limits. The previous snapshot is retained") }
          catch (_: Exception) { CollectorFetchResult.Unavailable("Source data could not be parsed. Check the feed address or try again later; the previous snapshot is retained") }
          finally { cancellation.cancel(); lease.close() }
    }
    private fun safeValidator(value: String) = value.isNotBlank() && value.length <= 1024 && value.none { it.isISOControl() }
    private fun retryAfter(value: String?): Duration? = value?.let {
        it.toLongOrNull()?.let { seconds -> Duration.ofSeconds(seconds.coerceIn(0, 86400)) }
            ?: runCatching { Duration.between(clock.instant(), ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant())
                .coerceIn(Duration.ZERO, Duration.ofDays(1)) }.getOrNull()
    }
    private data class RawReply(val code: Int, val bytes: ByteArray?, val etag: String?, val lastModified: String?, val url: String,
        val retryAfter: Duration?, val conditional: Boolean = false)
}
