package com.halo.app.ui.screens.settings

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import com.halo.app.ui.theme.HaloTheme
import com.halo.runtime.cognition.routine.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class RoutineDraftStateTest {
    @get:Rule val compose = createComposeRule()

    @Test fun restoringAnEditNeverRestoresExecutionAuthority() {
        val original = RoutineTemplate.NETWORK.buildDraft().routine.copy(enabled = true, grantDigest = "old-approval",
            conditions = listOf(RoutineCondition.BatteryAbove(40)))
        val restored = requireNotNull(decodeRoutineDraft(encodeRoutineDraft(RoutineDraft(original, original.id))))
        assertEquals(original.id, restored.originalId)
        assertEquals(original.actions, restored.routine.actions)
        assertEquals(original.conditions, restored.routine.conditions)
        assertFalse(restored.routine.enabled)
        assertNull(restored.routine.grantDigest)
        assertNull(restored.routine.nextRunAt)
        assertEquals(RoutineUiStatus.NEEDS_REVIEW, routineUiStatus(restored.routine))
        assertNotNull(decodeRoutineDraft(encodeRoutineDraft(RoutineDraft(original.copy(actions = emptyList())))))
        assertNull(decodeRoutineDraft("invalid"))
    }

    @Test fun editorDraftSurvivesRecreationWithoutSavingOrRunning() {
        val restore = StateRestorationTester(compose)
        val draft = RoutineTemplate.BATTERY.buildDraft()
        var saves = 0
        var reviews = 0
        restore.setContent { HaloTheme {
            EverydayRoutineEditor(draft, null, emptyList(), AutomationReadiness(), {},
                { reviews++; "review" }, { saves++ }, {}, {})
        } }
        compose.onNodeWithText("Name").performTextReplacement("My retained draft")
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("My retained draft").assertExists()
        assertEquals(0, saves)
        assertEquals(0, reviews)
        compose.onNodeWithText("Review & save").performScrollTo().performClick()
        assertEquals(1, reviews)
        assertEquals(0, saves)
        compose.onNodeWithText("Save off").performScrollTo().performClick()
        assertEquals(1, saves)
    }
}
