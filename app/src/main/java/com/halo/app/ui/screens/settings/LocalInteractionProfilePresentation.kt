package com.halo.app.ui.screens.settings

import com.halo.runtime.cognition.context.*
import com.halo.sdk.runtime.NativeMemoryPressure
import java.text.DateFormat
import java.util.Date

internal fun localInteractionProfilePresentation(profile: LocalInferenceRuntimeProfile?,
    pressure: NativeMemoryPressure = NativeMemoryPressure.NORMAL, projectionContextTokens: Long? = 4096L,
    health: LocalInferenceRuntimeHealth? = null): String = buildString {
    appendLine("CALIBRATION · Measured benchmark")
    if (profile == null) {
        appendLine("No compatible benchmark for this device/runtime.")
        appendLine("Run Benchmark to create one.")
    } else {
        appendLine("Measured on this device")
        appendLine("Backend          ${profile.backend}")
        appendLine("TTFT             ${profile.ttftMs?.let { "%.1f s".format(it / 1000.0) } ?: "Unknown"}")
        appendLine("Generation       ${profile.estimatedGenerationTokensPerSecond?.let { "~%.1f est tok/s".format(it) } ?: "Unknown"}")
        appendLine("Load             ${profile.loadMs?.let { "%.1f s".format(it / 1000.0) } ?: "Unknown"}")
        appendLine("Context tested   ${profile.contextTokens ?: "Unknown"}")
        appendLine("Last measured    ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(profile.measuredAtMs))}")
        appendLine("Status           ${if (profile.stable) "Stable" else "Unstable"}")
    }
    appendLine("LIVE · Session only")
    appendLine("Cold samples: ${health?.coldSamples ?: 0}")
    if ((health?.successfulWarmSamples ?: 0) < 3) appendLine("Not enough warm samples")
    appendLine("Warm samples: ${health?.successfulWarmSamples ?: 0} · TTFT EWMA: ${health?.warmTtftEwmaMs?.toLong()?.let { "$it ms" } ?: "Unknown"}")
    appendLine("Failures: ${health?.recentFailureStreak ?: 0}")
    appendLine("EFFECTIVE · Interactive profile · Memory: $pressure")
    for ((label, voice) in listOf("Voice" to true, "Text" to false)) {
        val decision = localInferencePerformance(true, projectionContextTokens, voice, profile, pressure, health)
        if (decision == null) appendLine("$label: outside small-context latency policy")
        else {
            val tier = when (decision.tier) {
                LocalPerformanceTier.SLOW -> "Reduced"
                LocalPerformanceTier.VERY_SLOW -> "Minimal"
                else -> "Standard"
            }
            appendLine("$label: ${decision.tier} / $tier · ${decision.history.maxConversationTokens} history tokens · ${decision.outputTokens} output tokens")
        }
    }
    appendLine(when {
        pressure != NativeMemoryPressure.NORMAL -> "Reason: live memory pressure $pressure"
        health?.tier != null && ((health.successfulWarmSamples >= 3) || health.recentFailureStreak >= 2) -> "Reason: live session health and warm TTFT; calibration remains separate"
        profile?.ttftMs?.let { it > 8000 } == true -> "Reason: measured TTFT > 8 s"
        profile?.ttftMs?.let { it > 4000 } == true -> "Reason: measured TTFT > 4 s"
        else -> "Reason: conservative standard baseline"
    })
}.trim()
