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
class SettingsRootReselectionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun explicitRootRequestClearsLocalPageAndInvalidatesItsActionLease() {
        val actions = mutableStateOf<ShellPageProjection?>(null)
        val request = mutableStateOf(0L)
        val unrelatedRecomposition = mutableStateOf("v1")
        compose.setContent { HostlessHaloTheme {
            CompositionLocalProvider(LocalShellHaloOrb provides true, LocalShellPageActions provides actions) {
                SettingsContent(SettingsUiState(version = unrelatedRecomposition.value), AppLanguage.ENGLISH,
                    {}, {}, {}, rootRequest = request.value)
            }
        } }
        compose.runOnIdle { assertTrue(actions.value!!.invoke("page.settings.about")) }
        compose.onNodeWithText("v1").assertExists()
        lateinit var nested: ShellPageProjection
        compose.runOnIdle { nested = actions.value!!; unrelatedRecomposition.value = "v2" }
        // Ordinary state updates and route restoration without a new request retain the page.
        compose.onNodeWithText("v2").assertExists()
        compose.runOnIdle { request.value++ }
        compose.onNodeWithText("v2").assertDoesNotExist()
        compose.runOnIdle {
            assertFalse(nested.invoke("page.settings.back"))
            assertTrue(actions.value!!.actions.any { it.id == "page.settings.setup" })
        }
    }
}
