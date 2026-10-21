package com.halo.app.screen

import org.junit.Assert.*
import org.junit.Test

class ScreenLayoutRequestsTest {
    @Test fun proposalsAreBoundedAndNeverGuessAnUnknownRequest() {
        val names = mapOf("clock" to "Clock & date", "notebook" to "Continue in Notebook")
        assertEquals(LayoutChange.Resize("clock", CardSize.EXPANDED), ScreenLayoutRequests.propose("enlarge Clock & date", names))
        assertNull(ScreenLayoutRequests.propose("delete all my notes", names))
        assertNull(ScreenLayoutRequests.propose("add unknown widget", names))
    }
    @Test fun applyingPreviewPreservesSourcesAndRejectsNoOps() {
        val original = ScreenWidgets.starter("Focus")
        val changed = ScreenLayoutRequests.apply(original, LayoutChange.Remove("notebook"))!!
        assertTrue(original.cards.any { it.widgetType == "notebook" })
        assertFalse(changed.cards.any { it.widgetType == "notebook" })
        assertNull(ScreenLayoutRequests.apply(original, LayoutChange.Add("clock", emptyMap())))
        assertNull(ScreenLayoutRequests.apply(original, LayoutChange.Add("unknown", emptyMap())))
        assertNull(ScreenLayoutRequests.apply(original, LayoutChange.Move("clock", 0)))
    }
}
