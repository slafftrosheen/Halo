package com.halo.app.ui.zone5

import androidx.compose.ui.graphics.luminance
import com.halo.app.ui.theme.*
import org.junit.Assert.*
import org.junit.Test

class Zone5WorkspaceContractTest {
    @Test fun filesAndToolsHaveDistinctThemeDerivedInkAcrossAllCuratedPalettes() {
        for (dark in listOf(false, true)) for (accent in HaloAccent.entries) for (tone in HaloTone.entries) {
            val scheme = haloColorScheme(dark, HaloPalette(accent, tone))
            val ink = zone5FileInk(scheme)
            assertNotEquals("$dark/$accent/$tone", scheme.primary, ink)
            val a = ink.luminance(); val b = scheme.background.luminance()
            assertTrue("File icon contrast: $dark/$accent/$tone", (maxOf(a, b) + .05f) / (minOf(a, b) + .05f) >= 3f)
        }
        assertEquals(Zone5IconRole.TOOL, zone5IconRole("tool_studio"))
        assertEquals(Zone5IconRole.FILE, zone5IconRole("type_canvas"))
        assertEquals(Zone5IconRole.FILE, zone5IconRole("library_saved_chat"))
        assertEquals(Zone5IconRole.DEFAULT, zone5IconRole("action_import"))
    }

    @Test fun instrumentsGroupExistingStableActionIdsWithoutInventingCommands() {
        assertEquals(WorkspaceMenuGroup.FILE, workspaceMenuGroup("page.studio.export"))
        assertEquals(WorkspaceMenuGroup.EDIT, workspaceMenuGroup("page.studio.transform"))
        assertEquals(WorkspaceMenuGroup.ADD, workspaceMenuGroup("page.studio.camera"))
        assertEquals(WorkspaceMenuGroup.VIEW, workspaceMenuGroup("page.note.view-read"))
        assertEquals(WorkspaceMenuGroup.MORE, workspaceMenuGroup("page.studio.ai"))
    }

    @Test fun everyInteractionGateIndependentlySuppressesCoaching() {
        assertTrue(zone5GuideAllowed(true, true, true, false, false, false, true, true))
        assertFalse(zone5GuideAllowed(false, true, true, false, false, false, true, true))
        assertFalse(zone5GuideAllowed(true, false, true, false, false, false, true, true))
        assertFalse(zone5GuideAllowed(true, true, false, false, false, false, true, true))
        assertFalse(zone5GuideAllowed(true, true, true, true, false, false, true, true))
        assertFalse(zone5GuideAllowed(true, true, true, false, true, false, true, true))
        assertFalse(zone5GuideAllowed(true, true, true, false, false, true, true, true))
        assertFalse(zone5GuideAllowed(true, true, true, false, false, false, false, true))
        assertFalse(zone5GuideAllowed(true, true, true, false, false, false, true, false))
        assertTrue(zone5SafeGuideAction("page.studio.add-layer"))
        for (id in listOf("page.studio.export", "page.memory.forget", "send-message", "confirm", "page.studio.ai"))
            assertFalse(id, zone5SafeGuideAction(id))
    }

    @Test fun dismissedHintsSurviveNewSessionsAndOnlyOneCoachmarkIsShownPerSession() {
        val storage = com.halo.app.ai.IntentQualificationFixture.MemoryStorage()
        val first = Zone5GuideSession(storage)
        assertFalse(first.shownThisSession)
        first.shown()
        assertTrue(first.shownThisSession)
        first.dismiss("studio-layer.v1")
        val second = Zone5GuideSession(storage)
        assertTrue(second.suppressed("studio-layer.v1"))
        assertFalse(second.suppressed("studio-layer.v2"))
        assertFalse(second.shownThisSession)
    }
}
