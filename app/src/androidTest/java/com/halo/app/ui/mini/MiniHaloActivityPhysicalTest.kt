package com.halo.app.ui.mini

import android.content.res.Configuration
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.halo.app.language.resolveUiText
import java.util.Locale
import java.util.regex.Pattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
                awaitMiniContent()
                scenario.onActivity { mini ->
                    binding = com.halo.app.ui.shell.bindShellSpeechLifecycle(mini.lifecycle,
                        { mini.speechHandoff.preservesSpeechOnStop(mini.isChangingConfigurations, mini.isFinishing) }) {
                        released.incrementAndGet()
                    }
                    if (viaCanonicalLauncher) com.halo.app.ui.shell.HaloScreenLauncher.dispatch(mini.applicationContext, "home")
                    else org.junit.Assert.assertTrue(mini.openFullHaloFromMini("home"))
                }
                full = monitor.waitForActivityWithTimeout(10_000)
                org.junit.Assert.assertNotNull("Main Activity must receive the transfer", full)
                assertEquals(com.halo.app.MainActivity::class.java, full!!.javaClass)
                awaitDestroyed(scenario)
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
            // RESUMED precedes asynchronous graph bootstrap/navigation. Wait for the
            // real Mini route (and its BackHandler), not just the Activity window.
            awaitMiniContent()
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            awaitDestroyed(scenario)
        }
    }

    private fun awaitMiniContent() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val labels = listOf("en", "ru", "lv", "pl").map { tag ->
            val configuration = Configuration(context.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(tag))
            }
            resolveUiText(context.createConfigurationContext(configuration).resources, "Mini Halo")
        }.distinct()
        val title = Pattern.compile(labels.joinToString("|", prefix = "^(?:", postfix = ")$") { Pattern.quote(it) })
        assertTrue("Production Mini route did not render before interaction",
            UiDevice.getInstance(instrumentation).wait(Until.hasObject(By.text(title).pkg(context.packageName)), 60_000))
    }

    private fun awaitDestroyed(scenario: ActivityScenario<MiniHaloActivity>) {
        // finish()/Back crosses ActivityTaskManager and later lifecycle transactions;
        // a single idle main queue does not establish that ON_DESTROY was delivered.
        // Observe natural destruction before use{} cleanup can force it ourselves.
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (scenario.state != Lifecycle.State.DESTROYED && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(50)
        }
        assertEquals("Mini must naturally finish after the requested action",
            Lifecycle.State.DESTROYED, scenario.state)
    }
}
