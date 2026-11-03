package com.halo.app.ui.screens.kids

import android.app.Application
import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.halo.app.ui.theme.HostlessHaloTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class FamilyModeMenuControlTest {
    @get:Rule val compose = createComposeRule()

    private fun preferences(kids: Boolean): KidsPreferences = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("halo_kids_ui", Context.MODE_PRIVATE).edit().clear().commit()
        KidsPreferences(context).also { it.setEnabled(kids) }
    }

    @Test fun switchPersistsEnteringKidsAndStillAppearsWhenMenuReopens() {
        val preferences = preferences(false)
        val menu = mutableStateOf(true)
        compose.setContent { HostlessHaloTheme {
            val control = rememberFamilyModeMenuControl(preferences)
            if (menu.value) control()
        } }
        compose.onNodeWithTag("family-mode-switch").assertIsOff().performClick()
        compose.waitUntil(5000) { preferences.enabled.value }
        compose.onNodeWithTag("family-mode-switch").assertIsOn()
        compose.runOnIdle { menu.value = false }
        compose.runOnIdle { menu.value = true }
        compose.onNodeWithTag("family-mode-switch").assertIsOn()
        assertTrue(runBlocking { KidsPreferences(ApplicationProvider.getApplicationContext()).getEnabled() })
    }

    @Test fun exitingKidsRequiresTheGateAndClosingMenuCannotDestroyItsState() {
        val preferences = preferences(true)
        val menu = mutableStateOf(true)
        compose.setContent { HostlessHaloTheme {
            val control = rememberFamilyModeMenuControl(preferences)
            if (menu.value) control()
        } }
        compose.onNodeWithTag("family-mode-switch").assertIsOn().performClick()
        compose.onNodeWithText("Ask a grown-up").assertExists()
        // Models the menu being removed on ON_STOP when Android credential UI opens.
        compose.runOnIdle { menu.value = false }
        compose.onNodeWithText("Ask a grown-up").assertExists()
        assertTrue(preferences.enabled.value)
        compose.onNodeWithText("Go back").performClick()
        compose.runOnIdle { menu.value = true }
        compose.onNodeWithTag("family-mode-switch").assertIsOn()
    }

    @Test fun unreadySwitchIsVisibleButCannotChangeFamilyMode() {
        var changes = 0
        compose.setContent { HostlessHaloTheme { FamilyModeMenuRow(true, false) { changes++ } } }
        compose.onNodeWithTag("family-mode-switch").assertIsOn().assertIsNotEnabled()
        assertEquals(0, changes)
    }
}
