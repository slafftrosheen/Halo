package com.halo.app.ui.mini

import android.content.res.Configuration
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.halo.app.language.uiCatalogue
import com.halo.app.ui.presence.HaloSignalPresentation
import com.halo.app.ui.theme.HaloTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/** Production MiniHaloContent on a physical Compose host; callbacks never perform real actions. */
@RunWith(AndroidJUnit4::class)
class MiniHaloPhysicalTest {
    @get:Rule val compose = createComposeRule()

    @Test fun narrowTranslatedBusyScreenKeepsStopAndDisablesDuplicateSend() {
        var language by mutableStateOf("en")
        var scale by mutableFloatStateOf(1f)
        var height by mutableIntStateOf(480)
        var stopped = 0
        var sent = 0
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        fun text(key: String): String {
            val context = base.createConfigurationContext(Configuration(base.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(language))
            })
            return context.getString(uiCatalogue.getValue(key))
        }
        compose.setContent {
            key(language, scale) {
                val configuration = Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
                val localized = base.createConfigurationContext(configuration)
                CompositionLocalProvider(LocalContext provides localized, LocalConfiguration provides configuration,
                    LocalResources provides localized.resources) {
                    HaloTheme(darkTheme = false, fontScale = scale) {
                        Surface(Modifier.width(240.dp).height(height.dp)) {
                            MiniHaloContent(HaloSignalPresentation(reducedMotion = true), "Fixture draft", {}, true,
                                null, null, {}, { sent++ }, { stopped++ }, {}, {}, orbControl = { size ->
                                    com.halo.app.ui.presence.HaloOrbLauncher(HaloSignalPresentation(reducedMotion = true),
                                        com.halo.app.ui.presence.HaloOrbInteraction(com.halo.app.ui.presence.HaloOrbPrimary.STOP, true),
                                        com.halo.app.ui.presence.HaloOrbShortcut.defaults, {}, {}, {}, {}, size,
                                        destinations = com.halo.app.ui.shell.ShellOrbCatalog.destinations(false))
                                })
                        }
                    }
                }
            }
        }
        for (locale in listOf("en", "lv", "ru", "pl")) for (font in listOf(1f, 1.5f, 2f)) for (windowHeight in listOf(480, 320)) {
            compose.runOnIdle { language = locale; scale = font; height = windowHeight }
            compose.onNodeWithText(text("Stop")).assertIsDisplayed().assertHeightIsAtLeast(48.dp)
                .performTouchInput { click() }
            // Composer controls are pinned outside the reply scroller.
            compose.onNodeWithContentDescription(text("Send")).assertIsDisplayed().assertIsNotEnabled()
                .performTouchInput { click() }
            compose.runOnIdle { assertEquals(0, sent) }
        }
        assertEquals(24, stopped)
        assertEquals(0, sent)
    }

    @Test fun pendingReviewRoutesToActivityAndCannotLaunchCamera() {
        var destination: String? = null
        var actions = 0
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val localized = base.createConfigurationContext(Configuration(base.resources.configuration).apply { setLocale(Locale.ENGLISH) })
        compose.setContent { CompositionLocalProvider(LocalContext provides localized, LocalResources provides localized.resources) { HaloTheme(darkTheme = false, fontScale = 2f) {
            Surface(Modifier.width(240.dp).height(480.dp)) {
                MiniHaloContent(HaloSignalPresentation(reducedMotion = true), "", {}, false, null, null,
                    { actions++ }, { actions++ }, {}, { destination = it }, {}, reviewRequired = true)
            }
        } } }
        // Review is pinned below the reply scroller and must be directly reachable.
        compose.onNodeWithText("Review").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            .performTouchInput { click() }
        assertEquals("activity", destination)
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        compose.onNodeWithText("My tools").assertDoesNotExist()
        compose.onNodeWithText("Open camera").assertDoesNotExist()
        assertEquals(0, actions)
    }
}
