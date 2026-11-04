package com.halo.app.ui.everyday

import android.app.Application
import android.os.Bundle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.halo.app.everyday.briefing.BriefSettings
import com.halo.app.everyday.organizer.*
import com.halo.app.language.uiText
import com.halo.app.ui.theme.HostlessHaloTheme
import com.halo.app.everyday.presentation.*
import com.halo.app.nativeactions.EverydayWidgetConfigurationForm
import com.halo.app.nativeactions.EverydayWidgetInstance
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "en-rUS-w360dp-h800dp")
class EverydayFormsRenderingTest {
    @get:Rule val compose = createComposeRule()
    @Test @Config(qualifiers = "ru-rRU-w360dp-h800dp") fun widgetDashboardOrderIsReachableAt200PercentAndSurvivesRestoration() {
        val restoration=StateRestorationTester(compose)
        val instance=EverydayWidgetInstance(81,3,EverydayCardConfig(EverydayCardKind.DASHBOARD,
            sections=listOf(EverydayCardKind.TASKS,EverydayCardKind.WEATHER,EverydayCardKind.BRIEF)))
        val snapshot=EverydaySnapshot(Instant.parse("2026-11-03T12:00:00Z"),ZoneOffset.UTC,emptyList(),emptyList(),emptyList(),emptyList())
        var move="";var apply="";var result: EverydayCardConfig?=null
        restoration.setContent {HostlessHaloTheme(darkTheme=true,fontScale=2f) {
            move=uiText("Move down")+" · "+uiText(EverydayCards.title(EverydayCardKind.TASKS))
            apply=uiText("Apply to this widget")
            androidx.compose.material3.Surface(Modifier.fillMaxSize()) {EverydayWidgetConfigurationForm(instance,snapshot,false,null,{}, {result=it}, {})}
        }}
        compose.onNodeWithText(move).performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText(apply).performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle {assertEquals(listOf(EverydayCardKind.WEATHER,EverydayCardKind.TASKS,EverydayCardKind.BRIEF),result!!.sections)}
        compose.onRoot().captureRoboImage()
    }
    @Test fun editorKeepsDraftAfterDiscardIsDeclinedAndRestoration() = editor(1f, false, true)
    @Test @Config(qualifiers = "ru-rRU-w360dp-h800dp") fun russianEditorAt200Percent() = editor(2f, true)
    @Test @Config(qualifiers = "lv-rLV-w360dp-h800dp") fun latvianEditorAt200Percent() = editor(2f, false)
    @Test @Config(qualifiers = "pl-rPL-w360dp-h800dp") fun polishEditorAt200Percent() = editor(2f, true)

    private fun editor(scale: Float, dark: Boolean, restore: Boolean = false) {
        val original = OrganizerDraft(OrganizerRecord("fixture", OrganizerKind.EVENT, "Reviewed calendar event",
            time = OrganizerTime(LocalDateTime.parse("2026-11-03T09:00"), LocalDateTime.parse("2026-11-03T10:00"), "Europe/Riga"),
            createdAt = Instant.parse("2026-11-01T12:00:00Z")), false)
        val restoration = StateRestorationTester(compose)
        var title = ""; var cancel = ""; var keep = ""; var save = ""; var edit = ""
        restoration.setContent { HostlessHaloTheme(darkTheme = dark, fontScale = scale) {
            var draft by rememberSaveable(stateSaver = Saver<OrganizerDraft, Bundle>({ it.bundle() }, { OrganizerDraft.restore(it) })) { mutableStateOf(original) }
            var shown by rememberSaveable { mutableStateOf(true) }
            title = uiText("Title"); cancel = uiText("Cancel"); keep = uiText("Keep editing"); save = uiText("Save"); edit = uiText("Edit item")
            if (shown) OrganizerEditorForm(OrganizerPageState(loading = false, kids = false), draft,
                onDraft = { if (it == null) shown = false else draft = it }, onSave = {})
        } }
        compose.onNodeWithText(title).performScrollTo().performTextReplacement("Unsaved local edit")
        if (restore) restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Unsaved local edit").assertExists()
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithText(edit).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(scale, layouts.single().layoutInput.density.fontScale, .001f)
        compose.onAllNodes(isRoot()).onLast().captureRoboImage()
        compose.onNodeWithText(cancel).assertIsDisplayed().performClick()
        compose.onNodeWithText(keep).performClick()
        compose.onNodeWithText(save).assertIsDisplayed()
        compose.onNodeWithText("Unsaved local edit").assertExists()
    }

    @Test fun longOrganizerFormKeepsSaveAndCancelVisibleWithoutScrolling() = longEditor(1f)
    @Test @Config(qualifiers = "ru-rRU-w640dp-h360dp") fun shortLandscapeOrganizerKeepsDecisionsVisibleAt200Percent() = longEditor(2f)
    private fun longEditor(scale:Float) {
        val original=OrganizerDraft(OrganizerRecord("fixture",OrganizerKind.TIMER,"Timer with many related items",createdAt=Instant.EPOCH),true)
        val choices=List(90) {OrganizerLinkChoice("link-$it","Saved activity $it","ACTIVITY")}
        var save="";var cancel="";var title="";var saved:OrganizerDraft?=null
        compose.setContent {HostlessHaloTheme(fontScale=scale) {
            var draft by remember {mutableStateOf(original)}
            save=uiText("Save");cancel=uiText("Cancel");title=uiText("Title")
            OrganizerEditorForm(OrganizerPageState(loading=false,kids=false,linkChoices=choices),draft,
                {if(it!=null) draft=it},{saved=draft})
        }}
        compose.onNodeWithText(save).assertIsDisplayed().assertHeightIsAtLeast(56.dp)
        compose.onNodeWithText(cancel).assertIsDisplayed().assertHeightIsAtLeast(56.dp)
        compose.onNodeWithText(title).performScrollTo().performTextReplacement("Reviewed timer")
        compose.onNodeWithText("Saved activity 89").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(save).assertIsDisplayed().performClick()
        compose.runOnIdle {assertEquals("Reviewed timer",saved!!.title);assertTrue(saved!!.links.isEmpty())}
        compose.onNodeWithText(cancel).assertIsDisplayed()
    }

    @Test fun parentCanRevokeUnavailableAndLateSharedRecordsWithoutLosingOtherSettings() {
        val records = (0..150).map { OrganizerRecord("record-$it", OrganizerKind.TASK, "Item $it", createdAt = Instant.EPOCH) }
        val saved = BriefSettings(childRecordIds = setOf("record-150", "missing-record"), pinnedRecordIds = setOf("record-149"))
        var result: BriefSettings? = null
        compose.setContent { HostlessHaloTheme(fontScale = 2f) {
            BriefSettingsSheet(saved, records, false, null, null, {}, { result = it }, {})
        } }
        compose.onNodeWithContentDescription("Share with Kids · missing-record").performScrollTo().assertIsOn().performClick()
        compose.onNodeWithContentDescription("Share with Kids · Item 150").performScrollTo().assertIsOn().performClick()
        compose.onNodeWithText("Save").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(result!!.childRecordIds.isEmpty()); assertEquals(saved.pinnedRecordIds, result!!.pinnedRecordIds) }
    }

    @Test @Config(qualifiers = "lv-rLV-w360dp-h800dp") fun childDateInputKeeps56DpActionsAt200Percent() {
        var choose = ""; var apply = ""; var cancel = ""
        compose.setContent { HostlessHaloTheme(fontScale = 2f) {
            choose = uiText("Choose date"); apply = uiText("Apply"); cancel = uiText("Cancel")
            Column { EverydayDateField("Datums", "9999-11-03", child = true) {} }
        } }
        compose.onNodeWithText(choose).performClick()
        compose.onNodeWithText(apply).assertIsDisplayed().assertHeightIsAtLeast(56.dp)
        compose.onNodeWithText(cancel).assertIsDisplayed().assertHeightIsAtLeast(56.dp)
        compose.onAllNodes(isRoot()).onLast().captureRoboImage()
        compose.onNodeWithText(cancel).performClick()
        compose.onNodeWithText(choose).assertIsDisplayed()
    }
}
