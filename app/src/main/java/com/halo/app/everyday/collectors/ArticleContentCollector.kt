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
    baseClient: OkHttpClient = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()) {
    private val requestScope = RemoteDispatchRequest(PUBLIC_COLLECTOR_OWNER, RemoteOperation.CONNECTED_SERVICE,
        setOf(RemoteDataCategory.ACCOUNT_METADATA))
    private val client = baseClient.withRemoteDispatchPolicy(policy, publicGetRedirects = true) { requestScope }
    suspend fun fetch(url: String): ArticleDownload = coroutineScope {
        val revision = RemoteDispatchContext.provenance.get()?.revision ?: RemoteDispatchContext.intent.get()?.revision ?: policy.revision
        val request = Request.Builder().url(CollectorUrlPolicy.validate(url)).get()
            .header("User-Agent", "Halo/Everyday").header("Accept", "text/html,application/xhtml+xml,image/jpeg,image/png,image/webp")
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
                    require(body.contentLength() <= CollectorRefreshPolicy.MAX_BODY_BYTES)
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    body.byteStream().use { input ->
                        while (true) {
                            ensureActive(); lease.requireCurrent()
                            val n = input.read(buffer); if (n < 0) break
                            require(output.size() + n <= CollectorRefreshPolicy.MAX_BODY_BYTES)
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
    private val download: suspend (String) -> ArticleDownload, private val clock: Clock = Clock.systemUTC()) {
    suspend fun enrich(reservation: CollectorReservation, payload: CollectorPayload): CollectorPayload = withContext(Dispatchers.IO) {
        if (payload !is CollectorPayload.Articles || reservation.source.kind != CollectorKind.FEED) return@withContext payload
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(120)
        fun hasTime() = System.nanoTime() < deadline
        suspend fun boundedDownload(url: String): ArticleDownload =
            withTimeoutOrNull(TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()).coerceAtLeast(1)) { download(url) }
                ?: throw java.io.IOException("Refresh content window elapsed")
        store.prune(repository.states().flatMap { (it.snapshot?.payload as? CollectorPayload.Articles)?.items.orEmpty() }
            .flatMap { listOfNotNull(it.contentKey) + it.images.mapNotNull { image -> image.assetKey } }.toSet())
        val previous = (reservation.cached?.payload as? CollectorPayload.Articles)?.items.orEmpty().associateBy { it.id }
        val bodies = linkedMapOf<String, ArticleContent>()
        val articles = payload.items.map { article ->
            currentCoroutineContext().ensureActive()
            val old = previous[article.id]?.takeIf { it.url == article.url && it.text == article.text && it.title == article.title }
            val saved = store.read(old?.contentKey ?: article.contentKey)
            if (saved != null && saved.status == "PAGE_CAPTURED" &&
                runCatching { java.time.Instant.parse(saved.retrievedAt).plusSeconds(6 * 3600) > clock.instant() }.getOrDefault(false)) {
                bodies[article.id] = saved
                article.copy(contentKey = old?.contentKey ?: article.contentKey, contentStatus = saved.status, images = saved.images)
            } else if (article.url == null) article
            else if (!hasTime() || !repository.reserveContent(reservation, clock.instant())) article.copy(contentStatus = "REFRESH_LIMIT")
            else try {
                val response = boundedDownload(article.url)
                require(response.mimeType in setOf("text/html", "application/xhtml+xml"))
                val body = ArticleExtractor.extract(response.bytes.toString(response.charset), response.url, clock.instant().toString())
                val combinedImages = (body.images + article.images).distinctBy { it.url }
                // Preserve a richer feed if a consent/paywall shell contains less usable text.
                val content = body.copy(text = if (body.text.length < article.text.length) article.text else body.text,
                    status = if (body.text.length < article.text.length && body.status == "PAGE_CAPTURED") "FEED_WITH_PAGE_SHELL" else body.status,
                    images = combinedImages.take(ArticleExtractor.MAX_IMAGES),
                    omittedImages = body.omittedImages + (combinedImages.size - ArticleExtractor.MAX_IMAGES).coerceAtLeast(0))
                bodies[article.id] = content
                article.copy(contentKey = store.save(content), contentStatus = content.status, images = content.images)
            } catch (cancelled: CancellationException) { throw cancelled }
              catch (blocked: RemoteDispatchBlocked) { throw blocked }
              catch (_: Exception) { article.copy(contentStatus = "PAGE_UNAVAILABLE") }
        }
        // Text first: large image galleries must not consume the budget before other articles are read.
        CollectorPayload.Articles(articles.map { article ->
            currentCoroutineContext().ensureActive()
            val originalRetrievedAt = if (payload == reservation.cached?.payload) reservation.cached?.retrievedAt else null
            val body = bodies[article.id] ?: ArticleContent(article.text, article.images, article.contentStatus,
                article.url ?: reservation.source.endpoint.orEmpty(), (originalRetrievedAt ?: clock.instant()).toString())
            val images = body.images.map { image ->
                if (store.imageFile(image.assetKey) != null) image
                else if (!hasTime() || !repository.reserveContent(reservation, clock.instant())) image.copy(assetKey = null, mimeType = null)
                else try {
                    val response = boundedDownload(image.url)
                    require(response.mimeType in setOf("image/jpeg", "image/png", "image/webp"))
                    image.copy(assetKey = store.saveImage(response.bytes), mimeType = response.mimeType)
                } catch (cancelled: CancellationException) { throw cancelled }
                  catch (blocked: RemoteDispatchBlocked) { throw blocked }
                  catch (_: Exception) { image.copy(assetKey = null, mimeType = null) }
            }
            try {
                val content = body.copy(images = images)
                article.copy(contentKey = store.save(content), images = images)
            } catch (_: IllegalArgumentException) { article }
        })
    }
}
