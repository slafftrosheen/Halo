package com.halo.app.screen

import android.app.Application
import android.os.PowerManager
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.halo.app.ui.mini.miniHaloActivityOwner
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ScreenWindowPolicyTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var window: Window
    private var desk by mutableStateOf(false)
    private var mounted by mutableStateOf(true)
    private var environment by mutableStateOf(ScreenEnvironment(true, true, false, true, 80, false, PowerManager.THERMAL_STATUS_NONE))
    private fun show() {
        compose.setContent {
            window = checkNotNull(miniHaloActivityOwner(LocalContext.current)).window
            if (mounted) ScreenWindowPolicy(desk, true, true, LocalDate.of(2026, 10, 23).atTime(23, 0).atZone(ZoneId.systemDefault()).toInstant(), environment)
            Box(Modifier.size(1.dp))
        }
        compose.runOnIdle { window.attributes = window.attributes.apply { screenBrightness = .42f } }
    }
    private fun flag(value: Int) = window.attributes.flags and value != 0

    @Test fun ambientFlagsAndDimmingRestoreOnBackgroundExitAndDisposal() {
        show()
        compose.runOnIdle { desk = true }
        compose.runOnIdle {
            assertTrue(flag(WindowManager.LayoutParams.FLAG_SECURE))
            assertTrue(flag(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON))
            assertEquals(.1f, window.attributes.screenBrightness, 0f)
            environment = environment.copy(resumed = false)
        }
        compose.runOnIdle {
            assertTrue(flag(WindowManager.LayoutParams.FLAG_SECURE))
            assertFalse(flag(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON))
            assertEquals(.42f, window.attributes.screenBrightness, 0f)
            desk = false
        }
        compose.runOnIdle {
            assertFalse(flag(WindowManager.LayoutParams.FLAG_SECURE))
            assertEquals(.42f, window.attributes.screenBrightness, 0f)
            mounted = false
        }
        compose.runOnIdle { assertEquals(.42f, window.attributes.screenBrightness, 0f) }
    }

    @Test fun lowBatteryReleasesOnlyTheKeepAwakeFlagThatScreenAdded() {
        show()
        compose.runOnIdle { desk = true }
        compose.runOnIdle { assertTrue(flag(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)); environment = environment.copy(batteryPercent = 10) }
        compose.runOnIdle { assertFalse(flag(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)); assertTrue(flag(WindowManager.LayoutParams.FLAG_SECURE)) }
    }

    @Test fun flagsOwnedBeforeScreenArePreservedAndOrdinaryScreenDoesNotOwnBrightness() {
        show()
        compose.runOnIdle {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            desk = true
        }
        compose.runOnIdle { desk = false }
        compose.runOnIdle {
            assertTrue(flag(WindowManager.LayoutParams.FLAG_SECURE)); assertTrue(flag(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON))
            assertEquals(.42f, window.attributes.screenBrightness, 0f)
            window.attributes = window.attributes.apply { screenBrightness = .3f }
            mounted = false
        }
        compose.runOnIdle { assertEquals(.3f, window.attributes.screenBrightness, 0f) }
    }
}
