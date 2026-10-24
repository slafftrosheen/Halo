package com.halo.app.ui.shell

import android.content.Intent
import android.content.res.Configuration
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.halo.app.MainActivity
import com.halo.app.di.ManualDependencyGraph
import com.halo.app.language.resolveUiText
import java.util.Locale
import java.util.regex.Pattern
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Full production Activity/AppShell regression, including Android's real warm-intent delivery.
 * Requires an existing adult setup; never completes setup or changes profile, privacy, models,
 * permissions or speech settings. The only requested actions are native page navigation.
 * Does not qualify speech capture, provider execution, or multi-window request ownership.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityScreenIntentPhysicalTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val device get() = UiDevice.getInstance(instrumentation)

    @Test fun realMainActivityRendersWarmAndResumedScreenIntents() {
        ActivityScenario.launch<MainActivity>(screenIntent("notebook")).use { scenario ->
            runBlocking {
                withTimeout(30_000) { ManualDependencyGraph.kidsPreferences.loadedState.first { it } }
            }
            assumeFalse("Preserve existing Kids policy", ManualDependencyGraph.kidsPreferences.enabled.value)
            assumeFalse("Preserve unfinished setup", ManualDependencyGraph.setupProgress.shouldAutoLaunch())

            awaitWorkspace()
            // Repeated requests to the same destination must still work after leaving it.
            repeat(2) {
                deliver("voice_settings")
                awaitVoice()
                deliver("notebook")
                awaitWorkspace()
            }

            // The request must survive delivery while the prior shell is stopped.
            scenario.moveToState(Lifecycle.State.CREATED)
            deliver("voice_settings")
            awaitVoice()
            deliver("notebook")
            awaitWorkspace()
        }
    }

    private fun screenIntent(route: String) = Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .putExtra(HaloScreenCatalog.EXTRA_SCREEN, route)

    private fun deliver(route: String) {
        // Uses framework startActivity/onNewIntent; no direct handler calls or fake navigation.
        context.startActivity(screenIntent(route))
    }

    private fun awaitWorkspace() = awaitPage(
        "Workspace search action", By.desc(translations("Search your workspace")), "notebook"
    )

    private fun awaitVoice() = awaitPage(
        "Voice and reading hub header", By.text(translations("Voice & reading")), "voice_settings"
    )

    private fun awaitPage(label: String, selector: BySelector, route: String) {
        assertTrue(
            "$label did not render after route=$route; pending=${ManualDependencyGraph.requestedHaloScreen.value}",
            device.wait(Until.hasObject(selector), 60_000)
        )
    }

    private fun translations(english: String): Pattern {
        // Resolve the reviewed catalogue in all supported app locales without writing preferences.
        val labels = listOf("en", "ru", "lv", "pl").map { tag ->
            val configuration = Configuration(context.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(tag))
            }
            resolveUiText(context.createConfigurationContext(configuration).resources, english)
        }.distinct()
        return Pattern.compile(labels.joinToString("|", prefix = "^(?:", postfix = ")$") { Pattern.quote(it) })
    }
}
