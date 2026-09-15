package com.halo.engines.speech

class AudioBuffer(val maxMillis: Long = 10000) {
    init {
        require(maxMillis > 0) { "maxMillis must be > 0" }
    }

    private val lock = Any()
    private val frames = mutableListOf<AudioFrame>()

    fun append(frame: AudioFrame) {
        require(frame.sampleRate > 0) { "sampleRate must be > 0" }
        require(frame.channels > 0) { "channels must be > 0" }
        synchronized(lock) {
            frames.add(frame.copyDetached())
            trimToMaxDuration()
        }
    }

    fun clear() {
        synchronized(lock) {
            frames.clear()
        }
    }

    /**
     * Creates an immutable, detached segment without changing this buffer.
     */
    fun snapshot(): SpeechSegment = synchronized(lock) {
        check(frames.isNotEmpty()) { "Cannot snapshot an empty audio buffer" }

        val firstFrame = frames.first()
        val lastFrame = frames.last()
        val pcm = copyPcm()
        val finishedAt = lastFrame.timestamp + frameDurationNanos(lastFrame)

        SpeechSegment(
            pcm = pcm,
            sampleRate = firstFrame.sampleRate,
            startedAt = firstFrame.timestamp,
            finishedAt = finishedAt,
            audioBuffer = com.halo.sdk.host.AudioBuffer(
                frames.map {
                    com.halo.sdk.host.AudioFrame(
                        samples = it.samples.copyOf(),
                        sampleRate = it.sampleRate,
                        channels = it.channels,
                        timestamp = it.timestamp
                    )
                }
            )
        )
    }

    fun duration(): Long = synchronized(lock) {
        durationMillis()
    }

    fun frames(): List<AudioFrame> = synchronized(lock) {
        frames.map { it.copy() }
    }

    fun pcm(): ShortArray = synchronized(lock) {
        copyPcm()
    }

    private fun trimToMaxDuration() {
        while (frames.size > 1 && durationMillis() > maxMillis) {
            frames.removeAt(0)
        }
    }

    private fun durationMillis(): Long {
        if (frames.isEmpty()) return 0
        val sampleRate = frames.first().sampleRate
        val totalSamples = frames.sumOf { it.samples.size }
        return (totalSamples * 1000L) / sampleRate
    }

    private fun copyPcm(): ShortArray {
        val size = frames.sumOf { it.samples.size }
        val result = ShortArray(size)
        var offset = 0
        for (frame in frames) {
            frame.samples.copyInto(result, offset)
            offset += frame.samples.size
        }
        return result
    }

    private fun AudioFrame.copyDetached(): AudioFrame = AudioFrame(
        samples = samples.copyOf(),
        sampleRate = sampleRate,
        channels = channels,
        timestamp = timestamp
    )

    private fun frameDurationNanos(frame: AudioFrame): Long {
        return (frame.samples.size * NANOS_PER_SECOND) / frame.sampleRate
    }

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000L
    }
}
