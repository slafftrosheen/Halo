package com.halo.app.data.everyday

import androidx.room.withTransaction
import com.halo.app.data.AppDatabase
import com.halo.app.everyday.collectors.*
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.map

class RoomCollectorRepository(private val database: AppDatabase, private val sourceChanging: (String) -> Unit = {},
    private val scheduleChanged: () -> Unit = {}) : CollectorRepository {
    private val dao get() = database.collectorDao()
    override fun observe() = dao.observe().map { database.withTransaction { dao.sources().map { state(it) } } }
    override suspend fun states() = database.withTransaction { dao.sources().map { state(it) } }
    override suspend fun state(id: String) = database.withTransaction { dao.source(id)?.let { state(it) } }
    override suspend fun source(id: String) = dao.source(id)?.let { CollectorCodec.source(it.body) }
    private suspend fun state(row: CollectorSourceEntity): CollectorState {
        val source = CollectorCodec.source(row.body)
        var attempt = dao.attempt(row.id)?.let(CollectorCodec::attempt) ?: CollectorAttempt(row.id, null, CollectorAttemptState.NEVER)
        val snapshot = dao.snapshot(row.id)?.let { stored ->
            try { CollectorCodec.snapshot(stored).also {CollectorPayloadLimits.validateKind(source.kind,it.payload)} }
            catch (_: Exception) {
                dao.removeSnapshot(row.id)
                attempt = attempt.copy(state = CollectorAttemptState.FAILED, message = "Stored source data could not be read. Refresh this source to recover")
                dao.putAttempt(CollectorCodec.entity(row.version, attempt))
                null
            }
        }
        return CollectorState(source, snapshot, attempt)
    }

    /** A restored Add form retries its stable draft ID; it never creates a second UUID after commit. */
    suspend fun saveDraft(source: CollectorSource): CollectorSource = database.withTransaction {
        com.halo.app.everyday.EverydayWriteAuthority.requireCurrent()
        val previous = dao.source(source.id)?.let { CollectorCodec.source(it.body) }
        if (previous == null) save(source, null) else {
            if (previous != source.copy(version = previous.version)) throw CollectorConflict("The saved source differs from this draft")
            previous
        }
    }

    override suspend fun save(source: CollectorSource, expectedVersion: Long?): CollectorSource {
        val endpoint = source.endpoint?.let { CollectorUrlPolicy.validate(it).toString() }
        source.attributionUrl?.let(CollectorUrlPolicy::validate)
        if (source.kind == CollectorKind.WEATHER) require(endpoint == OpenMeteoParser.endpoint(requireNotNull(source.place))) { "Weather must use the selected place's forecast endpoint" }
        val result = database.withTransaction {
            val previous = dao.source(source.id)?.let { CollectorCodec.source(it.body) }
            if (previous == null) {
                if (expectedVersion != null) throw CollectorConflict("Source no longer exists")
                require(dao.sources().size < CollectorRefreshPolicy.MAX_SOURCE_COUNT) { "Source limit reached; remove an unused source first" }
            } else {
                if (expectedVersion != previous.version) throw CollectorConflict("Source changed; reopen its settings")
                require(previous.kind == source.kind) { "Add a new source to change its collector type" }
            }
            val next = source.copy(endpoint = endpoint, version = (previous?.version ?: 0) + 1)
            com.halo.app.everyday.EverydayReadAuthority.invalidate(); com.halo.app.everyday.EverydayWriteAuthority.requireCurrent(); sourceChanging(source.id); com.halo.app.everyday.EverydayWriteAuthority.requireCurrent()
            val row = CollectorSourceEntity(next.id, next.version, CollectorCodec.encode(next))
            if (previous == null) dao.insert(row) else check(dao.update(row) == 1)
            val snapshot = dao.snapshot(next.id)
            val sameContent = previous != null && previous.endpoint == next.endpoint && previous.place == next.place &&
                previous.language == next.language && previous.publisher == next.publisher
            if (snapshot != null && sameContent) dao.putSnapshot(snapshot.copy(sourceVersion = next.version)) else dao.removeSnapshot(next.id)
            val priorAttempt = dao.attempt(next.id)?.let(CollectorCodec::attempt)
            val attempt = if (sameContent && priorAttempt != null) priorAttempt.copy(
                state = if (priorAttempt.state == CollectorAttemptState.FETCHING) CollectorAttemptState.CANCELLED else priorAttempt.state)
                else CollectorAttempt(next.id, null, CollectorAttemptState.NEVER)
            dao.putAttempt(CollectorCodec.entity(next.version, attempt))
            next
        }
        runCatching(scheduleChanged)
        return result
    }
    override suspend fun remove(id: String, expectedVersion: Long) {
        database.withTransaction {
            val current = dao.source(id) ?: throw CollectorConflict("Source no longer exists")
            if (current.version != expectedVersion) throw CollectorConflict("Source changed; reopen its settings")
            com.halo.app.everyday.EverydayReadAuthority.invalidate(); com.halo.app.everyday.EverydayWriteAuthority.requireCurrent(); sourceChanging(id); com.halo.app.everyday.EverydayWriteAuthority.requireCurrent()
            check(dao.remove(id, expectedVersion) == 1)
        }
        runCatching(scheduleChanged)
    }
    /** Clearing changes the source version and invalidates the active token before dropping cached content. */
    suspend fun clearSnapshot(id: String, expectedVersion: Long): CollectorSource {
        val next = database.withTransaction {
            val row = dao.source(id) ?: throw CollectorConflict("Source no longer exists")
            if (row.version != expectedVersion) throw CollectorConflict("Source changed; reopen its settings")
            val source = CollectorCodec.source(row.body).copy(version = row.version + 1)
            com.halo.app.everyday.EverydayReadAuthority.invalidate(); com.halo.app.everyday.EverydayWriteAuthority.requireCurrent(); sourceChanging(id); com.halo.app.everyday.EverydayWriteAuthority.requireCurrent()
            check(dao.update(CollectorSourceEntity(id, source.version, CollectorCodec.encode(source))) == 1)
            dao.removeSnapshot(id)
            dao.putAttempt(CollectorCodec.entity(source.version, CollectorAttempt(id, null, CollectorAttemptState.NEVER)))
            source
        }
        return next
    }
    override suspend fun recoverInterrupted(now: Instant) = database.withTransaction {
        dao.interrupted().forEach { attempt -> dao.putAttempt(attempt.copy(token = null, state = CollectorAttemptState.CANCELLED.name,
            nextAllowedAt = now.toEpochMilli(), message = "Refresh was interrupted; the previous snapshot is unchanged")) }
    }
    override suspend fun reserve(id: String, expectedVersion: Long, now: Instant, manual: Boolean): CollectorAdmission = database.withTransaction {
        val current = state(id) ?: throw CollectorConflict("Source no longer exists")
        val source = current.source
        if (source.version != expectedVersion) throw CollectorConflict("Source changed before refresh")
        fun deferred(state: CollectorAttemptState, message: String, next: Instant? = current.attempt.nextAllowedAt) =
            CollectorAdmission.Deferred(current.attempt.copy(state = state, message = message, nextAllowedAt = next))
        if (!source.selected || !source.enabled) return@withTransaction deferred(CollectorAttemptState.DISABLED, "Source is disabled")
        val prior = current.attempt
        if (!manual && prior.nextAllowedAt?.let { it > now } == true)
            return@withTransaction deferred(CollectorAttemptState.BACKOFF, "The next refresh is not due yet")
        val budgetKey = source.endpoint?.let { CollectorEndpointIdentity.budgetKey(source) }
        var failureLineage = prior.consecutiveFailures
        if (source.external) {
            val endpoint = dao.endpoint(requireNotNull(budgetKey))
            failureLineage = endpoint?.failures ?: 0
            val blockedUntil = endpoint?.blockedUntil
            val attemptedAt = endpoint?.attemptedAt
            if (blockedUntil != null && now.toEpochMilli() < blockedUntil)
                return@withTransaction deferred(CollectorAttemptState.BACKOFF, "This endpoint is waiting before retrying", Instant.ofEpochMilli(blockedUntil))
            if (attemptedAt != null && now.toEpochMilli() < attemptedAt + 60_000)
                return@withTransaction deferred(CollectorAttemptState.BACKOFF, "Wait one minute between requests to this endpoint", Instant.ofEpochMilli(attemptedAt + 60_000))
            val limit = quotaLimit(budgetKey, now)
            if (limit != null) {
                val attempt = CollectorAttempt(id, prior.at, CollectorAttemptState.QUOTA, prior.consecutiveFailures, limit,
                    "Refresh budget reached; stored information remains available")
                dao.putAttempt(CollectorCodec.entity(source.version, attempt))
                return@withTransaction CollectorAdmission.Deferred(attempt)
            }
        }
        val token = UUID.randomUUID().toString()
        if (source.external) {
            dao.request(CollectorRequestEntity(token, requireNotNull(budgetKey), now.toEpochMilli()))
            dao.putEndpoint(CollectorEndpointEntity(budgetKey, now.toEpochMilli(), null, dao.endpoint(budgetKey)?.failures ?: 0))
        }
        dao.putAttempt(CollectorCodec.entity(source.version, CollectorAttempt(id, now, CollectorAttemptState.FETCHING,
            failureLineage), token))
        CollectorAdmission.Accepted(CollectorReservation(token, source, current.snapshot, now, failureLineage))
    }
    override suspend fun reservePreview(sourceId: String, now: Instant): Boolean = database.withTransaction {
        require(sourceId.startsWith("preview:") && sourceId.length <= 160)
        val key = "endpoint:${sourceId.removePrefix("preview:")}"
        val prior = dao.endpoint(key)
        if (prior?.let { now.toEpochMilli() < it.attemptedAt + 60_000 || it.blockedUntil?.let { until -> now.toEpochMilli() < until } == true } == true) return@withTransaction false
        if (quotaLimit(key, now) != null) return@withTransaction false
        dao.request(CollectorRequestEntity(UUID.randomUUID().toString(), key, now.toEpochMilli()))
        dao.putEndpoint(CollectorEndpointEntity(key, now.toEpochMilli(), null, prior?.failures ?: 0))
        true
    }
    override suspend fun finishPreview(sourceId: String, startedAt: Instant, now: Instant, result: CollectorFetchResult) = database.withTransaction {
        val key = "endpoint:${sourceId.removePrefix("preview:")}"
        val prior = dao.endpoint(key) ?: return@withTransaction
        if (prior.attemptedAt != startedAt.toEpochMilli()) return@withTransaction
        val failed = result as? CollectorFetchResult.Unavailable
        val failures = if (failed == null) 0 else (prior.failures + 1).coerceAtMost(100)
        val blocked = failed?.let { now.plusSeconds(maxOf(60L * (1L shl failures.coerceAtMost(8)),
            it.retryAfter?.seconds?.coerceIn(0, 86400) ?: 0).coerceAtMost(86400)).toEpochMilli() }
        dao.putEndpoint(prior.copy(blockedUntil = blocked, failures = failures))
    }
    private suspend fun quotaLimit(id: String, now: Instant): Instant? {
        val since = now.minusSeconds(86400).toEpochMilli()
        dao.pruneRequests(since)
        dao.pruneEndpoints(now.minusSeconds(7 * 86400).toEpochMilli())
        val global = if (dao.requestCount(since) >= CollectorRefreshPolicy.MAX_EXTERNAL_REQUESTS_PER_DAY) dao.oldestRequest(since) else null
        val source = if (dao.sourceRequestCount(id, since) >= CollectorRefreshPolicy.MAX_SOURCE_REQUESTS_PER_DAY) dao.oldestSourceRequest(id, since) else null
        return listOfNotNull(global, source).maxOrNull()?.let { Instant.ofEpochMilli(it).plusSeconds(86400).plusMillis(1) }
    }
    override suspend fun finish(reservation: CollectorReservation, snapshot: CollectorSnapshot?, attempt: CollectorAttempt): Boolean = database.withTransaction {
        val source = dao.source(reservation.source.id) ?: return@withTransaction false
        val current = dao.attempt(source.id) ?: return@withTransaction false
        if (source.version != reservation.source.version || current.token != reservation.token) return@withTransaction false
        require(attempt.sourceId == source.id && attempt.state != CollectorAttemptState.FETCHING)
        if (snapshot != null) {
            require(snapshot.sourceId == source.id && snapshot.sourceVersion == source.version)
            CollectorPayloadLimits.validateKind(reservation.source.kind,snapshot.payload)
            dao.putSnapshot(CollectorCodec.entity(snapshot))
        }
        dao.putAttempt(CollectorCodec.entity(source.version, attempt))
        if (reservation.source.external) {
            val key = CollectorEndpointIdentity.budgetKey(reservation.source)
            val endpoint = dao.endpoint(key)
            if (endpoint?.attemptedAt == reservation.startedAt.toEpochMilli()) {
                val success = attempt.state in setOf(CollectorAttemptState.CURRENT, CollectorAttemptState.NOT_MODIFIED)
                val failed = attempt.state in setOf(CollectorAttemptState.FAILED, CollectorAttemptState.OFFLINE, CollectorAttemptState.BACKOFF)
                dao.putEndpoint(endpoint.copy(
                    blockedUntil = when { success -> null; failed -> attempt.nextAllowedAt?.toEpochMilli(); else -> endpoint.blockedUntil },
                    failures = when { success -> 0; failed -> attempt.consecutiveFailures; else -> endpoint.failures }))
            }
        }
        true
    }
    override suspend fun recordAttempt(id: String, expectedVersion: Long, attempt: CollectorAttempt): Boolean = database.withTransaction {
        val source = dao.source(id) ?: return@withTransaction false
        if (source.version != expectedVersion || dao.attempt(id)?.token != null) return@withTransaction false
        require(attempt.sourceId == id && attempt.state != CollectorAttemptState.FETCHING)
        dao.putAttempt(CollectorCodec.entity(source.version, attempt))
        true
    }
}
