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
    init { require(kind in setOf(CollectorKind.FEED, CollectorKind.WEATHER, CollectorKind.ALERTS, CollectorKind.PUBLIC_CALENDAR, CollectorKind.RESEARCH)) }
    private val client = baseClient.withRemoteDispatchPolicy(policy, publicGetRedirects = true) { request ->
        RemoteDispatchRequest(PUBLIC_COLLECTOR_OWNER, RemoteOperation.CONNECTED_SERVICE,
            request.tag(CollectorRequestScope::class.java)?.categories ?: setOf(RemoteDataCategory.ACCOUNT_METADATA))
    }
    private val pageClient = baseClient.newBuilder().dns(CollectorUrlPolicy.dns(baseClient.dns))
        .proxy(java.net.Proxy.NO_PROXY).cookieJar(CookieJar.NO_COOKIES).authenticator(Authenticator.NONE)
        .proxyAuthenticator(Authenticator.NONE).cache(null).callTimeout(15, TimeUnit.SECONDS).build()
        .withRemoteDispatchPolicy(policy, publicGetRedirects = false) { request ->
            RemoteDispatchRequest(PUBLIC_COLLECTOR_OWNER, RemoteOperation.CONNECTED_SERVICE,
                request.tag(CollectorRequestScope::class.java)?.categories ?: setOf(RemoteDataCategory.TEXT, RemoteDataCategory.WORKSPACE))
        }
    override suspend fun fetch(source: CollectorSource, cached: CollectorSnapshot?): CollectorFetchResult = coroutineScope {
        com.halo.app.everyday.EverydayEffectAuthority.requireCurrent()
        require(source.kind == kind && source.external && source.selected && source.enabled) { "Select and enable this source before fetching it" }
        val url = CollectorUrlPolicy.validate(requireNotNull(source.endpoint))
        val publicPage = source.research?.providerId == PublicPageResearchProvider.id
        // Redirects could bypass another path's robots rules. Select the final page URL explicitly.
        val transferClient = if (publicPage) pageClient else client
        val secondary = java.util.concurrent.atomic.AtomicReference<Call?>()
        val revision = RemoteDispatchContext.provenance.get()?.revision ?: RemoteDispatchContext.intent.get()?.revision ?: policy.revision
        val categories = when (kind) {
            CollectorKind.WEATHER -> setOf(RemoteDataCategory.LOCATION)
            CollectorKind.RESEARCH -> setOf(RemoteDataCategory.TEXT, RemoteDataCategory.WORKSPACE)
            else -> setOf(RemoteDataCategory.ACCOUNT_METADATA)
        }
        val request = Request.Builder().url(url).get().header("User-Agent", "Halo/Everyday")
            .header("Accept", if (publicPage) "text/html, application/xhtml+xml, text/plain;q=0.9" else when (kind) { CollectorKind.WEATHER, CollectorKind.RESEARCH -> "application/json"; CollectorKind.PUBLIC_CALENDAR -> "text/calendar"; else -> "application/atom+xml, application/rss+xml, application/xml, text/xml" })
            .tag(RemoteRequestRevision::class.java, RemoteRequestRevision(revision))
            .tag(CollectorRequestScope::class.java, CollectorRequestScope(categories))
        val cache = cached?.takeIf { it.sourceId == source.id && it.sourceVersion == source.version }
        cache?.etag?.takeIf(::safeValidator)?.let { request.header("If-None-Match", it) }
        cache?.lastModified?.takeIf(::safeValidator)?.let { request.header("If-Modified-Since", it) }
        val call = transferClient.newCall(request.build())
        val job = currentCoroutineContext().job
        val lease = policy.acquire(RemoteDispatchRequest(PUBLIC_COLLECTOR_OWNER, RemoteOperation.CONNECTED_SERVICE, categories),
            revision, recordActivity = false, cancel = { call.cancel(); secondary.get()?.cancel(); job.cancel(CancellationException("Collector mode authority changed")) })
        val cancellation = launch(start = CoroutineStart.UNDISPATCHED) { try { awaitCancellation() } finally { call.cancel(); secondary.get()?.cancel() } }
        try {
            if (publicPage) {
                val robots = readRobots(transferClient, request.build(), url, secondary, lease)
                if (robots.crawlDelaySeconds > 0) return@coroutineScope CollectorFetchResult.Unavailable(
                    "This site requests crawl spacing this bounded page retriever does not support; the page was not requested", Duration.ofSeconds(robots.crawlDelaySeconds.coerceAtMost(86400)))
                val path = url.encodedPath + (url.encodedQuery?.let { "?$it" } ?: "")
                if (!robots.allows(path)) return@coroutineScope CollectorFetchResult.Unavailable("This site's robots policy does not allow Halo to retain this page")
            }
            val response = withContext(Dispatchers.IO) {
                com.halo.app.everyday.EverydayEffectAuthority.requireCurrent()
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
                    if (publicPage) {
                        val type = body.contentType()?.let { "${it.type}/${it.subtype}" }
                        require(type == null || type in setOf("text/html", "text/plain", "application/xhtml+xml")) { "Page is not supported readable text" }
                        val restrictions = reply.headers.values("X-Robots-Tag").joinToString(",").lowercase(java.util.Locale.ROOT)
                        require(listOf("noindex", "noarchive", "none").none { it in restrictions }) { "This site disallows retaining this page" }
                    }
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
                        reply.header("Last-Modified")?.takeIf(::safeValidator), reply.request.url.toString(), null,
                        charset = body.contentType()?.charset(null)?.name())
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
                    CollectorKind.RESEARCH -> CollectorPayload.Articles(requireNotNull(source.research).let { query ->
                        if (query.providerId == PublicPageResearchProvider.id) PublicPageResearchProvider.parsePage(response.bytes, query.query, source.language, response.charset)
                        else ResearchProviderRegistry.public.require(query.providerId).parse(response.bytes, query.query, source.language) })
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
            lease.requireCurrent(); ensureActive(); com.halo.app.everyday.EverydayEffectAuthority.requireCurrent()
            CollectorFetchResult.Updated(payload, MessageDigest.getInstance("SHA-256").digest(response.bytes).joinToString("") { "%02x".format(it) },
                response.etag, response.lastModified, response.url)
        } catch (cancelled: CancellationException) { throw cancelled }
          catch (blocked: RemoteDispatchBlocked) { throw blocked }
          catch (_: IOException) { CollectorFetchResult.Unavailable("Source could not be reached. The previous snapshot is retained", offline = true) }
          catch (_: IllegalArgumentException) { CollectorFetchResult.Unavailable("Source data is invalid, unsupported, or exceeds the safety limits. The previous snapshot is retained") }
          catch (_: Exception) { CollectorFetchResult.Unavailable("Source data could not be parsed. Check the feed address or try again later; the previous snapshot is retained") }
          finally { cancellation.cancel(); lease.close() }
    }
    private suspend fun readRobots(transfer: OkHttpClient, page: Request, url: HttpUrl,
        secondary: java.util.concurrent.atomic.AtomicReference<Call?>, lease: RemoteDispatchLease): ResearchRobotsPolicy = withContext(Dispatchers.IO) {
        val robotsUrl = url.newBuilder().encodedPath("/robots.txt").query(null).fragment(null).build()
        val request = page.newBuilder().url(robotsUrl).removeHeader("If-None-Match").removeHeader("If-Modified-Since").header("Accept", "text/plain").build()
        val call = transfer.newCall(request); secondary.set(call)
        try {
            lease.requireCurrent(); ensureActive(); com.halo.app.everyday.EverydayEffectAuthority.requireCurrent()
            call.execute().use { reply ->
                if (reply.code in setOf(404, 410)) return@withContext ResearchRobotsPolicy.allowMissing()
                require(reply.isSuccessful) { "Robots policy is unavailable; the page was not requested" }
                val body = requireNotNull(reply.body)
                require(body.contentLength() <= 65536)
                val type = body.contentType()?.let { "${it.type}/${it.subtype}" }
                require(type == null || type == "text/plain") { "Robots policy is not plain text" }
                val output = ByteArrayOutputStream(); val buffer = ByteArray(4096)
                body.byteStream().use { stream -> while (true) {
                    ensureActive(); lease.requireCurrent(); val count = stream.read(buffer); if (count < 0) break
                    require(output.size() + count <= 65536); output.write(buffer, 0, count)
                } }
                lease.requireCurrent()
                val utf8 = Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                ResearchRobotsPolicy.parse(utf8.decode(java.nio.ByteBuffer.wrap(output.toByteArray())).toString())
            }
        } finally { secondary.set(null) }
    }
    private fun safeValidator(value: String) = value.isNotBlank() && value.length <= 1024 && value.none { it.isISOControl() }
    private fun retryAfter(value: String?): Duration? = value?.let {
        it.toLongOrNull()?.let { seconds -> Duration.ofSeconds(seconds.coerceIn(0, 86400)) }
            ?: runCatching { Duration.between(clock.instant(), ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant())
                .coerceIn(Duration.ZERO, Duration.ofDays(1)) }.getOrNull()
    }
    private data class RawReply(val code: Int, val bytes: ByteArray?, val etag: String?, val lastModified: String?, val url: String,
        val retryAfter: Duration?, val conditional: Boolean = false, val charset: String? = null)
}
