package com.halo.app.context

import org.junit.Assert.*
import org.junit.Test

class CurrentScreenContextTest {
    private fun note(version: String? = "7", selection: List<String> = emptyList()) = CurrentScreenMetadata(
        "notebook/note/{noteId}", CurrentScreenSuite.OFFICE, "note", "note-1", version, selection,
        uiActionIds = listOf("page.note.find"))

    @Test fun changedSelectionOrVersionInvalidatesSnapshotWithoutReadingAnything() {
        val owner = CurrentScreenContext(); val lease = owner.attach("entry-1")
        assertTrue(owner.publish(lease, note()))
        val original = requireNotNull(owner.read())
        owner.publish(lease, note())
        assertEquals(original.snapshot, owner.read()?.snapshot)
        owner.publish(lease, note(selection = listOf("block-1")))
        assertNull(owner.read(original.snapshot))
        val selected = requireNotNull(owner.read())
        owner.publish(lease, note(version = "8", selection = listOf("block-1")))
        assertNull(owner.read(selected.snapshot))
        assertEquals("8", owner.read()?.metadata?.sourceVersion)
    }

    @Test fun oldPageCannotPublishOrDetachCurrentPage() {
        val owner = CurrentScreenContext(); val first = owner.attach("entry-1")
        owner.publish(first, note()); val stale = requireNotNull(owner.read()).snapshot
        val second = owner.attach("entry-2")
        assertNull(owner.read())
        assertFalse(owner.publish(first, note()))
        owner.publish(second, note("9"))
        owner.detach(first)
        assertEquals("9", owner.read()?.metadata?.sourceVersion)
        assertNull(owner.read(stale))
        owner.detach(second)
        assertNull(owner.read())
        assertFalse(owner.publish(second, note()))
    }

    @Test fun callerListMutationCannotChangePublishedSelection() {
        val owner = CurrentScreenContext(); val lease = owner.attach("entry")
        val selection = mutableListOf("block-1")
        owner.publish(lease, note(selection = selection)); selection += "block-2"
        assertEquals(listOf("block-1"), owner.read()?.metadata?.selectionIds)
    }

    @Test fun unknownSourceVersionStaysUnknownAndBoundsRejectBodies() {
        val owner = CurrentScreenContext(); val lease = owner.attach("entry")
        owner.publish(lease, note(null))
        assertNull(owner.read()?.metadata?.sourceVersion)
        assertThrows(IllegalArgumentException::class.java) { owner.publish(lease, note().copy(objectId = "private note title")) }
        assertThrows(IllegalArgumentException::class.java) { owner.publish(lease, note(selection = List(101) { "block-$it" })) }
        assertThrows(IllegalArgumentException::class.java) { owner.publish(lease, note().copy(canonicalToolIds = listOf("page.note.find"))) }
    }
    @Test fun oversizedSelectionPublishesBoundedTruthAndTailChangesInvalidateSnapshot() {
        val owner = CurrentScreenContext(); val lease = owner.attach("library")
        val full = List(150) { "object-$it" }
        val metadata = note(selection = full.take(100)).copy(selectionCount = full.size, selectionTruncated = true)
        assertTrue(owner.publish(lease, metadata, full))
        val first = requireNotNull(owner.read())
        assertEquals(100, first.metadata.selectionIds.size)
        assertEquals(150, first.metadata.selectionCount)
        assertTrue(first.metadata.selectionTruncated)
        val changedTail = full.dropLast(1) + "object-changed"
        assertTrue(owner.publish(lease, metadata, changedTail))
        assertNull(owner.read(first.snapshot))
        assertEquals(metadata, owner.read()?.metadata)
        val stable = requireNotNull(owner.read()).snapshot
        owner.publish(lease, metadata, changedTail)
        assertEquals(stable, owner.read()?.snapshot)
        assertThrows(IllegalArgumentException::class.java) { owner.publish(lease, metadata.copy(selectionTruncated = false), full) }
        assertThrows(IllegalArgumentException::class.java) { owner.publish(lease, metadata) }
    }

}
