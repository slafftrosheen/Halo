package com.halo.app.ui.screens.notebook

import com.halo.app.data.notebook.WorkspaceObject
import com.halo.app.data.notebook.WorkspaceObjectId
import com.halo.app.data.notebook.WorkspaceObjectLifecycle
import com.halo.app.data.notebook.WorkspaceObjectSource
import com.halo.app.data.notebook.WorkspaceObjectType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HaloLibrarySourcesTest {
    @Test fun savedOriginsRefineThePresentationWithoutReplacingTheCanonicalType() {
        val sources = HaloLibrarySources(places = setOf("pin"), scans = setOf("scan"), photographs = setOf("image", "note"))
        val place = item("pin", WorkspaceObjectType.NOTE)
        assertEquals(HaloLibraryKind.PLACE, sources.kind(place))
        assertEquals(WorkspaceObjectType.NOTE, place.type)
        assertEquals(HaloLibraryKind.SCAN, sources.kind(item("scan", WorkspaceObjectType.FILE)))
        assertEquals(HaloLibraryKind.PHOTO, sources.kind(item("image", WorkspaceObjectType.NOTE, WorkspaceObjectSource.DEVICE_CAPTURE)))
        assertEquals(HaloLibraryKind.NOTE, sources.kind(item("note", WorkspaceObjectType.NOTE)))
        assertEquals(HaloLibraryKind.FILE, sources.kind(item("paper", WorkspaceObjectType.FILE)))
        assertNull(sources.kind(item("memory", WorkspaceObjectType.MEMORY)))
    }

    @Test fun filterKindAndSearchWorkForProjectedItemsInRussian() {
        assertEquals(HaloLibraryKind.PHOTO, NotebookFilter.GLASSES_PHOTOS.libraryKind())
        assertEquals(HaloLibraryKind.PLAN, NotebookFilter.ORGANIZER.libraryKind())
        assertTrue(matchesLibraryTerms("  ЛЕС  поезд  ", "Поезд до Лесной"))
        assertFalse(matchesLibraryTerms("поезд ПАРК", "Поезд до Лесной"))
    }

    private fun item(id: String, type: WorkspaceObjectType, source: WorkspaceObjectSource = WorkspaceObjectSource.USER_CREATED) =
        WorkspaceObject(WorkspaceObjectId(id), type, "Scanned paper.pdf", 0L, 0L, source,
            WorkspaceObjectLifecycle.ACTIVE, false, "${type.name.lowercase()}:$id")
}
