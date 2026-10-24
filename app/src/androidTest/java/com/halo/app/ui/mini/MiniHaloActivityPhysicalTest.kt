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
    @Test fun actualMiniExpansionStopsOldHostWithoutReleasingSharedSpeechBoundary() = assertMiniTransfer(false)

    @Test fun canonicalLauncherFromApplicationContextAdoptsVisibleMiniBeforeItsStop() = assertMiniTransfer(true)

    private fun assertMiniTransfer(viaCanonicalLauncher: Boolean) {
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val monitor = instrumentation.addMonitor(com.halo.app.MainActivity::class.java.name, null, false)
        val released = java.util.concurrent.atomic.AtomicInteger()
        var binding: AutoCloseable? = null
        var full: android.app.Activity? = null
        try {
            ActivityScenario.launch(MiniHaloActivity::class.java).use { scenario ->
                scenario.onActivity { mini ->
                    binding = com.halo.app.ui.shell.bindShellSpeechLifecycle(mini.lifecycle,
                        { mini.speechHandoff.preservesSpeechOnStop(mini.isChangingConfigurations, mini.isFinishing) }) {
                        released.incrementAndGet()
                    }
                    if (viaCanonicalLauncher) com.halo.app.ui.shell.HaloScreenLauncher.dispatch(mini.applicationContext, "home")
                    else org.junit.Assert.assertTrue(mini.openFullHaloFromMini("home"))
                }
                full = monitor.waitForActivityWithTimeout(10_000)
                instrumentation.waitForIdleSync()
                org.junit.Assert.assertNotNull("Main Activity must receive the transfer", full)
                assertEquals(com.halo.app.MainActivity::class.java, full!!.javaClass)
                assertEquals(Lifecycle.State.DESTROYED, scenario.state)
                assertEquals(0, released.get())
            }
        } finally {
            instrumentation.runOnMainSync { binding?.close(); full?.finish() }
            instrumentation.removeMonitor(monitor)
        }
    }

    @Test fun actualMiniActivityLaunchesAndBackCloses() {
        ActivityScenario.launch(MiniHaloActivity::class.java).use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertEquals(Lifecycle.State.DESTROYED, scenario.state)
        }
    }
}
