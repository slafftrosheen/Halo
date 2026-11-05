package com.halo.app.ui.zone5

import com.halo.app.ui.presence.*
import org.junit.Assert.*
import org.junit.Test

class WorkspaceActionPresentationTest {
    @Test fun creationNavigationAndOptionsHaveDistinctRoles() {
        assertEquals(ShellActionRole.PRIMARY, WorkspaceActionPresentation.require("page.note.insert").role)
        assertEquals(ShellActionGroup.INSERT, WorkspaceActionPresentation.require("page.note.insert").group)
        assertEquals(ShellActionRole.DESTINATION, WorkspaceActionPresentation.require("page.notebook.view-tools").role)
        assertEquals(ShellActionGroup.FILE, WorkspaceActionPresentation.require("page.office.open").group)
        assertEquals(ShellActionGroup.EDIT, WorkspaceActionPresentation.require("page.note.undo").group)
    }
    @Test fun unregisteredWorkspaceActionCannotSilentlyFallIntoMore() {
        assertNull(WorkspaceActionPresentation.find("page.note.some-new-command"))
        assertTrue(runCatching { WorkspaceActionPresentation.require("page.note.some-new-command") }.isFailure)
    }
    @Test fun everyStudioPrimaryHasOnePlacementAndStableOrder() {
        val ids = listOf("page.studio.add-layer", "page.studio.layers", "page.studio.tools", "page.studio.ai")
        assertEquals(ids, ids.reversed().sortedBy { WorkspaceActionPresentation.require(it).order })
        assertTrue(ids.all { WorkspaceActionPresentation.require(it).role == ShellActionRole.PRIMARY })
        assertEquals(ids.size, ids.distinct().size)
    }
}
