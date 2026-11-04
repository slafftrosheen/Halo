package com.halo.app.ui.zone5

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.presence.*
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
    @Test fun studioLongDirtyRussianIdentityKeepsAllFourPrimaryGroupsVisible() {
        val invoked = mutableListOf<String>()
        val labels = listOf("Добавить", "Слои", "Инструменты", "ИИ")
        val ids = listOf("page.studio.add-layer", "page.studio.layers", "page.studio.tools", "page.studio.ai")
        val busy = mutableStateOf(false)
        compose.setContent { HostlessHaloTheme {
            val projection = remember { mutableStateOf<ShellPageProjection?>(null) }
            val header = remember { mutableStateOf<ShellUtilityProjection?>(null) }
            CompositionLocalProvider(LocalShellHaloOrb provides true, LocalShellPageActions provides projection,
                LocalShellUtilityBand provides header) {
                val owner = ShellPageOwner("studio", objectId = "phone-regression",
                    statusLabel = if (busy.value) "Сохранение" else "Сохранено",
                    defaultDisabledActionReason = if (busy.value) "Завершаем сохранение рисунка перед изменением проекта" else null)
                BindShellPageActions(owner, ids.zip(labels).map { (id, label) ->
                    ShellPageAction(id, label, HaloGlyph.TOOL, !busy.value) { invoked += id }
                })
                BindShellUtilityBand(owner, includesStatus = true) {
                    ToolZoneHeader("Длинное название сохраняемого творческого проекта", {},
                        subtitle = "Черновик сохранён на этом телефоне · 1440 × 2048 пикс. · Слои: 5",
                        compactIdentity = true) {
                        TextButton({}) { Text("Рисование") }
                        IconButton({}) { Zone5Icon("action_save", HaloGlyph.NOTE, Modifier.size(24.dp)) }
                        IconButton({}) { Zone5Icon("action_more", HaloGlyph.TOOL, Modifier.size(24.dp)) }
                    }
                }
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f))
                    HaloZone("studio", 760.dp, false, emptyList(), {}, {}, 0,
                        utility = { header.value?.let { RenderShellUtilityBand(it) } },
                        orb = { size -> HaloOrbLauncher(HaloSignalPresentation(label = "Ready", reducedMotion = true),
                            HaloOrbInteraction(HaloOrbPrimary.TALK, false), emptySet(), {}, {}, {}, {}, size = size) })
                }
            }
        } }
        // The native regression appeared only after the busy reason enlarged the menu;
        // an initially saved/dirty screenshot alone could not catch the sticky overflow.
        compose.runOnIdle { busy.value = true }
        compose.waitForIdle()
        compose.runOnIdle { busy.value = false }
        compose.waitForIdle()
        compose.onNodeWithTag("zone5-command-groups").assertIsDisplayed()
        labels.forEach { label ->
            val node = compose.onNodeWithText(label)
            node.assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            val layouts = mutableListOf<TextLayoutResult>()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue("Missing text layout for $label", layouts.isNotEmpty())
            assertFalse("Primary group label is clipped: $label", layouts.single().hasVisualOverflow)
            node.performClick()
        }
        assertEquals(ids, invoked)
    }
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

    @Test fun studioPrimaryLabelsWrapWithoutClippingAt200Percent() {
        val labels = listOf("Добавить", "Слои", "Инструменты", "ИИ")
        val ids = listOf("page.studio.add-layer", "page.studio.layers", "page.studio.tools", "page.studio.ai")
        compose.setContent { HostlessHaloTheme(fontScale = 2f) {
            val projection = remember { mutableStateOf<ShellPageProjection?>(null) }
            CompositionLocalProvider(LocalShellPageActions provides projection) {
                BindShellPageActions(ShellPageOwner("studio"), ids.zip(labels).map { (id, label) ->
                    ShellPageAction(id, label, HaloGlyph.TOOL) {}
                })
                Box(Modifier.width(320.dp)) { WorkspaceToolMenus() }
            }
        } }
        labels.forEach { label ->
            val node = compose.onNodeWithText(label).assertIsDisplayed()
            val layouts = mutableListOf<TextLayoutResult>()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertFalse("Large primary label is clipped: $label", layouts.single().hasVisualOverflow)
        }
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
