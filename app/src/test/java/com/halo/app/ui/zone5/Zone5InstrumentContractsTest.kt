package com.halo.app.ui.zone5

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.presence.HaloOrbContextAction
import com.halo.app.ui.presence.HaloOrbLauncher
import com.halo.app.ui.presence.HaloOrbInteraction
import com.halo.app.ui.presence.HaloOrbPrimary
import com.halo.app.ui.presence.HaloSignalPresentation
import com.halo.app.ui.shell.HaloZone
import com.halo.app.ui.screens.kids.KidsBrandTheme
import com.halo.app.ui.screens.notebook.ToolZoneHeader
import com.halo.app.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.github.takahirom.roborazzi.captureRoboImage

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h800dp")
class Zone5InstrumentContractsTest {
    @get:Rule val compose = createComposeRule()
    @Test fun disabledStudioPrimaryGroupExplainsItsLiveOwnerReason() {
        compose.setContent {HostlessHaloTheme {
            val projection=remember {mutableStateOf<com.halo.app.ui.presence.ShellPageProjection?>(null)}
            CompositionLocalProvider(com.halo.app.ui.presence.LocalShellPageActions provides projection) {
                com.halo.app.ui.presence.BindShellPageActions(com.halo.app.ui.presence.ShellPageOwner("studio",activeToolId="page.studio.tools",defaultDisabledActionReason="Finishing paint"),
                    listOf(com.halo.app.ui.presence.ShellPageAction("page.studio.tools","Tools",HaloGlyph.TOOL,false) {}))
                WorkspaceToolMenus()
            }
        }}
        compose.onNodeWithText("Tools").assertIsNotEnabled().assert(androidx.compose.ui.test.SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription,"Finishing paint"))
        compose.onNodeWithText("Finishing paint").assertIsDisplayed()
    }

    @Test fun instrumentBackIsVisibleAndInvokesTheExistingOwner() {
        var back = 0
        compose.setContent { HostlessHaloTheme { ToolZoneHeader("Light and sound", { back++ }) } }
        compose.onNodeWithText("Back").assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, back)
    }

    @Test fun largeTextUtilityIdentityWrapsInsteadOfLosingTheDocumentName() {
        compose.setContent { HostlessHaloTheme(fontScale = 2f) {
            Box(Modifier.width(320.dp)) { ToolZoneHeader("Light and sound notes", {}) }
        } }
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("Light and sound notes").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(1, layouts.size)
        compose.onRoot().captureRoboImage()
        assertFalse("The visible document identity is ellipsized", layouts.single().hasVisualOverflow)
    }

    private fun icons(vararg expected: Pair<String, String>) = expected.forEach { (action, icon) ->
        assertEquals(action, icon, zone5ActionIconId(HaloOrbContextAction("page:$action", action, HaloGlyph.NOTE)))
        assertNotNull("Missing supplied native asset $icon", HaloZone5Icons.resource(icon))
    }
    @Test fun notesUseInsertionOutlineAndFindAssets() = icons(
        "page.note.insert" to "action_add", "page.note.outline" to "office_outline", "page.note.find" to "office_find_replace")
    @Test fun officeUsesDocumentOpenAndExportAssets() = icons(
        "page.office.new" to "office_document", "page.office.open" to "library_open", "page.office.export" to "action_export")
    @Test fun canvasUsesAddArrangeAndFitAssets() = icons(
        "page.canvas.add" to "action_add", "page.canvas.arrange" to "screen_arrange", "page.canvas.fit" to "canvas_fit")
    @Test fun studioUsesLayerAndExportAssets() = icons(
        "page.studio.add-layer" to "studio_layer_add", "page.studio.layers" to "studio_layers", "page.studio.export" to "action_export")
    @Test fun studioFourPrimaryGroupsHaveDistinctMeaningfulAssets() {
        val ids=listOf("page.studio.add-layer","page.studio.layers","page.studio.tools","page.studio.ai")
        val assets=ids.map {zone5ActionIconId(HaloOrbContextAction("page:$it",it,HaloGlyph.TOOL))}
        assertEquals(4,assets.distinct().size)
        assets.forEach {assertNotNull(HaloZone5Icons.resource(it))}
        val actions=ids.map {HaloOrbContextAction("page:$it",it,HaloGlyph.TOOL)}+HaloOrbContextAction("page:page.studio.export","Export",HaloGlyph.FILE)
        assertEquals(ids,zone5InstrumentOrbitActions(actions).map {it.id.removePrefix("page:")})
    }
    @Test fun studyUsesLessonPracticeAndNarrationAssets() = icons(
        "page.study.new" to "study_lesson", "page.study.practice" to "study_practice", "page.study.read" to "action_read_aloud")
    @Test fun memoryDetailUsesSourceEditAndForgetAssets() = icons(
        "object.memory.source" to "study_source", "object.memory.edit" to "office_edit_text", "object.memory.forget" to "memory_forget")

    @Test fun memoryHasOneDefaultSatelliteWhileSettingsRemainInAllActions() {
        val actions = listOf(HaloOrbContextAction("page:page.memory.add", "Remember something", HaloGlyph.MEMORY),
            HaloOrbContextAction("page:page.memory.settings", "Learning settings", HaloGlyph.SETTINGS))
        var settings = 0
        compose.setContent { HostlessHaloTheme {
            HaloZone("notebook/memory", 800.dp, false, actions, {}, {}, 0, utility = {},
                orb = { size -> HaloOrbLauncher(HaloSignalPresentation(label = "Ready", reducedMotion = true),
                    HaloOrbInteraction(HaloOrbPrimary.TALK, false), emptySet(), {}, {}, {}, {}, size = size,
                    contextActions = actions, onContextAction = { if (it.id == actions.last().id) settings++ }) })
        } }
        compose.onNodeWithText("Learning settings").assertDoesNotExist()
        compose.onNodeWithText("All actions").performClick()
        compose.onNodeWithText("Learning settings").performClick()
        assertEquals(1, settings)
    }

    @Test fun childAppearanceKeepsTheCurrentPaletteAcrossThemeChanges() {
        val palette = mutableStateOf(HaloPalette(HaloAccent.BLUE, HaloTone.COOL))
        var expected = androidx.compose.ui.graphics.Color.Unspecified
        var actual = androidx.compose.ui.graphics.Color.Unspecified
        compose.setContent { HostlessHaloTheme(palette = palette.value) {
            expected = MaterialTheme.colorScheme.primary
            KidsBrandTheme { actual = MaterialTheme.colorScheme.primary }
        } }
        compose.runOnIdle { assertEquals(expected, actual); palette.value = HaloPalette(HaloAccent.ROSE, HaloTone.WARM) }
        compose.runOnIdle { assertEquals(expected, actual) }
    }
}
