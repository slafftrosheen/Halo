package com.halo.app.everyday.knowledge

import androidx.room.withTransaction
import com.halo.app.data.AppDatabase
import com.halo.app.data.everyday.CollectorEndpointEntity
import com.halo.app.data.everyday.CollectorRequestEntity
import com.halo.app.everyday.collectors.*
import com.halo.sdk.provider.*
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.Proxy
import java.time.Clock
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import okhttp3.*

interface ReferenceTransfer {
    suspend fun fetch(url: HttpUrl, maxBytes: Long, sink: OutputStream, checkIndexing: Boolean = false, imageOnly: Boolean = false): String?
}

/** Shared rolling quotas, global policy lease, no credentials/cookies/proxy or unrestricted redirects. */
class GovernedReferenceTransfer(private val database: AppDatabase, private val policy: RemoteDispatchPolicy,
    private val authority: () -> (() -> Unit), baseClient: OkHttpClient = OkHttpClient.Builder().callTimeout(60, TimeUnit.SECONDS).build(),
    private val clock: Clock = Clock.systemUTC(), private val publicImages: Boolean = false) : ReferenceTransfer {
    private val client = baseClient.newBuilder().dns(CollectorUrlPolicy.dns(baseClient.dns)).proxy(Proxy.NO_PROXY)
        .cookieJar(CookieJar.NO_COOKIES).authenticator(Authenticator.NONE).proxyAuthenticator(Authenticator.NONE).cache(null).build()
        .withRemoteDispatchPolicy(policy, publicGetRedirects = false) {
            RemoteDispatchRequest(PUBLIC_COLLECTOR_OWNER, RemoteOperation.CONNECTED_SERVICE, setOf(if (publicImages) RemoteDataCategory.WORKSPACE else RemoteDataCategory.ACCOUNT_METADATA))
        }
    override suspend fun fetch(url: HttpUrl, maxBytes: Long, sink: OutputStream, checkIndexing: Boolean, imageOnly: Boolean): String? = withContext(Dispatchers.IO) {
        require(maxBytes in 1..ReferenceLimits.INPUT_BYTES)
        val guard = authority()
        val check: () -> Unit = { guard(); com.halo.app.everyday.EverydayWriteAuthority.requireCurrent() }
        check(); val validated = CollectorUrlPolicy.validate(url.toString()); require(validated.isHttps)
        require(publicImages && (imageOnly || validated.encodedPath == "/robots.txt") || validated.host in setOf("medlineplus.gov", "www.cdc.gov", "en.wikipedia.org", "ru.wikipedia.org", "lv.wikipedia.org", "pl.wikipedia.org"))
        val revision = RemoteDispatchContext.provenance.get()?.revision ?: RemoteDispatchContext.intent.get()?.revision ?: policy.revision
        val call = client.newCall(Request.Builder().url(validated).header("User-Agent", "Halo/1.0 (https://github.com/slafftrosheen/Halo; offline reference import)")
            .tag(RemoteRequestRevision::class.java, RemoteRequestRevision(revision)).build())
        val job = currentCoroutineContext().job
        val lease = policy.acquire(RemoteDispatchRequest(PUBLIC_COLLECTOR_OWNER, RemoteOperation.CONNECTED_SERVICE, setOf(if (publicImages) RemoteDataCategory.WORKSPACE else RemoteDataCategory.ACCOUNT_METADATA)),
            revision, recordActivity = false, cancel = { call.cancel(); job.cancel(CancellationException("Reference transfer policy changed")) })
        try {
            lease.requireCurrent(); check()
            val now = clock.millis(); val key = "reference:${validated}"; val bucket = "reference:${validated.host}"
            database.withTransaction {
                val dao = database.collectorDao(); val since = now - 86400000L
                require(dao.requestCount(since) < CollectorRefreshPolicy.MAX_EXTERNAL_REQUESTS_PER_DAY && dao.sourceRequestCount(bucket, since) < CollectorRefreshPolicy.MAX_SOURCE_REQUESTS_PER_DAY) { "Reference download request quota reached; try later" }
                require(dao.endpoint(key)?.blockedUntil?.let { it <= now } != false) { "This reference endpoint is waiting before retrying" }
                check(); lease.requireCurrent(); dao.request(CollectorRequestEntity(UUID.randomUUID().toString(), bucket, now))
            }
            val cancellation = CoroutineScope(currentCoroutineContext()).launch(start = CoroutineStart.UNDISPATCHED) { try { awaitCancellation() } finally { call.cancel() } }
            try {
                call.execute().use { response ->
                    if (validated.encodedPath == "/robots.txt" && response.code in setOf(404, 410)) {
                        check(); lease.requireCurrent(); return@withContext null
                    }
                    if (validated.encodedPath == "/robots.txt" && response.isSuccessful) require(response.body?.contentType()?.let { "${it.type}/${it.subtype}" } in setOf(null, "text/plain"))
                    if (!response.isSuccessful) {
                        val retry = response.header("Retry-After")?.toLongOrNull()?.coerceIn(0, 86400) ?: 300L
                        database.collectorDao().putEndpoint(CollectorEndpointEntity(key, now, now + retry * 1000, 1))
                        error("The reference source returned HTTP ${response.code}; saved libraries are unchanged")
                    }
                    val restrictions = response.headers.values("X-Robots-Tag").joinToString(",").lowercase(java.util.Locale.ROOT)
                    require(!checkIndexing || listOf("noindex", "noarchive", "none").none { it in restrictions }) { "This source disallows local retention" }
                    if (imageOnly) require(response.body?.contentType()?.let { "${it.type}/${it.subtype}" } in setOf("image/jpeg", "image/png", "image/webp")) { "Only static JPEG, PNG and WebP images can be saved" }
                    val body = requireNotNull(response.body); require(body.contentLength() <= maxBytes) { "Reference download exceeds its size limit" }
                    var count = 0L; val buffer = ByteArray(32 * 1024)
                    body.byteStream().use { stream -> while (true) {
                        check(); ensureActive(); lease.requireCurrent()
                        val read = stream.read(buffer); if (read < 0) break; count += read
                        require(count <= maxBytes) { "Reference download exceeds its size limit" }; sink.write(buffer, 0, read)
                    } }
                    check(); lease.requireCurrent(); sink.flush()
                    database.collectorDao().putEndpoint(CollectorEndpointEntity(key, now, null, 0))
                    response.header("Last-Modified")?.take(40)
                }
            } finally { cancellation.cancel() }
        } finally { call.cancel(); lease.close() }
    }
}

internal suspend fun ReferenceTransfer.bytes(url: HttpUrl, maxBytes: Long, checkIndexing: Boolean = false): ByteArray = ByteArrayOutputStream().also { fetch(url, maxBytes, it, checkIndexing) }.toByteArray()
