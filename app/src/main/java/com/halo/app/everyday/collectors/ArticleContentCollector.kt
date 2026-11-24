package com.halo.app.everyday.collectors

import com.halo.sdk.provider.*
import java.io.ByteArrayOutputStream
import java.time.Clock
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request

data class ArticleDownload(val bytes: ByteArray, val mimeType: String, val url: String,
    val charset: java.nio.charset.Charset = Charsets.UTF_8)

/** Article and image GETs use the same mode, redirect, DNS and cancellation boundary as feeds. */
class ArticleDownloader(private val policy: RemoteDispatchPolicy,
    baseClient: OkHttpClient = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build(),
    allowPublicHttp: Boolean = true) {
    private val requestScope = RemoteDispatchRequest(PUBLIC_COLLECTOR_OWNER, RemoteOperation.CONNECTED_SERVICE,
        setOf(RemoteDataCategory.ACCOUNT_METADATA))
    private val client = baseClient.withRemoteDispatchPolicy(policy, publicGetRedirects = true,
        allowPublicCollectorHttp = allowPublicHttp) { requestScope }
    suspend fun fetch(url: String, maxBytes: Int = CollectorRefreshPolicy.MAX_BODY_BYTES,
        accept: String = "text/html,application/xhtml+xml,image/jpeg,image/png,image/webp,image/gif,image/avif"): ArticleDownload = coroutineScope {
        require(maxBytes in 1..CollectorRefreshPolicy.MAX_BODY_BYTES)
        val revision = RemoteDispatchContext.provenance.get()?.revision ?: RemoteDispatchContext.intent.get()?.revision ?: policy.revision
        val request = Request.Builder().url(CollectorUrlPolicy.validate(url)).get()
            .header("User-Agent", "Halo/11.0 (https://github.com/slafftrosheen/Halo)")
            .header("Accept", accept)
            .tag(RemoteRequestRevision::class.java, RemoteRequestRevision(revision)).build()
        val call = client.newCall(request)
        val job = currentCoroutineContext().job
        val lease = policy.acquire(requestScope, revision, recordActivity = false, cancel = {
            call.cancel(); job.cancel(CancellationException("Source authority changed"))
        })
        val cancellation = launch(start = CoroutineStart.UNDISPATCHED) { try { awaitCancellation() } finally { call.cancel() } }
        try {
            withContext(Dispatchers.IO) {
                call.execute().use { response ->
                    require(response.isSuccessful) { "Article response unavailable" }
                    val body = requireNotNull(response.body)
                    require(body.contentLength() <= maxBytes)
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    body.byteStream().use { input ->
                        while (true) {
                            ensureActive(); lease.requireCurrent()
                            val n = input.read(buffer); if (n < 0) break
                            require(output.size() + n <= maxBytes)
                            output.write(buffer, 0, n)
                        }
                    }
                    lease.requireCurrent()
                    ArticleDownload(output.toByteArray(), body.contentType()?.let { "${it.type}/${it.subtype}" }.orEmpty(),
                        response.request.url.toString(), body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8)
                }
            }
        } finally { cancellation.cancel(); lease.close() }
    }
}

/** Enrich the existing snapshot under its reservation; never bypass the source or global request budget. */
class ArticleContentCollector(private val repository: CollectorRepository, private val store: ArticleContentStore,
    private val download: suspend (String) -> ArticleDownload, private val clock: Clock = Clock.systemUTC(),
    private val picturesVisible: () -> Boolean = { true }, private val metered: () -> Boolean = { false },
    private val downloadSmall: (suspend (String) -> ArticleDownload)? = null,
    private val additionallyRetainedImages: (List<CollectorState>) -> Set<String> = { emptySet() }) {
    suspend fun enrich(reservation: CollectorReservation, payload: CollectorPayload): CollectorPayload = withContext(Dispatchers.IO) {
        if (payload !is CollectorPayload.Articles || reservation.source.kind != CollectorKind.FEED) return@withContext payload
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(120)
        fun hasTime() = System.nanoTime() < deadline
        suspend fun boundedDownload(url: String, loader: suspend (String) -> ArticleDownload = download): ArticleDownload =
            withTimeoutOrNull(TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()).coerceAtLeast(1)) { loader(url) }
                ?: throw java.io.IOException("Refresh content window elapsed")
        suspend fun loadPicture(image: ArticleImage): ArticleImage {
            val small = metered()
            val cached = store.imageFile(image.assetKey) != null
            if (cached && (!image.previewOnly || small)) return image
            val requestUrl = if (small) image.previewUrl ?: image.url else image.url
            val loader = if (!small) download else downloadSmall ?: if (image.previewUrl == null) return image else download
            if (!image.fromArticle || !picturesVisible() || !hasTime() ||
                !repository.reserveContent(reservation, clock.instant())) return image
            return try {
                val response = boundedDownload(requestUrl, loader)
                require(response.mimeType in setOf("image/jpeg", "image/png", "image/webp", "image/gif", "image/avif"))
                image.copy(assetKey = store.saveImage(response.bytes), mimeType = response.mimeType,
                    previewOnly = requestUrl != image.url)
            } catch (cancelled: CancellationException) { throw cancelled }
              catch (blocked: RemoteDispatchBlocked) { throw blocked }
              catch (_: Exception) { image }
        }
        val savedSources = repository.states()
        store.prune(savedSources.flatMap { (it.snapshot?.payload as? CollectorPayload.Articles)?.items.orEmpty() }
            .flatMap { listOfNotNull(it.contentKey) + it.images.mapNotNull { image -> image.assetKey } }.toSet() +
            additionallyRetainedImages(savedSources))
        val previous = (reservation.cached?.payload as? CollectorPayload.Articles)?.items.orEmpty().associateBy { it.id }
        val bodies = payload.items.map { article ->
            currentCoroutineContext().ensureActive()
            val sameArticle = previous[article.id]?.takeIf { it.url == article.url }
            val old = sameArticle?.takeIf { it.text == article.text && it.title == article.title }
            val saved = store.read(old?.contentKey ?: article.contentKey)
            val feedImages = article.images.map { image ->
                // Previous parsers marked item-scoped RSS enclosures as unattached.
                if (image.fromArticle) image else image.copy(fromArticle = true)
            }
            val originalRetrievedAt = reservation.cached?.retrievedAt.takeIf { payload == reservation.cached?.payload }
            val fallback = ArticleContent(article.text, feedImages, article.contentStatus,
                article.url ?: reservation.source.endpoint.orEmpty(), (originalRetrievedAt ?: clock.instant()).toString())
            var page = when {
                article.url == null -> fallback
                saved != null && saved.status == "PAGE_CAPTURED" &&
                    runCatching { java.time.Instant.parse(saved.retrievedAt).plusSeconds(6 * 3600) > clock.instant() }.getOrDefault(false) -> saved
                !hasTime() || !repository.reserveContent(reservation, clock.instant()) -> saved ?: fallback.copy(status = "REFRESH_LIMIT")
                else -> try {
                    val response = boundedDownload(article.url)
                    require(response.mimeType in setOf("text/html", "application/xhtml+xml"))
                    val body = ArticleExtractor.extract(response.bytes.toString(response.charset), response.url, clock.instant().toString())
                    body.copy(text = if (body.text.length < article.text.length) article.text else body.text,
                        status = if (body.text.length < article.text.length && body.status == "PAGE_CAPTURED") "FEED_WITH_PAGE_SHELL" else body.status)
                } catch (cancelled: CancellationException) { throw cancelled }
                  catch (blocked: RemoteDispatchBlocked) { throw blocked }
                  catch (_: Exception) { saved ?: fallback.copy(status = "PAGE_UNAVAILABLE") }
            }
            val cached = (sameArticle?.images.orEmpty() + saved?.images.orEmpty())
                .filter { it.fromArticle && it.assetKey != null }.associateBy(ArticleImage::url)
            val candidates = (page.images + feedImages).distinctBy(ArticleImage::url).map { entry ->
                val image = if (!entry.fromArticle && feedImages.any { it.url == entry.url }) entry.copy(fromArticle = true) else entry
                val prior = cached[image.url]
                if (image.assetKey == null && prior?.assetKey != null)
                    image.copy(assetKey = prior.assetKey, mimeType = prior.mimeType, previewOnly = prior.previewOnly)
                else image
            }
            val selected = candidates.take(ArticleExtractor.MAX_IMAGES)
            // Give each article its lead picture before fetching the rest of any gallery.
            page = page.copy(images = selected.firstOrNull()?.let { listOf(loadPicture(it)) + selected.drop(1) }.orEmpty(),
                omittedImages = page.omittedImages + (candidates.size - selected.size))
            article to page
        }
        CollectorPayload.Articles(bodies.map { (article, body) ->
            currentCoroutineContext().ensureActive()
            val images = body.images.mapIndexed { index, image -> if (index == 0) image else loadPicture(image) }
            try {
                val content = body.copy(images = images)
                article.copy(contentKey = store.save(content), contentStatus = content.status, images = images)
            } catch (_: IllegalArgumentException) {
                article.copy(contentKey = null, contentStatus = body.status, images = images)
            }
        })
    }
}
