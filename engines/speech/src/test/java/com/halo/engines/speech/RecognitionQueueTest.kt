package com.halo.engines.speech

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class RecognitionQueueTest {
    @Test
    fun unboundedQueuePreservesSegmentOrder(): Unit = runBlocking {
        val queue = Channel<SpeechSegment>(Channel.UNLIMITED)
        repeat(100) { index ->
            queue.send(segment(index))
        }
        queue.close()

        repeat(100) { index ->
            assertEquals(index.toLong(), queue.receive().startedAt)
        }
        assertEquals(null, queue.receiveCatching().getOrNull())
    }

    private fun segment(index: Int): SpeechSegment = SpeechSegment(
        pcm = shortArrayOf(index.toShort()),
        sampleRate = 16_000,
        startedAt = index.toLong(),
        finishedAt = index + 1L
    )
}
