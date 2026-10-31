package com.halo.app.ui.screens.kids

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.halo.app.ui.presence.*
import com.halo.app.ui.shell.HaloZone
import com.halo.app.ui.theme.*
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android renderer with synthetic child content. Never enables Kids or changes app preferences. */
@RunWith(AndroidJUnit4::class)
class Zone5KidsFrameDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun childFrameThemesCompactActionsBackAndParentBoundary() {
        val accent = mutableStateOf(HaloAccent.BLUE)
        val dark = mutableStateOf(true)
        val scale = mutableStateOf(1f)
        val compact = mutableStateOf(false)
        val destination = mutableStateOf(KidsDestination.HOME)
        val input = mutableStateOf(false)
        var typed = 0; var helped = 0; var stopped = 0; var verified = 0
        var imeVisible = false
        var primary = Color.Unspecified
        lateinit var host: androidx.activity.ComponentActivity
        compose.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                HaloTheme(darkTheme = dark.value, fontScale = scale.value, palette = HaloPalette(accent.value)) {
                    val ink = MaterialTheme.colorScheme.primary
                    SideEffect { primary = ink }
                    host = LocalContext.current as androidx.activity.ComponentActivity
                    val page = remember { mutableStateOf<ShellPageProjection?>(null) }
                    val band = remember { mutableStateOf<ShellUtilityProjection?>(null) }
                    CompositionLocalProvider(LocalShellHaloOrb provides true, LocalShellPageActions provides page,
                        LocalShellUtilityBand provides band) {
                        KidsBrandTheme {
                            val gate = rememberKidsGrownUpGate({ verified++ }, {})
                            KidsZoneBinding(destination.value, null, "Talk or type", false, input.value,
                                { typed++; destination.value = KidsDestination.LEARN; input.value = true },
                                { helped++ }, { stopped++ }, gate)
                            KidsSystemBack(destination.value, input.value, { stopped++ },
                                { input.value = false }, { destination.value = KidsDestination.HOME })
                        }
                        val actions = page.value?.actions.orEmpty().map {
                            HaloOrbContextAction("page:${it.id}", it.title, it.glyph, enabled = it.enabled)
                        }
                        val invoke: (HaloOrbContextAction) -> Unit = { page.value?.invoke(it.id.removePrefix("page:")) }
                        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                            BoxWithConstraints {
                                val keyboard = WindowInsets.ime.getBottom(LocalDensity.current) > 0
                                SideEffect { imeVisible = keyboard }
                                Column(Modifier.fillMaxSize().imePadding()) {
                                    Column(Modifier.weight(1f).fillMaxWidth().testTag("kids-work").padding(16.dp)) {
                                        KidsHomeHero(HaloSignalPresentation(), {}, {}, {}, true)
                                        KidsActivityHeader(destination.value, HaloSignalPresentation(), null, {}, {})
                                        KidsVoiceDock("Talk or type", false, false, {}, {}, {}, {})
                                        KidsBrandTheme {
                                            if (input.value) {
                                                var draft by remember { mutableStateOf("") }
                                                TextField(draft, { draft = it }, Modifier.fillMaxWidth().testTag("child-input"), label = { Text("My thinking") })
                                            } else KidsHomeChoices(onSelect = { destination.value = it })
                                        }
                                    }
                                    HaloZone("home", maxHeight, compact.value || keyboard, actions, invoke, {}, 0,
                                        utility = { band.value?.let { RenderShellUtilityBand(it) } },
                                        orb = { size -> HaloOrbLauncher(HaloSignalPresentation(reducedMotion = true),
                                            HaloOrbInteraction(HaloOrbPrimary.TALK, false), emptySet(), {}, {}, {}, {}, size = size,
                                            contextActions = actions, onContextAction = invoke) },
                                        profile = {}, kids = true)
                                }
                            }
                        }
                    }
                }
            }
        }
        for ((value, night) in listOf(HaloAccent.BLUE to true, HaloAccent.MINT to true, HaloAccent.ROSE to false)) {
            compose.runOnIdle { accent.value = value; dark.value = night }
            val work = compose.onNodeWithTag("kids-work").fetchSemanticsNode().boundsInRoot
            val zone = compose.onNodeWithTag("halo-zone5").fetchSemanticsNode().boundsInRoot
            assertEquals(.25f, zone.height / (zone.height + work.height), .002f)
            compose.onNodeWithText("Back to choices").assertDoesNotExist()
            compose.onNode(hasText("Type instead") and hasAnyAncestor(hasTestTag("kids-work"))).assertDoesNotExist()
            compose.onAllNodesWithContentDescription("Talk").assertCountEquals(1)
            compose.onNodeWithContentDescription("Talk").assertHeightIsAtLeast(56.dp)
            compose.onNodeWithText("What can I say?").assertHeightIsAtLeast(56.dp)
            val pixels = compose.onNodeWithContentDescription("Talk").captureToImage().toPixelMap()
            var painted = 0
            for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                val c = pixels[x,y]
                if (kotlin.math.abs(c.red-primary.red)<.025f && kotlin.math.abs(c.green-primary.green)<.025f && kotlin.math.abs(c.blue-primary.blue)<.025f) painted++
            }
            assertTrue("Child orb must repaint with $value", painted > 40)
            save("kids-${value.name.lowercase()}")
        }
        compose.onNodeWithText("Type instead").performClick()
        compose.onNodeWithTag("child-input").assertExists()
        compose.runOnIdle { host.onBackPressedDispatcher.onBackPressed() }
        compose.runOnIdle { assertFalse(input.value); assertEquals(KidsDestination.LEARN, destination.value) }
        compose.runOnIdle { host.onBackPressedDispatcher.onBackPressed() }
        compose.runOnIdle { assertEquals(KidsDestination.HOME, destination.value); assertEquals(2, stopped) }
        compose.onNodeWithText("All actions").performClick()
        compose.onNode(hasText("Grown-up space") and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText("Ask a grown-up").assertIsDisplayed()
        assertEquals(0, verified)
        save("kids-parent-boundary")
        compose.onNodeWithText("Go back").performClick()
        compose.runOnIdle { compact.value = true; scale.value = 2f }
        compose.onNodeWithText("All actions").assertIsDisplayed().assertHeightIsAtLeast(56.dp).performClick()
        compose.onNode(hasText("What can I say?") and hasClickAction()).performScrollTo().assertHeightIsAtLeast(56.dp).performClick()
        assertEquals(1, helped)
        compose.onNodeWithText("All actions").performClick()
        compose.onNode(hasText("Type instead") and hasClickAction()).performScrollTo().performClick()
        assertEquals(2, typed)
        compose.onNodeWithTag("child-input").assertIsDisplayed()
        save("kids-compact-200")
        compose.runOnIdle { compact.value = false; scale.value = 1f }
        compose.onNodeWithTag("child-input").performClick().performTextInput("Synthetic child draft")
        compose.waitUntil(8_000) { imeVisible }
        compose.onNodeWithContentDescription("Talk").assertIsDisplayed().assertHeightIsAtLeast(56.dp)
        compose.onNodeWithText("Menu").assertIsDisplayed()
        save("kids-keyboard")
        androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
    }

    private fun save(name: String) {
        val output = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "zone5-kids-polish").apply { mkdirs() }
        File(output, "$name.png").outputStream().use {
            (if (name == "kids-parent-boundary") compose.onNode(isRoot() and hasAnyDescendant(hasText("Ask a grown-up"))) else compose.onRoot()).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
