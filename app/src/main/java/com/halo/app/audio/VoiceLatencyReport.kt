package com.halo.app.audio

/** Read-only summary of actual voice measurements. Never imputes missing stages. */
data class VoiceLatencyReport(
    val captured: Int,
    val audioStarted: Int,
    val completed: Int,
    val cancelled: Int,
    val failed: Int,
    val endpointSamples: Int,
    val medianEosToAudioMs: Long?,
    val p95EosToAudioMs: Long?,
) {
    /** No prompt, transcript, model ID, device identifier or tool payload. */
    fun toDiagnosticText(): String = buildString {
        appendLine("HALO_VOICE_LATENCY_REPORT v1")
        appendLine("captureCount=$captured")
        appendLine("audioStartedCount=$audioStarted")
        appendLine("completedCount=$completed")
        appendLine("cancelledCount=$cancelled")
        appendLine("failedCount=$failed")
        appendLine("validBufferedEndOfSpeechSamples=$endpointSamples")
        appendLine("medianEosToFirstAudioMs=${medianEosToAudioMs ?: "NOT_ESTABLISHED"}")
        appendLine("p95EosToFirstAudioMs=${p95EosToAudioMs ?: "NOT_ESTABLISHED"}")
        appendLine("minimumPercentileSampleCount=$MIN_SAMPLES")
        appendLine("warning=window-limited sample; live STT endpoint unknown")
        appendLine("warning=not a paired baseline benchmark or physical sound-at-ear measurement")
    }

    companion object {
        const val MIN_SAMPLES = 30

        fun from(traces: List<VoicePipelineSnapshot>): VoiceLatencyReport {
            // Standalone TTS and typed turns cannot establish speech capture truth.
            val captured = traces.filter { it.captureId != null && it.captureStartedAtMs != null }
            val durations = captured.mapNotNull { it.endOfSpeechToFirstAudioMs }.sorted()
            val enough = durations.size >= MIN_SAMPLES
            return VoiceLatencyReport(
                captured = captured.size,
                audioStarted = captured.count { it.playbackStartedAtMs != null },
                completed = captured.count { it.state == "COMPLETED" },
                cancelled = captured.count { it.state == "CANCELLED" },
                failed = captured.count { it.state == "OUTPUT_FAILED" || it.state == "GENERATION_FAILED" },
                endpointSamples = durations.size,
                medianEosToAudioMs = if (enough) {
                    val mid = durations.size / 2
                    if (durations.size % 2 == 1) durations[mid]
                    else durations[mid - 1] + (durations[mid] - durations[mid - 1]) / 2
                } else null,
                p95EosToAudioMs = if (enough) durations[
                    (kotlin.math.ceil(durations.size * 0.95).toInt() - 1).coerceAtLeast(0)
                ] else null,
            )
        }
    }
}
