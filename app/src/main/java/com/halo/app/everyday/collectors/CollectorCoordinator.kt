package com.halo.app.everyday.collectors

import com.halo.sdk.provider.*
import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class CollectorRefreshReceipt(val state: CollectorState?, val admitted: Boolean)

/** One service owns manual refresh, workers, source cancellation and deterministic snapshot publication. */
class CollectorCoordinator(
    private val repository: CollectorRepository,
    private val registry: CollectorRegistry,
    private val policy: RemoteDispatchPolicy,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val jobs = ConcurrentHashMap<String, Job>()
    fun cancel(sourceId: String) { jobs[sourceId]?.cancel(CancellationException("Source refresh cancelled")) }

    suspend fun refresh(sourceId: String, manual: Boolean = false): CollectorState? = refreshDetailed(sourceId, manual).state
    suspend fun refreshDetailed(sourceId: String, manual: Boolean = false, expectedVersion: Long? = null): CollectorRefreshReceipt {
        val revision = RemoteDispatchContext.provenance.get()?.revision ?: RemoteDispatchContext.intent.get()?.revision ?: policy.revision
        return locks.getOrPut(sourceId) { Mutex() }.withLock {
            val current = repository.state(sourceId) ?: return@withLock CollectorRefreshReceipt(null, false)
            if(expectedVersion!=null && current.source.version!=expectedVersion) throw CollectorConflict("Source changed before refresh admission")
            if (!current.source.enabled || !current.source.selected) return@withLock CollectorRefreshReceipt(current, false)
            var reservation: CollectorReservation? = null
            var deferred: CollectorAttempt? = null
            val failedByPolicy = AtomicBoolean(false)
            supervisorScope {
                val transfer = async(start = CoroutineStart.LAZY) {
                    val categories = when (current.source.kind) {
                        CollectorKind.WEATHER -> setOf(RemoteDataCategory.LOCATION)
                        CollectorKind.RESEARCH -> setOf(RemoteDataCategory.TEXT, RemoteDataCategory.WORKSPACE)
                        else -> setOf(RemoteDataCategory.ACCOUNT_METADATA)
                    }
                    val job = currentCoroutineContext().job
                    val authority = if (current.source.external) policy.acquire(RemoteDispatchRequest(PUBLIC_COLLECTOR_OWNER,
                        RemoteOperation.CONNECTED_SERVICE, categories), revision, recordActivity = false, cancel = {
                        failedByPolicy.set(true); job.cancel(CancellationException("Global mode changed"))
                    }) else null
                    try {
                        when (val admitted = repository.reserve(sourceId, current.source.version, clock.instant(), manual)) {
                            is CollectorAdmission.Deferred -> { deferred = admitted.attempt; return@async }
                            is CollectorAdmission.Accepted -> reservation = admitted.reservation
                        }
                        val active = requireNotNull(reservation)
                        val sourceWatcher = launch(start = CoroutineStart.UNDISPATCHED) {
                            repository.observe().first { values -> values.find { it.source.id == sourceId }?.source?.let {
                                it.version != active.source.version || !it.selected || !it.enabled } ?: true }
                            job.cancel(CancellationException("Source selection changed"))
                        }
                        try {
                            val context = RemoteDispatchContext.intent.asContextElement(RemoteDispatchIntent(
                                RemoteDispatchRequest(PUBLIC_COLLECTOR_OWNER, RemoteOperation.CONNECTED_SERVICE, categories), revision))
                            val result = withContext(context) { registry.require(active.source.kind).fetch(active.source, active.cached) }
                            authority?.requireCurrent(); ensureActive()
                            val now = clock.instant()
                            when (result) {
                                is CollectorFetchResult.Updated -> repository.finish(active,
                                    CollectorSnapshot(sourceId, active.source.version, result.payload, result.contentRevision, now, now,
                                        now.plusSeconds(active.source.refreshMinutes * 60L), result.etag, result.lastModified, result.effectiveUrl),
                                    CollectorAttempt(sourceId, now, CollectorAttemptState.CURRENT, nextAllowedAt = now.plusSeconds(active.source.refreshMinutes * 60L)))
                                CollectorFetchResult.NotModified -> {
                                    val cached = active.cached ?: error("Conditional response has no cached body")
                                    repository.finish(active, cached.copy(verifiedAt = now, expiresAt = now.plusSeconds(active.source.refreshMinutes * 60L)),
                                        CollectorAttempt(sourceId, now, CollectorAttemptState.NOT_MODIFIED, nextAllowedAt = now.plusSeconds(active.source.refreshMinutes * 60L)))
                                }
                                is CollectorFetchResult.Unavailable -> {
                                    val failures = (active.consecutiveFailures + 1).coerceAtMost(100)
                                    // A 304 from a different final destination must not renew freshness.
                                    // Forget only stale validators, not the last good snapshot.
                                    val retained = if (result.resetValidators)
                                        active.cached?.copy(etag = null, lastModified = null)
                                        else active.cached
                                    repository.finish(active, retained, CollectorAttempt(sourceId, now,
                                        when { result.retryAfter != null -> CollectorAttemptState.BACKOFF
                                            result.offline -> CollectorAttemptState.OFFLINE
                                            else -> CollectorAttemptState.FAILED }, failures,
                                        CollectorRefreshPolicy.nextAttempt(active.source, now, failures, result.retryAfter), result.reason.take(300)))
                                }
                            }
                        } finally { sourceWatcher.cancel() }
                    } finally { authority?.close() }
                }
                jobs[sourceId] = transfer
                try { transfer.start(); transfer.await() }
                catch (blocked: RemoteDispatchBlocked) {
                    val attempt = current.attempt.copy(at = clock.instant(), state = CollectorAttemptState.LOCAL_ONLY,
                        consecutiveFailures = reservation?.consecutiveFailures ?: current.attempt.consecutiveFailures,
                        message = "Refresh needs Power and permitted connected services. The stored snapshot remains available")
                    reservation?.let { repository.finish(it, it.cached, attempt) } ?: repository.recordAttempt(sourceId, current.source.version, attempt)
                } catch (cancelled: CancellationException) {
                    val attempt = CollectorAttempt(sourceId, clock.instant(), CollectorAttemptState.CANCELLED,
                        consecutiveFailures = reservation?.consecutiveFailures ?: current.attempt.consecutiveFailures,
                        message = if (failedByPolicy.get()) "Refresh paused after a mode change. Start a new refresh when ready" else "Refresh cancelled; the stored snapshot is unchanged")
                    withContext(NonCancellable) { reservation?.let { repository.finish(it, it.cached, attempt) } }
                    currentCoroutineContext().ensureActive()
                } catch (_: Exception) {
                    val failures = ((reservation?.consecutiveFailures ?: current.attempt.consecutiveFailures) + 1).coerceAtMost(100)
                    val attempt = CollectorAttempt(sourceId, clock.instant(), CollectorAttemptState.FAILED, failures,
                        CollectorRefreshPolicy.nextAttempt(current.source, clock.instant(), failures), "Source data could not be read; the stored snapshot is unchanged")
                    reservation?.let { repository.finish(it, it.cached, attempt) } ?: repository.recordAttempt(sourceId, current.source.version, attempt)
                } finally { jobs.remove(sourceId, transfer) }
            }
            CollectorRefreshReceipt(repository.state(sourceId)?.let { state -> deferred?.let { state.copy(attempt = it) } ?: state }, reservation != null)
        }
    }

    /** Internal snapshots are useful in Local; external adapters still recheck global admission per request. */
    suspend fun refreshDue(maximum: Int = 10, external: Boolean? = null) {
        require(maximum in 1..100)
        val now = clock.instant()
        val due = repository.states().filter { it.source.selected && it.source.enabled && (external == null || it.source.external == external) &&
            (it.attempt.nextAllowedAt == null || it.attempt.nextAllowedAt <= now) }
            .sortedWith(compareBy<CollectorState> { it.source.external }.thenBy { it.attempt.at ?: Instant.MIN }.thenBy { it.source.id })
            .take(CollectorRefreshPolicy.MAX_SOURCE_COUNT)
        var admitted = 0
        for (state in due) {
            if (admitted >= maximum) break
            if (refreshDetailed(state.source.id).admitted) admitted++
        }
    }
}
