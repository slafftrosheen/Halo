package com.halo.app.ui.presence

import androidx.compose.foundation.layout.*
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.halo.app.language.AppLanguagePreferences
import com.halo.app.language.AppLanguageProvider
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.theme.HaloTheme
import com.halo.sdk.runtime.Storage
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Actual Android rendering/input of the production launcher in an isolated fixture.
 * No AppShell, speech host, provider, mode change or persisted preference mutation.
 * The privacy button is a callback-only leading-slot fixture, not the durable control.
 */
@RunWith(AndroidJUnit4::class)
class InterfaceFourDeviceTest {
    @get:Rule val compose = createComposeRule()

    private class Calls {
        val primary = mutableListOf<HaloOrbPrimary>()
        val routes = mutableListOf<String>()
        var stopped = 0
        var tools = 0
        var privacy = 0
        var customization = 0
        fun total() = primary.size + routes.size + stopped + tools + privacy + customization
    }

    private fun render(calls: Calls, fontScale: Float = 1f, dark: Boolean = false,
        owner: LifecycleOwner? = null) {
        val language = AppLanguagePreferences(MemoryStorage(), "en")
        compose.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                AppLanguageProvider(language) {
                    HaloTheme(darkTheme = dark, fontScale = fontScale) {
                        val content: @Composable () -> Unit = {
                            Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                                HaloOrbLauncher(
                                    signal = HaloSignalPresentation(label = "Ready", reducedMotion = true),
                                    interaction = HaloOrbInteraction(HaloOrbPrimary.TALK, false),
                                    shortcuts = emptySet(), onShortcuts = { calls.customization++ },
                                    onPrimary = { calls.primary += it }, onStop = { calls.stopped++ },
                                    onTools = { calls.tools++ }, size = 80.dp,
                                    destinations = listOf(
                                        HaloOrbDestination("studio", "Workspace", HaloGlyph.NOTE),
                                        HaloOrbDestination("privacy", "Privacy", HaloGlyph.SECURITY),
                                        HaloOrbDestination("settings", "Settings", HaloGlyph.TOOL)),
                                    onNavigate = { calls.routes += it },
                                    leading = {
                                        OutlinedButton(onClick = { calls.privacy++ },
                                            modifier = Modifier.heightIn(min = 48.dp)
                                                .semantics { contentDescription = "Fixture privacy mode" },
                                            contentPadding = PaddingValues(horizontal = 8.dp)) {
                                            Text("Private")
                                        }
                                    })
                            }
                        }
                        if (owner == null) content()
                        else CompositionLocalProvider(LocalLifecycleOwner provides owner) { content() }
                    }
                }
            }
        }
        // Native Activity/window attachment can finish after setContent returns.
        // Wait for this fixture's actual root before injecting a gesture; a
        // missing hierarchy must still fail within the bounded readiness gate.
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription("Talk")
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        compose.onNodeWithContentDescription("Talk").assertIsDisplayed()
    }

    @Test fun nativeTapInvokesExactlyOnePrimaryWithoutOpeningMenu() {
        val calls = Calls()
        render(calls)
        compose.onNodeWithContentDescription("Talk").performTouchInput { click() }
        compose.onNodeWithText("All tools").assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf(HaloOrbPrimary.TALK), calls.primary); assertEquals(1, calls.total()) }
    }

    @Test fun nativeLongPressOpensDestinationListWithoutCapturing() {
        val calls = Calls()
        render(calls, dark = true)
        compose.onNodeWithContentDescription("Talk").performTouchInput { longClick() }
        compose.onNodeWithText("Workspace").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(listOf("studio"), calls.routes); assertEquals(1, calls.total()) }
        compose.onNodeWithText("All tools").assertDoesNotExist()
    }

    @Test fun nativeSwipeUpOpensTheListWithoutInvokingPrimary() {
        val calls = Calls()
        render(calls)
        compose.onNodeWithContentDescription("Talk").performTouchInput { swipeUp() }
        compose.onNodeWithText("All tools").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, calls.total()) }
    }

    @Test fun customAccessibilityActionOpensTheSameListWithoutSideEffects() {
        val calls = Calls()
        render(calls)
        val actions = compose.onNodeWithContentDescription("Talk").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        val menu = actions.single { it.label == "All destinations and actions" }
        compose.runOnIdle { assertTrue(menu.action()) }
        compose.onNodeWithText("All tools").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, calls.tools); assertEquals(1, calls.total()) }
    }

    @Test fun twoHundredPercentListAndPrivacyTargetsStaySeparate() {
        val calls = Calls()
        render(calls, fontScale = 2f, dark = true)
        val orb = compose.onNodeWithContentDescription("Talk")
        val privacy = compose.onNodeWithContentDescription("Fixture privacy mode")
        val menu = compose.onNodeWithContentDescription("Show Halo shortcuts")
        orb.assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        privacy.assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        menu.assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        val orbBounds = orb.fetchSemanticsNode().boundsInRoot
        val privacyBounds = privacy.fetchSemanticsNode().boundsInRoot
        val menuBounds = menu.fetchSemanticsNode().boundsInRoot
        assertFalse("Privacy overlaps the orb", orbBounds.overlaps(privacyBounds))
        assertFalse("Menu overlaps the orb", orbBounds.overlaps(menuBounds))
        privacy.performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, calls.privacy); assertEquals(1, calls.total()) }
        menu.performTouchInput { click() }
        compose.onNodeWithText("Privacy").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(listOf("privacy"), calls.routes)
            assertEquals(1, calls.privacy)
            assertTrue(calls.primary.isEmpty())
            assertEquals(2, calls.total())
        }
    }

    @Test fun stoppedSurfaceDismissesMenuAndResumeDoesNotReplayAnyCallback() {
        val calls = Calls()
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry.createUnsafe(this)
            override val lifecycle: Lifecycle get() = registry
        }
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        render(calls, owner = owner)
        compose.onNodeWithContentDescription("Show Halo shortcuts").performClick()
        compose.onNodeWithText("All tools").assertExists()
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
        compose.onNodeWithText("All tools").assertDoesNotExist()
        // A stale accessibility click below STARTED must not start capture.
        compose.onNodeWithContentDescription("Talk").performClick()
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.onNodeWithText("All tools").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, calls.total()) }
        compose.onNodeWithContentDescription("Talk").performClick()
        compose.runOnIdle { assertEquals(listOf(HaloOrbPrimary.TALK), calls.primary); assertEquals(1, calls.total()) }
    }

    /** Language override is local to this fixture, never the user's saved choice. */
    private class MemoryStorage : Storage {
        private val values = mutableMapOf<String, Any>()
        override fun getString(key: String, defaultValue: String?) = values[key] as? String ?: defaultValue
        override fun putString(key: String, value: String?) { if (value == null) values.remove(key) else values[key] = value }
        override fun getInt(key: String, defaultValue: Int) = values[key] as? Int ?: defaultValue
        override fun putInt(key: String, value: Int) { values[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean) = values[key] as? Boolean ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
        override fun clear() { values.clear() }
    }
}
