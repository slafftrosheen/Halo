package com.halo.app.ui.zone5

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.halo.app.R
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.presence.*
import com.halo.app.ui.theme.ActionPinSettings
import com.halo.app.ui.theme.HostlessHaloTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class, qualifiers = "en-rUS-w360dp-h800dp")
class Zone5PinnedActionsTest {
    @get:Rule val compose = createComposeRule()
    @Test fun english200() = render(2f)
    @Test @Config(qualifiers = "ru-rRU-w360dp-h800dp") fun russian200() = render(2f)
    @Test @Config(qualifiers = "lv-rLV-w360dp-h800dp") fun latvian200() = render(2f)
    @Test @Config(qualifiers = "pl-rPL-w360dp-h800dp") fun polish200() = render(2f)
    private fun render(scale: Float, kids: Boolean = false) {
        val holder = mutableStateOf<ShellPageProjection?>(null)
        var objectId by mutableStateOf("A")
        var pins by mutableStateOf(ActionPinSettings())
        var invoked: String? = null
        var actionLabel = ""; var choose = ""; var done = ""; var pinned = ""
        var queued: HaloOrbContextAction? = null
        compose.setContent { HostlessHaloTheme(fontScale = scale) {
            actionLabel = stringResource(R.string.zone5_all_actions); choose = stringResource(R.string.zone5_pins_choose)
            done = stringResource(R.string.zone5_pins_done); pinned = stringResource(R.string.zone5_pins_title)
            CompositionLocalProvider(LocalShellPageActions provides holder) {
                BindShellPageActions(ShellPageOwner("studio", objectId, sourceIdentity = "version-$objectId"), listOf(
                    ShellPageAction("page.studio.layers", "Fixture layers", HaloGlyph.NOTE) { invoked = objectId },
                    ShellPageAction("page.studio.undo", "Fixture undo", HaloGlyph.PREVIOUS, false) { error("Disabled") }))
                val projection = holder.value
                val actions = projection?.actions.orEmpty().map { HaloOrbContextAction("page:${it.id}", it.title, it.glyph,
                    enabled = it.enabled, disabledReason = "No earlier edit", pageLease = projection) }
                SideEffect { if (objectId == "A") queued = actions.firstOrNull() }
                HaloZone5OrbLauncher(HaloSignalPresentation(state = HaloSignalState.IDLE, label = "Ready", reducedMotion = true),
                    HaloOrbInteraction(HaloOrbPrimary.TALK, false), {}, {}, actions,
                    { it.invokePageAction(holder.value) { true } }, 48.dp, Modifier,
                    HaloZone5Navigation("studio", emptyList(), {}, false, {}, {}, entryId = "one", compact = true, kids = kids),
                    actionPins = pins, onActionPins = { family, expected, next ->
                        assertEquals(pins.families[family].orEmpty(), expected)
                        pins = pins.copy(families = pins.families + (family to next))
                    })
            }
        } }
        compose.onNodeWithText(actionLabel).performClick()
        if (kids) { compose.onNodeWithText(choose).assertDoesNotExist(); return }
        compose.onNodeWithText(choose).performScrollTo().performClick()
        compose.onNodeWithText("Fixture layers").performScrollTo().performClick()
        compose.waitForIdle()
        assertNull(invoked)
        assertEquals(listOf("page:page.studio.layers"), pins.families["studio"])
        compose.onNodeWithText(done).performScrollTo().performClick()
        compose.onNodeWithText(pinned).performScrollTo().assertIsDisplayed()
        compose.onRoot().captureRoboImage()
        val old = queued!!
        compose.runOnIdle { objectId = "B" }
        compose.runOnIdle { assertFalse(old.invokePageAction(holder.value) { true }) }
        compose.onAllNodesWithText("Fixture layers").onFirst().performScrollTo().performClick()
        assertEquals("B", invoked)
    }
    @Test fun childCannotCustomizeAdultPinsEvenWhenCallbackIsPresent() = render(1f, kids = true)
    @Test fun localizedAliasesCoverActualCompoundActionsAndOnlyGivenCommands() = checkAliases()
    @Test @Config(qualifiers = "ru-rRU-w360dp-h800dp") fun russianAliases() = checkAliases()
    @Test @Config(qualifiers = "lv-rLV-w360dp-h800dp") fun latvianAliases() = checkAliases()
    @Test @Config(qualifiers = "pl-rPL-w360dp-h800dp") fun polishAliases() = checkAliases()
    private fun checkAliases() {
        var matches: List<String> = emptyList()
        compose.setContent { HostlessHaloTheme {
            val aliases = stringResource(R.string.zone5_search_export).split(" ").last()
            matches = listOf(
                HaloOrbContextAction("page:page.studio.export-jpeg", "JPEG result", HaloGlyph.FILE),
                HaloOrbContextAction("page:page.studio.export", "PNG result", HaloGlyph.FILE),
                HaloOrbContextAction("page:page.studio.discard", "Remove draft", HaloGlyph.TRASH)
            ).filter { actionMatchesQuery(it, aliases) }.map { it.id }
            assertTrue(actionMatchesQuery(HaloOrbContextAction("page:page.studio.add-layer", "Layer chooser", HaloGlyph.NOTE), stringResource(R.string.zone5_search_insert).split(" ").first()))
            assertTrue(actionMatchesQuery(HaloOrbContextAction("page:page.study.save-notes", "Keep study", HaloGlyph.NOTE), stringResource(R.string.zone5_search_keep).split(" ").first()))
            Text("Scoped alias checks")
        } }
        compose.runOnIdle { assertEquals(listOf("page:page.studio.export-jpeg", "page:page.studio.export"), matches) }
    }
    @Test fun childCanvasButtonAlternativesKeep56dpTargetsAtLargeText() {
        compose.setContent { HostlessHaloTheme(fontScale = 2f) {
            CompositionLocalProvider(LocalHaloZone5Navigation provides HaloZone5Navigation("notebook/canvas/child", emptyList(), {}, false, {}, {}, kids = true)) {
                com.halo.app.ui.screens.notebook.CanvasNavigationDialog(
                    com.halo.app.ui.screens.notebook.CanvasEditorUiState(loading = false), { _, _ -> }, {}, { _, _ -> }, {}, {}, {}, kidsMode = true)
            }
        } }
        compose.onAllNodesWithText("Left").onFirst().performScrollTo().assertHeightIsAtLeast(56.dp)
        compose.onNodeWithText("Zoom in").performScrollTo().assertHeightIsAtLeast(56.dp)
        compose.onNodeWithText("Small steps").performScrollTo().assertHeightIsAtLeast(56.dp)
        compose.onAllNodesWithText("Right").onLast().performScrollTo().assertHeightIsAtLeast(56.dp)
        compose.onNodeWithText("Done").assertHeightIsAtLeast(56.dp)
        compose.onNode(isDialog()).captureRoboImage()
    }
    @Test fun requestedHelpRemainsReachableWhenOnlyBackIsAvailable() {
        var actionsLabel = ""; var helpLabel = ""
        val holder = mutableStateOf<ShellPageProjection?>(null)
        compose.setContent { HostlessHaloTheme {
            actionsLabel = stringResource(R.string.zone5_all_actions); helpLabel = stringResource(R.string.zone5_help_title)
            CompositionLocalProvider(LocalShellPageActions provides holder) {
                BindShellPageActions(ShellPageOwner("settings", helpDescription = "Current appearance preferences"),
                    listOf(ShellPageAction("page.settings.back", "Back", HaloGlyph.PREVIOUS) {}))
                HaloZone5OrbLauncher(HaloSignalPresentation(reducedMotion = true), HaloOrbInteraction(HaloOrbPrimary.TALK, false), {}, {},
                    listOf(HaloOrbContextAction("page:page.settings.back", "Back", HaloGlyph.PREVIOUS)), {}, 48.dp, Modifier,
                    HaloZone5Navigation("settings", emptyList(), {}, false, {}, {}, compact = true))
            }
        } }
        compose.onNodeWithText(actionsLabel).performClick()
        compose.onNodeWithText(helpLabel).performScrollTo().performClick()
        compose.onNodeWithText("Current appearance preferences").assertIsDisplayed()
    }
}
