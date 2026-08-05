package com.halo.engines.speech

class AudioFrame(
    val samples: ShortArray,
    val sampleRate: Int,
    val channels: Int,
    /** Monotonic capture timestamp in nanoseconds. */
    val timestamp: Long
)
