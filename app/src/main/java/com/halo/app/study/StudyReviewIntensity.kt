package com.halo.app.study

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * How hard the learner wants to hold on to what they studied.
 *
 * The value is the target *retention* the memory model schedules against: a stricter target buys a
 * higher chance of recall at the cost of more reviews. Only the memory model follows this setting —
 * the mistake ladder is a drill rhythm and stays independent of it, so choosing "Relaxed" never
 * weakens how often an actual mistake is re-tested.
 */
enum class StudyReviewIntensity(
    val label: String,
    val desiredRetention: Double,
    val description: String,
) {
    RELAXED("Relaxed", 0.85, "Longer gaps · expect to forget more"),
    STANDARD("Standard", 0.90, "Balanced spacing"),
    STRICT("Strict", 0.95, "Shorter gaps · remember for longer"),
    ;

    val retentionPercent: Int get() = (desiredRetention * 100).toInt()

    companion object {
        val DEFAULT: StudyReviewIntensity = STANDARD

        /** Unknown or missing names fall back to the default rather than throwing at startup. */
        fun fromName(raw: String?): StudyReviewIntensity =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: DEFAULT
    }
}

/** Persistence seam, so scheduling and mastery stay testable without Android or DataStore. */
interface StudyReviewIntensityStore {
    val stored: Flow<StudyReviewIntensity>

    suspend fun store(value: StudyReviewIntensity)
}

/**
 * Authoritative in-memory value of the review intensity.
 *
 * Two callers need the same number in two different shapes: the UI wants a flow, while scheduling
 * and mastery recomputation run synchronously inside a review transaction and cannot suspend to read
 * a preference store. Keeping one mirrored value satisfies both without a second source of truth.
 *
 * Construction is cheap and main-safe: the stored value arrives on [scope] and the published value
 * settles as soon as the store's first emission lands — long before a learner can finish a review.
 */
class StudyReviewIntensityController(
    private val store: StudyReviewIntensityStore? = null,
    scope: CoroutineScope,
) {
    private val mutableIntensity = MutableStateFlow(StudyReviewIntensity.DEFAULT)

    /** Observable value for the UI. */
    val intensity: StateFlow<StudyReviewIntensity> = mutableIntensity.asStateFlow()

    /** Number of successful local selections; mirror emissions older than the newest one are stale. */
    private val localSelections = AtomicLong()

    private val syncJob: Job? = store?.let { source ->
        scope.launch {
            source.stored
                .catch { emit(StudyReviewIntensity.DEFAULT) }
                .collect { stored ->
                    // A selection made after this emission was read must win, even if the collector
                    // delivers the older value later on a different dispatcher.
                    if (localSelections.get() == 0L || mutableIntensity.value == stored) {
                        mutableIntensity.value = stored
                    }
                }
        }
    }

    /** Synchronous read used by the scheduler and by mastery recomputation. */
    fun current(): StudyReviewIntensity = mutableIntensity.value

    fun currentRetention(): Double = mutableIntensity.value.desiredRetention

    /**
     * Store first, then publish: if persistence fails the learner keeps seeing the old, true value
     * instead of a setting that will silently revert on the next launch. A controller without a
     * store reports failure rather than pretending a preference was saved.
     */
    suspend fun select(value: StudyReviewIntensity): Boolean {
        val target = store ?: return false
        if (runCatching { target.store(value) }.isFailure) return false
        localSelections.incrementAndGet()
        mutableIntensity.value = value
        return true
    }

    /** Stops mirroring the store; the owning component's scope also ends this, so it is belt-and-braces. */
    fun shutdown() {
        syncJob?.cancel()
    }
}
