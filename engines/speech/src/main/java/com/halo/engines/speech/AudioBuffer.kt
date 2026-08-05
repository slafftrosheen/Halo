package com.halo.engines.speech

class AudioBuffer(val maxMillis: Long = 10000) {
    private val frames = mutableListOf<AudioFrame>()

    fun append(frame: AudioFrame) {
        frames.add(frame)
    }

    fun clear() {
        frames.clear()
    }

    fun duration(): Long {
        if (frames.isEmpty()) return 0
        val sampleRate = frames.first().sampleRate
        val totalSamples = frames.sumOf { it.samples.size }
        return (totalSamples * 1000L) / sampleRate
    }

    fun frames(): List<AudioFrame> = frames.toList()

    fun pcm(): ShortArray {
        val size = frames.sumOf { it.samples.size }
        val result = ShortArray(size)
        var offset = 0
        for (frame in frames) {
            frame.samples.copyInto(result, offset)
            offset += frame.samples.size
        }
        return result
    }
}
