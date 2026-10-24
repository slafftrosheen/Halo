package com.halo.host.ink

import com.halo.sdk.runtime.ink.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class InkLocalDraftStoreTest {
    @get:Rule val folder = TemporaryFolder()
    private val document = InkDocument(listOf(InkStroke(InkTool.STYLUS, listOf(InkPoint(1, 2)), "stroke")))
    @Test fun localCompletedStrokesReopenWithIdentityAndInvalidSaveKeepsPreviousRecovery() {
        val path = folder.root.toPath().resolve("drawing.ink")
        val store = InkLocalDraftStore(path)
        assertNull(store.load())
        store.save(document)
        assertEquals(document, InkLocalDraftStore(path).load())
        assertThrows(IllegalArgumentException::class.java) { store.save(document.copy(strokes = document.strokes + document.strokes)) }
        assertEquals(document, InkLocalDraftStore(path).load())
        assertEquals(listOf("drawing.ink"), folder.root.list()!!.toList())
    }
    @Test fun corruptAndOversizeRecoveryFailsClosedRatherThanBecomingEmptyDrawing() {
        val path = folder.newFile("drawing.ink").toPath()
        java.nio.file.Files.write(path, "invalid".toByteArray())
        assertThrows(IllegalArgumentException::class.java) { InkLocalDraftStore(path).load() }
        java.nio.file.Files.write(path, ByteArray(InkLimits.MAX_ENCODED_CHARS + 1))
        assertThrows(IllegalArgumentException::class.java) { InkLocalDraftStore(path).load() }
    }
    @Test fun invitationIsLiteralLocalBoundedAndDoesNotTrustDnsOrPublicAddresses() {
        val invitation = InkConnectionInvitation("session", "192.168.1.20", 4444, "a".repeat(64))
        assertEquals(invitation, InkConnectionInvitation.decode(invitation.encode()))
        listOf("example.com", "8.8.8.8", "0.0.0.0", "192.168.001.2", "192.168.1.300").forEach {
            assertThrows(IllegalArgumentException::class.java) { invitation.copy(address = it) }
        }
        assertNull(InkConnectionInvitation.decode(invitation.encode() + byteArrayOf(10)))
        assertNull(InkConnectionInvitation.decode(ByteArray(1025)))
    }
    @Test fun identicalDocumentIdsOnDifferentTrustedHostsCannotRecoverEachOthersInk() {
        val first = InkLocalDraftStore.scopeKey("a".repeat(64), "board", "document")
        val second = InkLocalDraftStore.scopeKey("b".repeat(64), "board", "document")
        assertNotEquals(first, second)
        InkLocalDraftStore(folder.root.toPath().resolve(first)).save(document)
        assertNull(InkLocalDraftStore(folder.root.toPath().resolve(second)).load())
    }
}
