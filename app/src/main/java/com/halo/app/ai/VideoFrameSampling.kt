package com.halo.app.ai

/** Stable, bounded timestamps for the local video-frame search preprocessor. */
internal object VideoFrameSampling {
    const val MAX_DURATION_MS = 120_000L
    const val FRAME_COUNT = 4

    fun timestamps(durationMs: Long): LongArray {
        require(durationMs in 1_000L..MAX_DURATION_MS) { "VIDEO_DURATION_UNSUPPORTED" }
        return LongArray(FRAME_COUNT) { index -> durationMs * (index + 1) / (FRAME_COUNT + 1) }
    }

    /** Unit-normalized mean so one video has one comparable vector regardless of frame count. */
    fun aggregate(vectors: List<List<Float>>, dimension: Int): List<Float> {
        require(vectors.size == FRAME_COUNT && dimension in 1..16_384)
        require(vectors.all { vector -> vector.size == dimension && vector.all { it.isFinite() } })
        val mean = DoubleArray(dimension) { index -> vectors.sumOf { it[index].toDouble() } / FRAME_COUNT }
        val norm = kotlin.math.sqrt(mean.sumOf { it * it })
        require(norm.isFinite() && norm > 0.0)
        return mean.map { (it / norm).toFloat() }
    }
}
