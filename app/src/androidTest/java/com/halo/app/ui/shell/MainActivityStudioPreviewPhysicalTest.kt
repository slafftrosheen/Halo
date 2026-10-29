package com.halo.app.ui.shell

import android.content.Intent
import android.content.res.Configuration
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.halo.app.MainActivity
import com.halo.app.R
import com.halo.app.di.ManualDependencyGraph
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import java.util.regex.Pattern

/** Real Activity/dialog/renderer; cancels every preview and creates no project or remote request. */
@RunWith(AndroidJUnit4::class)
class MainActivityStudioPreviewPhysicalTest {
    @Test fun newProjectPreviewSurvivesTypingCancellationAndWarmNavigation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        fun intent(route: String) = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(HaloScreenCatalog.EXTRA_SCREEN, route)
        fun label(id: Int): Pattern = Pattern.compile(listOf("en", "ru", "lv", "pl").map { language ->
            context.createConfigurationContext(Configuration(context.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(language))
            }).getString(id)
        }.distinct().joinToString("|", "^(?:", ")$") { Pattern.quote(it) })

        ActivityScenario.launch<MainActivity>(intent("studio")).use {
            runBlocking { withTimeout(30_000) { ManualDependencyGraph.kidsPreferences.loadedState.first { it } } }
            assumeFalse("Preserve Kids policy", ManualDependencyGraph.kidsPreferences.enabled.value)
            assumeFalse("Preserve unfinished setup", ManualDependencyGraph.setupProgress.shouldAutoLaunch())
            repeat(3) { iteration ->
                assertTrue("Studio entry did not render", device.wait(Until.hasObject(By.text(label(R.string.studio_new_project))), 60_000))
                device.findObject(By.text(label(R.string.studio_new_project))).click()
                val field = By.clazz("android.widget.EditText").pkg(context.packageName)
                assertTrue("New Project dialog did not render", device.wait(Until.hasObject(field), 30_000))
                device.findObject(field).text = "HALO-QA-preview-$iteration"
                assertTrue("Rendered preview did not appear", device.wait(Until.hasObject(By.desc(label(R.string.studio_title))), 30_000))
                // The next frame must still be drawable after replacing the preview.
                device.waitForIdle()
                device.findObject(By.text(label(R.string.studio_cancel))).click()
                assertTrue("Preview dialog did not close", device.wait(Until.gone(field), 30_000))
                context.startActivity(intent("notebook"))
                assertTrue("Workspace did not render", device.wait(Until.hasObject(By.clazz("android.widget.EditText").pkg(context.packageName)), 30_000))
                context.startActivity(intent("studio"))
            }
        }
    }
}
