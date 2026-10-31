package com.halo.app.ui.shell

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.presence.*
import com.halo.app.ui.screens.notebook.ToolZoneHeader
import com.halo.app.ui.theme.HostlessHaloTheme
import com.halo.app.ui.zone5.Zone5InstrumentHeading
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-rUS-w393dp-h873dp")
class Zone5WorkingAreaTest {
    @get:Rule val compose = createComposeRule()
    private val calls = mutableListOf<String>()

    @Composable
    private fun face(size: androidx.compose.ui.unit.Dp) {
        HaloOrbLauncher(HaloSignalPresentation(label = "Ready", reducedMotion = true),
            HaloOrbInteraction(HaloOrbPrimary.TALK, false), emptySet(), {}, {}, {}, {}, size = size)
    }

    @Test fun documentIdentityAndMenusStayInsideTheQuarterHeightZone() {
        compose.setContent { HostlessHaloTheme {
            val band = remember { mutableStateOf<ShellUtilityProjection?>(null) }
            val actions = remember { mutableStateOf<ShellPageProjection?>(null) }
            val owner = ShellPageOwner("studio", "poster", "1")
            CompositionLocalProvider(LocalShellHaloOrb provides true, LocalShellUtilityBand provides band,
                LocalShellPageActions provides actions) {
                BindShellPageActions(owner, listOf(
                    ShellPageAction("page.studio.new", "New project", HaloGlyph.NOTE) { calls += "new" },
                    ShellPageAction("page.studio.add-layer", "Add layer", HaloGlyph.NOTE) { calls += "layer" }))
                BindShellUtilityBand(owner) { ToolZoneHeader("Field poster", {}) }
                Column(Modifier.width(393.dp).height(800.dp)) {
                    Box(Modifier.weight(1f).fillMaxWidth().testTag("work")) {
                        Zone5InstrumentHeading("HaloStudio", "tool_studio", "Field poster")
                        Text("Artboard content")
                    }
                    HaloZone("studio", 800.dp, false, emptyList(), {}, {}, 0,
                        utility = { band.value?.let { RenderShellUtilityBand(it) } }, orb = { face(it) }, backdrop = {})
                }
            }
        } }
        compose.onNodeWithTag("work").assertHeightIsEqualTo(600.dp)
        compose.onNodeWithTag("halo-zone5").assertHeightIsEqualTo(200.dp)
        compose.onNode(hasText("Field poster") and hasAnyAncestor(hasTestTag("halo-zone5"))).assertIsDisplayed()
        compose.onNode(hasText("Field poster") and hasAnyAncestor(hasTestTag("work"))).assertDoesNotExist()
        compose.onNode(hasText("File") and hasAnyAncestor(hasTestTag("halo-zone5"))).assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Back").assertCountEquals(0)
        compose.onNodeWithText("Add").performClick()
        assertTrue(calls.isEmpty())
        compose.onNodeWithText("Add layer").performClick()
        assertEquals(listOf("layer"), calls)
    }

    @Test fun compactTransitionRetainsTheFocusedComposerAndItsDraft() {
        val compact = mutableStateOf(false)
        compose.setContent { HostlessHaloTheme {
            Column(Modifier.width(393.dp).height(800.dp)) {
                Box(Modifier.weight(1f)) { Text("Conversation") }
                HaloZone("home", 800.dp, compact.value, emptyList(), {}, {}, 0, composer = true,
                    utility = {
                        var draft by rememberSaveable { mutableStateOf("Keep my draft") }
                        TextField(draft, { draft = it }, Modifier.fillMaxWidth().testTag("composer"))
                    }, orb = { face(it) }, backdrop = {})
            }
        } }
        compose.onNodeWithTag("composer").performClick().assertIsFocused()
        val regularHeight = compose.onNodeWithTag("halo-zone5").fetchSemanticsNode().boundsInRoot.height
        compose.runOnIdle { compact.value = true }
        compose.onNodeWithTag("composer").assertIsFocused().assertTextEquals("Keep my draft")
        assertTrue(compose.onNodeWithTag("halo-zone5").fetchSemanticsNode().boundsInRoot.height <= regularHeight * .65f)
        compose.onAllNodesWithContentDescription("Talk").assertCountEquals(1)
        compose.runOnIdle { compact.value = false }
        compose.onNodeWithTag("composer").assertIsFocused().assertTextEquals("Keep my draft")
        compose.onNodeWithTag("halo-zone5").assertHeightIsEqualTo(200.dp)
    }
}
