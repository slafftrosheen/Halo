package com.halo.app.ui.shell

import org.junit.Assert.assertEquals
import org.junit.Test

class HaloScreenCatalogTest {
    @Test fun notebookUsesOneHumanFacingName() {
        assertEquals("Workspace", HaloScreenCatalog.screens["notebook"])
        assertEquals("notebook", Screen.Notebook.route)
        assertEquals("Workspace", Screen.Notebook.title)
    }
    @Test fun legacyObjectAndStudyRoutesStillSelectWorkspace() {
        for (route in listOf("notebook/note/old-id", "notebook/canvas/old-id", "notebook/object/old-id",
            "notebook/memory?section=Suggestions", "study?session=old-id")) {
            assertEquals("notebook", primaryNavigationRoute(route))
        }
    }
}
