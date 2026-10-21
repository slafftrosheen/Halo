package com.halo.app.ui.screens.settings

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.halo.app.ui.state.RuntimeUiProjection
import com.halo.app.ui.theme.HaloTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, shadows = [BrandBook3PowerManager::class])
class BrandAdvancedScreenTest {
    @get:Rule val compose = createComposeRule()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @After fun cleanup() { scope.cancel() }

    @Test fun advancedUsesRealRuntimeProjectionAndCanonicalDestinations() {
        val paths = mutableListOf<String>()
        val projection = RuntimeUiProjection(null, null, null, scope)
        compose.setContent {
            HaloTheme(darkTheme = true) {
                AdvancedSettingsScreen(
                    projection = projection,
                    onBack = {},
                    onRuntime = { paths += "runtime" },
                    onSystemCheck = { paths += "check" },
                    onStorage = { paths += "storage" }
                )
            }
        }
        compose.onNodeWithText("0 active tasks · 0 waiting").assertExists()
        compose.onNodeWithText("Runtime status").performScrollTo().performClick()
        compose.onNodeWithText("System check & reports").performScrollTo().performClick()
        compose.onNodeWithText("Storage & backup").performScrollTo().performClick()
        assertEquals(listOf("runtime", "check", "storage"), paths)
        compose.onNodeWithText("Reset Halo").assertDoesNotExist()
    }

    @Test fun destinationUsesSeparateAdvancedRoute() {
        assertEquals("advanced_settings", SettingsDestination.ADVANCED.route)
        assertEquals("runtime_status", SettingsDestination.RUNTIME.route)
    }
}
