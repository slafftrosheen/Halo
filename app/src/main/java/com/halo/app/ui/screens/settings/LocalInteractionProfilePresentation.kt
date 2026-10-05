package com.halo.app.ui.screens.settings

import com.halo.runtime.cognition.context.*
import com.halo.sdk.runtime.NativeMemoryPressure
import java.text.DateFormat
import java.util.Date

internal fun localInteractionProfilePresentation(profile: LocalInferenceRuntimeProfile?,
    pressure: NativeMemoryPressure = NativeMemoryPressure.NORMAL, projectionContextTokens: Long? = 4096L): String = buildString {
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
    appendLine("Interactive profile")
    for ((label, voice) in listOf("Voice" to true, "Text" to false)) {
        val decision = localInferencePerformance(true, projectionContextTokens, voice, profile, pressure)
        if (decision == null) appendLine("$label: outside small-context latency policy")
        else {
            val tier = when (decision.tier) {
                LocalPerformanceTier.SLOW -> "Reduced"
                LocalPerformanceTier.VERY_SLOW -> "Minimal"
                else -> "Standard"
            }
            appendLine("$label: $tier · ${decision.history.maxConversationTokens} history tokens · ${decision.outputTokens} output tokens")
        }
    }
    appendLine(when {
        pressure != NativeMemoryPressure.NORMAL -> "Reason: live memory pressure $pressure"
        profile?.ttftMs?.let { it > 8000 } == true -> "Reason: measured TTFT > 8 s"
        profile?.ttftMs?.let { it > 4000 } == true -> "Reason: measured TTFT > 4 s"
        else -> "Reason: conservative standard baseline"
    })
}.trim()
