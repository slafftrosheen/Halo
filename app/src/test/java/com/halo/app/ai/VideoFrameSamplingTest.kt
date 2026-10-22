package com.halo.app.ai

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoFrameSamplingTest {
    @Test fun samplesFourInteriorFramesAtStableFractionsOfTheVideo() {
        assertArrayEquals(longArrayOf(24_000L, 48_000L, 72_000L, 96_000L),
            VideoFrameSampling.timestamps(120_000L))
    }

    @Test fun rejectsZeroAndOverLimitDurationsBeforeDecoding() {
        assertThrows(IllegalArgumentException::class.java) { VideoFrameSampling.timestamps(0L) }
        assertThrows(IllegalArgumentException::class.java) { VideoFrameSampling.timestamps(999L) }
        assertThrows(IllegalArgumentException::class.java) {
            VideoFrameSampling.timestamps(VideoFrameSampling.MAX_DURATION_MS + 1)
        }
    }

    @Test fun aggregatesExactlyFourFramesIntoAUnitVector() {
        val vector = VideoFrameSampling.aggregate(List(4) { listOf(3f,4f) },2)
        assertEquals(0.6f,vector[0],0.0001f)
        assertEquals(0.8f,vector[1],0.0001f)
    }
}
