package com.halo.app.ui.shell

import org.junit.Assert.assertEquals
import org.junit.Test

class HaloScreenCatalogTest {
    @Test fun notebookUsesOneHumanFacingName() {
        assertEquals("Notebook", HaloScreenCatalog.screens["notebook"])
        assertEquals("notebook", Screen.Notebook.route)
        assertEquals("Notebook", Screen.Notebook.title)
    }
}
