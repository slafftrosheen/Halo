package com.halo.app.everyday.collectors

import com.halo.sdk.provider.*
import java.security.MessageDigest
import java.time.Clock
import kotlinx.coroutines.*

data class SourcePreview(val candidate: CollectorSource, val result: CollectorFetchResult)

/** An explicit preview uses the same transport, parser, policy and durable request budget as polling. */
class CollectorPreview(private val repository: CollectorRepository, private val registry: CollectorRegistry,
    private val policy: RemoteDispatchPolicy, private val clock: Clock = Clock.systemUTC()) {
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
            val result = withContext(RemoteDispatchContext.intent.asContextElement(RemoteDispatchIntent(request, revision))) {
                registry.require(kind).fetch(candidate, null)
            }
            lease.requireCurrent(); ensureActive()
            repository.finishPreview(candidate.id, startedAt, clock.instant(), result)
            SourcePreview(candidate, result)
        } finally { lease.close() }
    }
    /** The preview is advisory; saving does not silently opt in or publish its uncommitted snapshot. */
    suspend fun add(preview: SourcePreview, title: String, selected: Boolean): CollectorSource {
        require(preview.result is CollectorFetchResult.Updated) { "Validate this feed before adding it" }
        return repository.save(preview.candidate.copy(id = "custom:${java.util.UUID.randomUUID()}", title = title.trim(), selected = selected), null)
    }
}
