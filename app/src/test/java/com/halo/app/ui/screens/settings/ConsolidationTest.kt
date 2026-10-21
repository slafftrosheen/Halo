package com.halo.app.ui.screens.settings

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import com.halo.app.embodiment.BuddyBotTelemetry
import com.halo.app.ui.screens.devices.BuddyBotDriveHud
import com.halo.app.ui.screens.integrations.McpEditor
import com.halo.app.ui.screens.integrations.persistableMcpEndpointDraft
import com.halo.app.ui.shell.primaryNavigationRoute
import com.halo.app.ui.theme.HostlessHaloTheme as HaloTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w320dp-h800dp")
class ConsolidationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun disconnectedHudCannotPresentStaleAuthorityOrClearance() {
        compose.setContent { HaloTheme(darkTheme = true, fontScale = 2f) {
            BuddyBotDriveHud(BuddyBotTelemetry(motorsArmed = true, safetyState = "ARMED",
                rangeMm = 1000, rangeValid = true), false, "FORWARD")
        } }
        compose.onNodeWithText("Not confirmed").assertExists()
        compose.onNodeWithText("ARMED").assertDoesNotExist()
        compose.onNodeWithText("CLEAR").assertDoesNotExist()
        compose.onNodeWithText("1000 mm detected").assertDoesNotExist()
        compose.onNodeWithText("Sensor unavailable").assertExists()
    }

    @Test fun integrationMetadataRestoresButCredentialRequiresFreshEntry() {
        val restoration = StateRestorationTester(compose)
        var saves = 0
        restoration.setContent { HaloTheme {
            McpEditor(null, false, null, null, {}, { _, _ -> saves++ })
        } }
        compose.onNodeWithText("Name").performTextInput("My service")
        compose.onNodeWithText("Endpoint").performTextInput("https://example.test/mcp")
        compose.onNodeWithText("Bearer").performClick()
        compose.onNodeWithText("Bearer token").performScrollTo().performTextInput("ephemeral-secret")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("My service").assertExists()
        compose.onNodeWithText("https://example.test/mcp").assertExists()
        compose.onNodeWithText("Save").performScrollTo().assertIsNotEnabled()
        assertEquals(0, saves)
        assertNull(persistableMcpEndpointDraft("https://example.test/mcp?token=secret"))
        assertNull(persistableMcpEndpointDraft("https://user:secret@example.test/mcp"))
    }

    @Test fun nestedAndAliasRoutesKeepTheirCanonicalParent() {
        val routes = mapOf("activity?runId=123" to "activity", "study?captureSource=note" to "notebook",
            "notebook/study/id?review=true" to "notebook", "notebook/conversation/id" to "notebook",
            "device/id" to "devices", "discovery" to "devices", "notifications/key" to "home",
            "automation?routineId=id" to "settings", "automation_review/id/proposal" to "settings",
            "integrations/mcp/id" to "settings", "provider_setup" to "settings", "model_browser" to "settings",
            "settings/intelligence/models?providerId=id&role=CHAT" to "settings", "storage_backup" to "settings")
        routes.forEach { (route, parent) -> assertEquals(route, parent, primaryNavigationRoute(route)) }
        assertNull(primaryNavigationRoute(null))
    }
}
