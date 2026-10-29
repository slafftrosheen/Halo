package com.halo.app.ui.shell

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Rect
import android.media.AudioManager
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import com.halo.app.MainActivity
import com.halo.app.di.ManualDependencyGraph
import com.halo.app.language.AppLanguage
import com.halo.app.language.resolveUiText
import com.halo.app.ui.theme.AppearanceMode
import java.io.File
import java.util.Locale
import java.util.regex.Pattern
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real production Activity/touch/OS-configuration matrix. Never sends, records or grants anything.
 * Screenshots stay app-private and require review before publication. This qualifies the listed
 * route/footer/layout invariants, not every page action, TalkBack speech or aesthetic approval.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityUnattendedMatrixPhysicalTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val device get() = UiDevice.getInstance(instrumentation)
    private val results = JSONArray()
    private val routes = listOf("home", "notebook", "devices", "settings", "study", "activity", "studio", "screen")
    private val fixed = linkedMapOf("home" to "Home", "notebook" to "Workspace", "devices" to "Devices", "settings" to "Settings")
    private lateinit var runId: String
    private lateinit var report: File
    private lateinit var captures: File

    @Test fun allLocalesWidthsFontsAndThemesKeepNativeRootsAndSingleOrbUsable() {
        val arguments = InstrumentationRegistry.getArguments()
        runId = requireNotNull(arguments.getString("runId"))
        require(runId.matches(Regex("[A-Za-z0-9_-]{1,100}")))
        val sourceStamp = requireNotNull(arguments.getString("sourceStamp"))
        val caseLimit = arguments.getString("caseLimit")?.toInt()?.also { require(it in 1..256) } ?: 256
        assumeTrue("Never interrupt an active phone/audio call", context.getSystemService(AudioManager::class.java).mode == AudioManager.MODE_NORMAL)
        report = File(context.filesDir, "$runId-matrix.json")
        captures = File(context.cacheDir, runId).apply { check(mkdirs()) }
        val originalFont = shell("settings get system font_scale").trim()
        val originalAutoRotation = shell("settings get system accelerometer_rotation").trim()
        val originalUserRotation = shell("settings get system user_rotation").trim()
        val density = shell("wm density")
        val originalOverride = Regex("Override density: (\\d+)").find(density)?.groupValues?.get(1)
        var originalLanguage: AppLanguage? = null
        var originalMode: AppearanceMode? = null
        var originalDrafts: String? = null
        var restored = false
        val failures = mutableListOf<String>()
        try {
            device.setOrientationNatural()
            ActivityScenario.launch<MainActivity>(intent("home")).use {
                runBlocking { withTimeout(30_000) { ManualDependencyGraph.kidsPreferences.loadedState.first { it } } }
                assumeFalse("Preserve the user's Kids policy", ManualDependencyGraph.kidsPreferences.enabled.value)
                assumeFalse("Preserve unfinished setup", ManualDependencyGraph.setupProgress.shouldAutoLaunch())
                assumeFalse("Preserve an existing microphone session", ManualDependencyGraph.audioPerceptionEngine.inputStatus.value.capturing)
                runBlocking { ManualDependencyGraph.appearancePreferences.reloadFromDisk() }
                originalLanguage = ManualDependencyGraph.appLanguagePreferences.language.value
                originalMode = ManualDependencyGraph.appearancePreferences.mode.value
                runBlocking {
                    ManualDependencyGraph.conversationStore.awaitReady()
                    ManualDependencyGraph.composerDraftStore.load(
                        ManualDependencyGraph.conversationStore.activeConversationId().orEmpty())
                }
                // The canonical composer snapshot is Keystore-backed. The old
                // ordinary-preferences key is absent after migration.
                val draftStorage = com.halo.app.security.ComposerDraftSecretStorage(ManualDependencyGraph.secretStore)
                originalDrafts = draftStorage.getString(com.halo.app.ui.screens.home.ComposerDraftStore.STORAGE_KEY)
                matrix@ for ((width, dpi) in listOf(360 to 480, 412 to 419)) {
                    shell("wm density $dpi")
                    for (font in listOf("1.0", "2.0")) {
                        shell("settings put system font_scale $font")
                        for (language in AppLanguage.entries) {
                            instrumentation.runOnMainSync { ManualDependencyGraph.appLanguagePreferences.select(language) }
                            for (mode in listOf(AppearanceMode.LIGHT, AppearanceMode.DARK)) {
                                runBlocking { ManualDependencyGraph.appearancePreferences.setModePersisted(mode) }
                                for (route in routes) {
                                    val id = "w${width}-f${font.replace(".", "")}-${language.tag}-${mode.name.lowercase()}-$route"
                                    val row = JSONObject().put("id", id).put("requestedWidthDp", width)
                                        .put("fontScale", font).put("language", language.tag).put("theme", mode.name).put("route", route)
                                    try {
                                        check(!ManualDependencyGraph.audioPerceptionEngine.inputStatus.value.capturing) {
                                            "Unexpected capture; abort the matrix before further navigation"
                                        }
                                        // Fixed destinations use Android-dispatched touches, other roots the production intent adapter.
                                        if (route in fixed && route != "home") {
                                            val label = text(fixed.getValue(route), language)
                                            val node = device.findObject(By.desc(label))
                                            assertNotNull("Missing fixed $route control before navigation", node)
                                            clickable(node!!).click()
                                        } else context.startActivity(intent(route))
                                        awaitRoute(route)
                                        assertEquals("Navigation must not start communications mode", AudioManager.MODE_NORMAL,
                                            context.getSystemService(AudioManager::class.java).mode)
                                        assertFalse("Navigation must not start microphone capture",
                                            ManualDependencyGraph.audioPerceptionEngine.inputStatus.value.capturing)
                                        val actualWidth = context.resources.configuration.screenWidthDp
                                        row.put("actualWidthDp", actualWidth)
                                        assertTrue("Requested width not applied: $actualWidth", actualWidth in (width-1)..(width+1))
                                        assertEquals(font.toFloat(), context.resources.configuration.fontScale, .01f)
                                        val primary = listOf("Talk", "Send voice", "Stop", "Interrupt reply and talk", "Review")
                                            .map { text(it, language) }.distinct()
                                        val pattern = Pattern.compile(primary.joinToString("|", "^(?:", ")$") { Pattern.quote(it) })
                                        // Route projection can arrive before configuration recreation has
                                        // attached the replacement window's accessibility actions.
                                        val readyDeadline = SystemClock.elapsedRealtime() + 10_000
                                        while (SystemClock.elapsedRealtime() < readyDeadline) {
                                            val visible = device.findObjects(By.desc(pattern))
                                            if (visible.size == 1 && clickableOrNull(visible.single()) != null) break
                                            SystemClock.sleep(100)
                                        }
                                        val orbs = device.findObjects(By.desc(pattern))
                                        assertEquals("There must be one authoritative shell Orb", 1, orbs.size)
                                        val orb = clickable(orbs.single()).visibleBounds
                                        row.put("orb", bounds(orb))
                                        assertUsable("Orb", orb, dpi)
                                        val targets = mutableListOf<Pair<String, Rect>>()
                                        // Screen may deliberately use the existing immersive/ambient presentation.
                                        if (route != "screen") for ((root, english) in fixed) {
                                            val candidates = device.findObjects(By.desc(text(english, language)))
                                                .filter { it.visibleBounds.centerY() > device.displayHeight / 2 }
                                            assertEquals("One fixed $root target", 1, candidates.size)
                                            val rect = clickable(candidates.single()).visibleBounds
                                            assertUsable(root, rect, dpi)
                                            assertFalse("$root overlaps the Orb", Rect.intersects(rect, orb))
                                            targets += root to rect
                                        }
                                        for (a in targets.indices) for (b in a+1 until targets.size)
                                            assertFalse("Fixed roots overlap", Rect.intersects(targets[a].second, targets[b].second))
                                        row.put("status", "PASS").put("nativeRootTargets", targets.size)
                                        // One reference per route/locale/theme, plus every failed case below.
                                        if (width == 360 && font == "2.0") {
                                            val png = File(captures, "$id.png")
                                            check(device.takeScreenshot(png))
                                            row.put("capture", png.name)
                                        }
                                    } catch (failure: AssertionError) {
                                        row.put("status", "FAIL").put("error", failure.message)
                                        failures += "$id: ${failure.message}"
                                        device.takeScreenshot(File(captures, "$id-failed.png"))
                                        if (failures.size == 1) device.dumpWindowHierarchy(File(captures, "$id-failed.xml"))
                                    }
                                    results.put(row)
                                    persist(sourceStamp, restored)
                                    Log.i("UnattendedQA", "$id ${row.optString("status")}")
                                    if (results.length() == caseLimit) break@matrix
                                }
                            }
                        }
                    }
                }
                assertEquals("Navigation/configuration must preserve unsent drafts", originalDrafts,
                    draftStorage.getString(com.halo.app.ui.screens.home.ComposerDraftStore.STORAGE_KEY))
            }
        } finally {
            // Every OS and canonical preference override is restored even if a route assertion fails.
            try {
                shell(if (originalOverride == null) "wm density reset" else "wm density $originalOverride")
                restoreSetting("font_scale", originalFont)
                device.unfreezeRotation()
                restoreSetting("accelerometer_rotation", originalAutoRotation)
                restoreSetting("user_rotation", originalUserRotation)
            } finally {
                originalLanguage?.let { saved -> instrumentation.runOnMainSync { ManualDependencyGraph.appLanguagePreferences.select(saved) } }
                originalMode?.let { saved -> runBlocking { ManualDependencyGraph.appearancePreferences.setModePersisted(saved) } }
                restored = shell("settings get system font_scale").trim() == originalFont &&
                    shell("settings get system accelerometer_rotation").trim() == originalAutoRotation &&
                    shell("settings get system user_rotation").trim() == originalUserRotation &&
                    Regex("Override density: (\\d+)").find(shell("wm density"))?.groupValues?.get(1) == originalOverride &&
                    (originalLanguage == null || ManualDependencyGraph.appLanguagePreferences.language.value == originalLanguage) &&
                    (originalMode == null || ManualDependencyGraph.appearancePreferences.mode.value == originalMode)
                persist(sourceStamp, restored)
            }
        }
        assertTrue("QA settings restoration must be verified", restored)
        assertEquals("Every requested matrix case must run", caseLimit, results.length())
        assertTrue("${failures.size} layout/navigation failures; see private matrix report: ${failures.take(5)}", failures.isEmpty())
    }

    private fun intent(route: String) = Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .putExtra(HaloScreenCatalog.EXTRA_SCREEN, route)

    private fun awaitRoute(route: String) {
        val deadline = SystemClock.elapsedRealtime()+30_000
        while (SystemClock.elapsedRealtime()<deadline) {
            if (ManualDependencyGraph.currentScreenContext.state.value?.metadata?.routeTemplate?.substringBefore('?') == route) {
                device.waitForIdle(300)
                return
            }
            SystemClock.sleep(150)
        }
        fail("Requested route $route did not become current; actual=" +
            ManualDependencyGraph.currentScreenContext.state.value?.metadata?.routeTemplate)
    }

    private fun text(english: String, language: AppLanguage): String {
        val configuration = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(language.tag)) }
        return resolveUiText(context.createConfigurationContext(configuration).resources, english)
    }

    private fun clickable(node: UiObject2): UiObject2 {
        return clickableOrNull(node) ?: throw AssertionError("No clickable owner for a shell target")
    }

    private fun clickableOrNull(node: UiObject2): UiObject2? {
        var current = node
        while (!current.isClickable) {
            val parent = current.parent ?: break
            current = parent
        }
        if (current.isClickable) return current
        // Compose can export a labelled Tab in a separate accessibility branch
        // from its IconButton action. Resolve the smallest real clickable region
        // containing the label; never substitute the 24dp glyph as its hit area.
        val labelBounds = node.visibleBounds
        return device.findObjects(By.clickable(true))
            .filter { it.visibleBounds.contains(labelBounds) }
            .minByOrNull { it.visibleBounds.width().toLong() * it.visibleBounds.height() }
    }

    private fun assertUsable(label: String, rect: Rect, dpi: Int) {
        val minimum = 48f*dpi/160f - 1f // Allow one layout pixel of rounding at the 48dp contract.
        assertTrue("$label target smaller than 48dp: $rect", rect.width() >= minimum && rect.height() >= minimum)
        assertTrue("$label target clipped by display: $rect", rect.left>=0 && rect.top>=0 &&
            rect.right<=device.displayWidth && rect.bottom<=device.displayHeight)
    }

    private fun bounds(rect: Rect) = JSONArray(listOf(rect.left,rect.top,rect.right,rect.bottom))
    private fun shell(command: String) = device.executeShellCommand(command)
    private fun restoreSetting(key: String, value: String) {
        shell(if (value == "null") "settings delete system $key" else "settings put system $key $value")
    }
    private fun persist(sourceStamp: String, restored: Boolean) {
        report.writeText(JSONObject().put("runId",runId).put("sourceStamp",sourceStamp)
            .put("scope","REAL_MAIN_ACTIVITY_ROUTE_FOOTER_MATRIX_ONLY").put("settingsRestored",restored)
            .put("cases",results).toString(2))
    }
}
