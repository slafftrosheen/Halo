package com.halo.engines.speech

/**
 * Immutable PCM captured between two monotonic timestamps.
 *
 * The constructor and [pcm] accessor both copy the PCM data so a segment never
 * exposes mutable audio owned by another component.
 */
class SpeechSegment(
    pcm: ShortArray,
    val sampleRate: Int,
    val startedAt: Long,
    val finishedAt: Long,
    val audioBuffer: com.halo.sdk.host.AudioBuffer
) {
    private val samples = pcm.copyOf()

    init {
        require(sampleRate > 0) { "sampleRate must be greater than zero" }
        require(finishedAt >= startedAt) {
            "finishedAt must not be earlier than startedAt"
        }
    }

    val pcm: ShortArray
        get() = samples.copyOf()

    val sampleCount: Int
        get() = samples.size

    val durationNanos: Long
        get() = finishedAt - startedAt

    val durationMillis: Long
        get() = durationNanos / NANOS_PER_MILLISECOND

    private companion object {
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
