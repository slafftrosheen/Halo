package com.halo.app.ui.screens.settings

import com.halo.app.ui.shell.primaryNavigationRoute
import org.junit.Assert.assertEquals
import org.junit.Test

class DiagnosticNavigationTest {
    @Test fun diagnosticScreensRetainTheSettingsTab() {
        listOf("vision_lab", "local_audio_lab", "settings/intelligence", "settings/intelligence/roles",
            "voice_settings", "model_browser").forEach { route ->
            assertEquals(route, "settings", primaryNavigationRoute(route))
        }
        assertEquals("home", primaryNavigationRoute("home"))
        assertEquals("notebook", primaryNavigationRoute("notebook/note/example"))
    }
}
