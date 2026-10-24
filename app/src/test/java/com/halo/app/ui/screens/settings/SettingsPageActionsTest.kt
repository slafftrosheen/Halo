package com.halo.app.ui.screens.settings

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.halo.app.language.AppLanguage
import com.halo.app.ui.presence.*
import com.halo.app.ui.theme.HostlessHaloTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class SettingsPageActionsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun orbPageCallbacksOperateCurrentSettingsAndRejectStaleModalOwner() {
        val holder = mutableStateOf<ShellPageProjection?>(null)
        var setup = 0
        val opened = mutableListOf<SettingsDestination>()
        compose.setContent { HostlessHaloTheme {
            CompositionLocalProvider(LocalShellPageActions provides holder) {
                SettingsContent(SettingsUiState(version = "fixture-version"), AppLanguage.ENGLISH, {}, {},
                    { opened += it }, onSetup = { setup++ })
            }
        } }
        lateinit var mainPage: ShellPageProjection
        compose.runOnIdle { mainPage = holder.value!!; assertEquals("settings", mainPage.owner.routeFamily); assertTrue(mainPage.invoke("page.settings.search")) }
        compose.onNode(hasSetTextAction()).assertIsDisplayed()
        compose.runOnIdle { assertTrue(holder.value!!.invoke("page.settings.about")) }
        compose.onNodeWithText("fixture-version").assertIsDisplayed()
        compose.runOnIdle { assertFalse(mainPage.invoke("page.settings.setup")); assertTrue(holder.value!!.invoke("page.settings.back")) }
        compose.onNodeWithText("fixture-version").assertDoesNotExist()
        compose.runOnIdle { assertTrue(holder.value!!.invoke("page.settings.setup")) }
        assertEquals(1, setup)
        assertTrue("Page actions must not route through Home or execute unrelated navigation", opened.isEmpty())
    }
}
