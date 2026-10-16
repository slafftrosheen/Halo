package com.halo.app.ui.mini

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Launch as the app UID: Mini Halo deliberately is not exported to shell/other apps. */
@RunWith(AndroidJUnit4::class)
class MiniHaloActivityPhysicalTest {
    @Test fun actualMiniActivityLaunchesAndBackCloses() {
        ActivityScenario.launch(MiniHaloActivity::class.java).use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertEquals(Lifecycle.State.DESTROYED, scenario.state)
        }
    }
}
