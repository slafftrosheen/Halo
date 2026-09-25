package com.halo.runtime.cognition.context

import com.halo.sdk.runtime.ScreenContextSnapshot
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AssistantContextRepositoryTest {
    private var now = 10_000L
    private val repository = AssistantContextRepository(nowMs = { now }, ttlMs = 1_000L)

    @Test
    fun `context is interaction scoped and explicit cleanup removes both modalities`() {
        repository.put("first", screen(capturedAtMs = now))
        repository.putImage("first", ImageContext(byteArrayOf(1), "image/jpeg", now))
        repository.put("second", screen(capturedAtMs = now))

        repository.clear("first")

        assertNull(repository.screenFor("first"))
        assertNull(repository.imageFor("first"))
        assertNotNull(repository.screenFor("second"))
    }

    @Test
    fun `overlay lifetime does not affect context and ttl bounds abandoned interactions`() {
        repository.put("interaction", screen(capturedAtMs = now))
        repository.putImage("interaction", ImageContext(byteArrayOf(1), "image/jpeg", now))

        now += 999L
        assertNotNull(repository.screenFor("interaction"))
        assertNotNull(repository.imageFor("interaction"))

        now += 2L
        assertNull(repository.screenFor("interaction"))
        assertNull(repository.imageFor("interaction"))
    }

    private fun screen(capturedAtMs: Long) = ScreenContextSnapshot(
        packageName = "com.example",
        activityName = "ArticleActivity",
        title = "Article",
        webUri = null,
        nodes = emptyList(),
        capturedAtMs = capturedAtMs,
    )
}
