package com.halo.host.ink

import com.halo.sdk.runtime.ink.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class InkLocalDraftWriterTest {
    private fun document(id: String) = InkDocument(listOf(InkStroke(InkTool.STYLUS, listOf(InkPoint(1, 2)), id)))

    @Test fun olderSaveCannotAcknowledgeAQueuedNewerCompletedEdit() = runBlocking {
        val firstEntered = CompletableDeferred<Unit>(); val firstFinish = CompletableDeferred<Unit>()
        val secondEntered = CompletableDeferred<Unit>(); val secondFinish = CompletableDeferred<Unit>()
        val writer = InkLocalDraftWriter(this, write = { ink ->
            if (ink.strokes.single().strokeId == "first") { firstEntered.complete(Unit); firstFinish.await() }
            else { secondEntered.complete(Unit); secondFinish.await() }
        }, onPersisted = {})
        assertTrue(writer.edit(document("first"), true)); firstEntered.await()
        assertTrue(writer.edit(document("second"), true))
        firstFinish.complete(Unit); secondEntered.await()
        assertEquals(InkLocalSaveState.SAVING, writer.state.value)
        secondFinish.complete(Unit)
        withTimeout(2000) { writer.state.first { it == InkLocalSaveState.SAVED } }
        writer.close(); writer.awaitClosed()
    }

    @Test fun explicitCloseDrainsLocalRecoveryAndSuppressesQueuedRemotePublication() = runBlocking {
        val entered = CompletableDeferred<Unit>(); val finish = CompletableDeferred<Unit>()
        val writes = mutableListOf<String>(); val sent = mutableListOf<String>()
        val writer = InkLocalDraftWriter(this, write = { ink ->
            val id = ink.strokes.single().strokeId
            if (id == "first") { entered.complete(Unit); finish.await() }
            writes.add(id)
        }, onPersisted = { sent.add(it.strokes.single().strokeId) })
        writer.edit(document("first"), true); entered.await()
        writer.edit(document("latest"), true); writer.close(); finish.complete(Unit)
        withTimeout(2000) { writer.awaitClosed() }
        assertEquals(listOf("first", "latest"), writes)
        assertTrue(sent.isEmpty())
        assertFalse(writer.edit(document("closed"), true))
    }

    @Test fun failedLocalWriteDoesNotSendAndACompletedRecoveryCanBeRetriedLocally() = runBlocking {
        val fail = java.util.concurrent.atomic.AtomicBoolean(true)
        val sent = mutableListOf<String>()
        val writer = InkLocalDraftWriter(this, write = { if (fail.get()) error("Fixture storage full") },
            onPersisted = { sent.add(it.strokes.single().strokeId) })
        writer.edit(document("first"), true)
        withTimeout(2000) { writer.state.first { it == InkLocalSaveState.FAILED } }
        assertTrue(sent.isEmpty())
        fail.set(false); writer.edit(document("recovered"), false)
        withTimeout(2000) { writer.state.first { it == InkLocalSaveState.SAVED } }
        assertTrue(sent.isEmpty())
        writer.close(); writer.awaitClosed()
    }
}
