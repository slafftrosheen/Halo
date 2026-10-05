package com.halo.runtime.cognition.context

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Bounded, process-local health. Contains no requests, output, or exception text. */
data class LocalInferenceRuntimeHealth(
    val providerId: String, val modelId: String, val backend: String?, val executionIdentity: String?,
    val successfulWarmSamples: Int = 0, val coldSamples: Int = 0,
    val warmTtftEwmaMs: Double? = null, val recentFailureStreak: Int = 0,
    val consecutiveHealthyWarmSamples: Int = 0, val slowWarmStreak: Int = 0,
    val tier: LocalPerformanceTier? = null,
)

class LocalInferenceRuntimeHealthTracker : LocalInferenceObservationSink {
    private data class Key(val provider: String, val model: String, val backend: String?, val execution: String?)
    @Volatile var lastPolicy: LocalInteractionPolicySnapshot? = null
        private set
    fun recordPolicy(policy: LocalInteractionPolicySnapshot) { lastPolicy = policy }
    private val samples = LinkedHashMap<Key, LocalInferenceRuntimeHealth>()
    private val _snapshots = MutableStateFlow<List<LocalInferenceRuntimeHealth>>(emptyList())
    val snapshots = _snapshots.asStateFlow()

    @Synchronized fun snapshot(provider: String, model: String, backend: String?, execution: String? = null): LocalInferenceRuntimeHealth? =
        samples[Key(provider, model, backend, execution)]?.let { warm ->
            warm.copy(recentFailureStreak = maxOf(warm.recentFailureStreak,
                if (execution != null) samples[Key(provider, model, null, execution)]?.recentFailureStreak ?: 0 else 0))
        } ?: if (execution != null || backend == null) samples[Key(provider, model, null, execution)] else null

    @Synchronized fun baseline(provider: String, model: String, backend: String?, execution: String?, profile: LocalInferenceRuntimeProfile?) {
        val key = Key(provider, model, backend, execution)
        if (key !in samples && profile?.stable == true && profile.ttftMs != null) {
            samples[key] = LocalInferenceRuntimeHealth(provider, model, backend, execution, tier = latencyTier(profile.ttftMs.toDouble()))
            while (samples.size > 32) samples.remove(samples.keys.first())
        }
    }

    override suspend fun record(observation: LocalInferenceObservation) { accept(observation) }

    @Synchronized fun accept(o: LocalInferenceObservation) {
        if (o.outcome in setOf(LocalInferenceObservationOutcome.CANCELLED, LocalInferenceObservationOutcome.PREEMPTED,
                LocalInferenceObservationOutcome.RESOURCE_BUSY)) return
        val key = Key(o.providerId, o.modelId, o.backend, o.executionIdentity)
        var h = samples[key] ?: LocalInferenceRuntimeHealth(o.providerId, o.modelId, o.backend, o.executionIdentity)
        val count: (Int) -> Int = { (it + 1).coerceAtMost(1_000_000) }
        if (!o.success) {
            val failures = count(h.recentFailureStreak)
            val tier = when {
                failures >= 3 -> LocalPerformanceTier.VERY_SLOW
                failures == 2 -> if (h.tier == LocalPerformanceTier.SLOW || h.tier == LocalPerformanceTier.VERY_SLOW) LocalPerformanceTier.VERY_SLOW else LocalPerformanceTier.SLOW
                else -> h.tier
            }
            h = h.copy(recentFailureStreak = failures, tier = tier, consecutiveHealthyWarmSamples = 0, slowWarmStreak = 0)
        } else {
            if (o.backend != null) {
                val failed = samples.remove(Key(o.providerId, o.modelId, null, o.executionIdentity))
                if (failed?.tier != null && (h.tier == null || failed.tier.ordinal > h.tier!!.ordinal)) h = h.copy(tier = failed.tier)
            }
            h = h.copy(recentFailureStreak = 0, coldSamples = if (o.coldStart == true) count(h.coldSamples) else h.coldSamples)
            val ttft = o.ttftMs?.takeIf { it >= 0 }
            if (o.coldStart == false && ttft != null && o.backend != null) {
                val ewma = h.warmTtftEwmaMs?.let { 0.25 * ttft + 0.75 * it } ?: ttft.toDouble()
                val healthy = if (ttft < (if (h.tier == LocalPerformanceTier.VERY_SLOW) 7000 else 3500)) count(h.consecutiveHealthyWarmSamples) else 0
                val slow = if (ttft > 4000) count(h.slowWarmStreak) else 0
                var tier = h.tier
                if (h.successfulWarmSamples + 1 >= 3) {
                    val measured = latencyTier(ewma)
                    if (tier == null) tier = measured
                    else if (slow >= 3 && measured.ordinal > tier.ordinal) tier = measured
                    else if (healthy >= 5) {
                        // Recovery is committed only after five qualifying warm observations.
                        tier = when (tier) {
                            LocalPerformanceTier.VERY_SLOW -> if (ewma < 7000) LocalPerformanceTier.SLOW else tier
                            LocalPerformanceTier.SLOW -> if (ewma < 3500) LocalPerformanceTier.GOOD else tier
                            else -> tier
                        }
                    }
                }
                h = h.copy(successfulWarmSamples = count(h.successfulWarmSamples), warmTtftEwmaMs = ewma,
                    consecutiveHealthyWarmSamples = if (tier != h.tier) 0 else healthy, slowWarmStreak = slow, tier = tier)
            }
        }
        samples[key] = h
        while (samples.size > 32) samples.remove(samples.keys.first())
        _snapshots.value = samples.values.toList()
    }
}

internal fun latencyTier(ttft: Double): LocalPerformanceTier = when {
    ttft <= 4000 -> LocalPerformanceTier.GOOD
    ttft <= 8000 -> LocalPerformanceTier.SLOW
    else -> LocalPerformanceTier.VERY_SLOW
}

/** Latest local policy numbers, never interaction content or identifiers. */
data class LocalInteractionPolicySnapshot(val providerId: String, val modelId: String,
    val inputModality: com.halo.runtime.cognition.turn.InteractionInputModality,
    val responseDelivery: com.halo.sdk.runtime.ResponseDelivery,
    val profileLookupMs: Long, val pressure: com.halo.sdk.runtime.NativeMemoryPressure,
    val performance: LocalInferencePerformance?)
