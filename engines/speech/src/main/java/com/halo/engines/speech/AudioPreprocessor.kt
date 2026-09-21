package com.halo.engines.speech

import kotlin.math.*

interface AudioPreprocessor {
    fun process(frame: AudioFrame): AudioFrame
    fun reset() {}
}

/** Streaming, windowed-sinc low-pass conversion to mono PCM16. Phase and filter
 * history survive packet boundaries; timestamps use the output sample clock. */
class DefaultAudioPreprocessor(private val targetRate: Int = 16000) : AudioPreprocessor {
    private var rate = 0
    private var history = DoubleArray(0)
    private var cursor = 0.0
    private var outputSamples = 0L
    private var epoch = 0L
    private val radius = 16

    override fun reset() {
        rate = 0; history = DoubleArray(0); cursor = 0.0; outputSamples = 0; epoch = 0
    }

    override fun process(frame: AudioFrame): AudioFrame {
        require(frame.sampleRate > 0 && frame.channels > 0)
        require(frame.samples.size % frame.channels == 0)
        if (rate != frame.sampleRate) {
            reset(); rate = frame.sampleRate; epoch = frame.timestamp
            history = DoubleArray(radius); cursor = radius.toDouble()
        }
        if (frame.sampleRate == targetRate && frame.channels == 1) return frame
        val count = frame.samples.size / frame.channels
        val data = DoubleArray(history.size + count)
        history.copyInto(data)
        for (i in 0 until count) {
            var sum = 0.0
            for (channel in 0 until frame.channels) sum += frame.samples[i * frame.channels + channel]
            data[history.size + i] = sum / frame.channels
        }
        val step = rate.toDouble() / targetRate
        val cutoff = min(1.0, targetRate.toDouble() / rate) * 0.92
        val out = ShortArray(max(0, ceil((data.size - radius - cursor) / step).toInt()))
        var n = 0
        while (cursor + radius < data.size && n < out.size) {
            var sum = 0.0; var weight = 0.0
            val center = floor(cursor).toInt()
            for (i in center - radius + 1..center + radius) {
                val distance = i - cursor
                val x = PI * distance * cutoff
                val sinc = if (abs(x) < 1e-10) 1.0 else sin(x) / x
                val w = cutoff * sinc * (0.5 + 0.5 * cos(PI * distance / radius))
                sum += data[i] * w; weight += w
            }
            out[n++] = (sum / weight).roundToInt().coerceIn(-32768, 32767).toShort()
            cursor += step
        }
        val consumed = max(0, floor(cursor).toInt() - radius)
        history = data.copyOfRange(consumed, data.size)
        cursor -= consumed
        val timestamp = epoch + outputSamples * 1_000_000_000L / targetRate
        outputSamples += n
        return AudioFrame(if (n == out.size) out else out.copyOf(n), targetRate, 1, timestamp)
    }
}
