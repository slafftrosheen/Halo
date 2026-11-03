package com.halo.app.everyday.collectors

import java.time.Instant
import kotlinx.coroutines.flow.Flow

data class CollectorState(val source: CollectorSource, val snapshot: CollectorSnapshot?, val attempt: CollectorAttempt)
data class CollectorReservation(val token: String, val source: CollectorSource, val cached: CollectorSnapshot?, val startedAt: Instant,
    val consecutiveFailures: Int = 0)
sealed interface CollectorAdmission {
    data class Accepted(val reservation: CollectorReservation) : CollectorAdmission
    data class Deferred(val attempt: CollectorAttempt) : CollectorAdmission
}

/** Source version and reservation token fence every completion, including cancellation and process recovery. */
interface CollectorRepository {
    fun observe(): Flow<List<CollectorState>>
    suspend fun states(): List<CollectorState>
    suspend fun state(id: String): CollectorState?
    /** Authority-only reads avoid decoding retained article/page bodies. */
    suspend fun source(id: String): CollectorSource? = state(id)?.source
    suspend fun save(source: CollectorSource, expectedVersion: Long?): CollectorSource
    suspend fun remove(id: String, expectedVersion: Long)
    suspend fun recoverInterrupted(now: Instant)
    suspend fun reserve(id: String, expectedVersion: Long, now: Instant, manual: Boolean): CollectorAdmission
    suspend fun reservePreview(sourceId: String, now: Instant): Boolean
    suspend fun finishPreview(sourceId: String, startedAt: Instant, now: Instant, result: CollectorFetchResult)
    suspend fun finish(reservation: CollectorReservation, snapshot: CollectorSnapshot?, attempt: CollectorAttempt): Boolean
    suspend fun recordAttempt(id: String, expectedVersion: Long, attempt: CollectorAttempt): Boolean
}

class CollectorConflict(message: String) : IllegalStateException(message)
