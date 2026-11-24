package com.halo.app.everyday.collectors

import com.halo.sdk.provider.*
import java.security.MessageDigest
import java.time.Clock
import kotlinx.coroutines.*

data class SourcePreview(val candidate: CollectorSource, val result: CollectorFetchResult,
    val requestedUrl: String = requireNotNull(candidate.endpoint), val discoveredFeed: Boolean = false,
    val websiteWithoutFeed: Boolean = false) {
    fun matches(url: String, kind: CollectorKind, language: String): Boolean =
        candidate.kind == kind && candidate.language == language &&
            runCatching { CollectorUrlPolicy.validate(url).toString() == requestedUrl }.getOrDefault(false)
}

/** An explicit preview uses the same transport, parser, policy and durable request budget as polling. */
class CollectorPreview(private val repository: CollectorRepository, private val registry: CollectorRegistry,
    private val policy: RemoteDispatchPolicy, private val clock: Clock = Clock.systemUTC(),
    private val downloadFeedPreview: (suspend (String) -> ArticleDownload)? = null) {
    suspend fun inspect(url: String, kind: CollectorKind, language: String): SourcePreview = coroutineScope {
        require(kind in setOf(CollectorKind.FEED, CollectorKind.ALERTS, CollectorKind.PUBLIC_CALENDAR))
        val endpoint = CollectorUrlPolicy.validate(url)
        val fingerprint = CollectorEndpointIdentity.fingerprint(endpoint.toString())
        val candidate = CollectorSource("preview:$fingerprint", endpoint.host, endpoint.host, kind,
            if (kind == CollectorKind.PUBLIC_CALENDAR) SourceCategory.CALENDAR else SourceCategory.CUSTOM, language,
            endpoint.toString(), endpoint.newBuilder().query(null).fragment(null).encodedPath("/").build().toString(), selected = true)
        val request = RemoteDispatchRequest(PUBLIC_COLLECTOR_OWNER, RemoteOperation.CONNECTED_SERVICE, setOf(RemoteDataCategory.ACCOUNT_METADATA))
        val revision = RemoteDispatchContext.provenance.get()?.revision ?: RemoteDispatchContext.intent.get()?.revision ?: policy.revision
        val job = currentCoroutineContext().job
        val lease = policy.acquire(request, revision, recordActivity = false, cancel = { job.cancel(CancellationException("Preview mode changed")) })
        try {
            val startedAt = clock.instant()
            if (!repository.reservePreview(candidate.id, startedAt)) return@coroutineScope SourcePreview(candidate,
                CollectorFetchResult.Unavailable("Preview refresh budget reached. Wait before trying again"))
            val preview = withContext(RemoteDispatchContext.intent.asContextElement(RemoteDispatchIntent(request, revision))) {
                val download = downloadFeedPreview
                if (kind == CollectorKind.FEED && endpoint.isHttps && download != null) {
                    withTimeoutOrNull(30_000) { inspectFeed(candidate, download) } ?: SourcePreview(candidate,
                        CollectorFetchResult.Unavailable("Preview timed out. Try the feed address directly or try again later"))
                } else SourcePreview(candidate, registry.require(kind).fetch(candidate, null))
            }
            lease.requireCurrent(); ensureActive()
            repository.finishPreview(candidate.id, startedAt, clock.instant(),
                if (preview.websiteWithoutFeed || preview.discoveredFeed) CollectorFetchResult.NotModified else preview.result)
            preview
        } finally { lease.close() }
    }

    private suspend fun inspectFeed(candidate: CollectorSource, download: suspend (String) -> ArticleDownload): SourcePreview {
        val requested = requireNotNull(candidate.endpoint)
        val initial = downloadSafely(requested, download)
            ?: return SourcePreview(candidate, CollectorFetchResult.Unavailable("The address could not be reached or exceeded the preview limit"))
        parseFeed(candidate, initial)?.let { return SourcePreview(candidate, it) }
        if (initial.mimeType !in setOf("text/html", "application/xhtml+xml"))
            return SourcePreview(candidate, CollectorFetchResult.Unavailable("This address did not return a supported feed"))
        val base = requireNotNull(publicHttps(initial.url))
        val alternates = org.jsoup.Jsoup.parse(initial.bytes.toString(initial.charset)).head().select("link[href][type]")
            .asSequence().filter { link ->
                link.attr("rel").split(Regex("\\s+")).any { it.equals("alternate", ignoreCase = true) } &&
                    link.attr("type").substringBefore(';').trim().lowercase(java.util.Locale.ROOT) in
                    setOf("application/rss+xml", "application/atom+xml")
            }.mapNotNull { link ->
                base.resolve(link.attr("href"))?.toString()?.let(::publicHttps)
                    ?.newBuilder()?.fragment(null)?.build()
            }.distinct().filter { it.toString() !in setOf(initial.url, requested) }.take(3).toList()
        if (alternates.isEmpty()) return SourcePreview(candidate,
            CollectorFetchResult.Unavailable("This website does not advertise an RSS or Atom feed"), websiteWithoutFeed = true)
        for (url in alternates) {
            val feed = candidate.copy(id = "preview:${CollectorEndpointIdentity.fingerprint(url.toString())}",
                endpoint = url.toString())
            val startedAt = clock.instant()
            if (!repository.reservePreview(feed.id, startedAt)) continue
            val result = downloadSafely(url.toString(), download)?.let { parseFeed(feed, it) }
                ?: CollectorFetchResult.Unavailable("The advertised feed could not be previewed")
            currentCoroutineContext().ensureActive()
            repository.finishPreview(feed.id, startedAt, clock.instant(), result)
            if (result is CollectorFetchResult.Updated)
                return SourcePreview(feed, result, requestedUrl = requested, discoveredFeed = true)
        }
        return SourcePreview(candidate, CollectorFetchResult.Unavailable("The website advertises feeds, but none could be previewed. Try again later."),
            websiteWithoutFeed = true)
    }

    private suspend fun downloadSafely(url: String, download: suspend (String) -> ArticleDownload): ArticleDownload? = try {
        download(url).takeIf { it.bytes.size <= CollectorRefreshPolicy.MAX_BODY_BYTES && publicHttps(it.url) != null }
    } catch (cancelled: CancellationException) { throw cancelled }
      catch (blocked: RemoteDispatchBlocked) { throw blocked }
      catch (_: java.io.IOException) { null }
      catch (_: IllegalArgumentException) { null }

    private fun publicHttps(url: String): okhttp3.HttpUrl? =
        runCatching { CollectorUrlPolicy.validate(url).takeIf { it.isHttps } }.getOrNull()

    private fun parseFeed(candidate: CollectorSource, response: ArticleDownload): CollectorFetchResult.Updated? = runCatching {
        val payload = CollectorPayload.Articles(FeedParser.parse(response.bytes, candidate.copy(endpoint = response.url)).articles)
        CollectorPayloadLimits.validate(payload)
        val hash = MessageDigest.getInstance("SHA-256").digest(response.bytes).joinToString("") { "%02x".format(it) }
        CollectorFetchResult.Updated(payload, hash, null, null, response.url)
    }.getOrNull()
    /** The preview is advisory; saving does not silently opt in or publish its uncommitted snapshot. */
    suspend fun add(preview: SourcePreview, title: String, selected: Boolean): CollectorSource {
        require(preview.result is CollectorFetchResult.Updated) { "Validate this feed before adding it" }
        return repository.save(preview.candidate.copy(id = "custom:${java.util.UUID.randomUUID()}", title = title.trim(), selected = selected), null)
    }
}
