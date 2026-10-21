package com.halo.app.ui.screens.settings

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import com.halo.app.ui.theme.HostlessHaloTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class, qualifiers = "en-w360dp-h780dp", shadows = [BrandBook3PowerManager::class])
class RoutineSuggestionUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun turnOnRequiresReviewedSaveAndDoesNotReplayOnRestore() {
        val restore = StateRestorationTester(compose)
        val draft = RoutineTemplate.BATTERY.buildDraft()
        val open = mutableStateOf(false)
        var saves = 0
        restore.setContent { HostlessHaloTheme {
            if (open.value) EverydayRoutineEditor(draft, null, emptyList(), AutomationReadiness(), {}, { "review" },
                { assertTrue(it.enabled); saves++ }, {}, {}, startWithReview = true)
            else RoutineSuggestionReview(RoutineSuggestion("s", draft.routine.id.value, 3), draft.routine,
                AutomationReadiness(), {}, { open.value = true }, {}, {})
        } }
        compose.onNodeWithText("You completed this routine manually 3 times recently.").assertExists()
        compose.onNodeWithText("Turn on").performScrollTo().performClick()
        assertEquals(0, saves)
        restore.emulateSavedInstanceStateRestore()
        assertEquals(0, saves)
        compose.onNodeWithText("Save & turn on").performScrollTo().performClick()
        assertEquals(1, saves)
    }

    @Test fun missingRoutineAllowsOnlyDismissal() {
        var dismissed = 0
        compose.setContent { HostlessHaloTheme {
            RoutineSuggestionReview(RoutineSuggestion("s", "missing", 3), null, AutomationReadiness(), {},
                { error("must not enable") }, { error("must not edit") }, { dismissed++ })
        } }
        compose.onNodeWithText("Turn on").assertDoesNotExist()
        compose.onNodeWithText("Edit").assertDoesNotExist()
        compose.onNodeWithText("No thanks").performScrollTo().performClick()
        assertEquals(1, dismissed)
    }

    @Test fun largeTextKeepsEditAndDismissReachableWithoutTurningOn() {
        val routine = RoutineTemplate.BATTERY.buildDraft().routine
        var edits = 0
        var dismissed = 0
        compose.setContent { HostlessHaloTheme(fontScale = 2f) {
            RoutineSuggestionReview(RoutineSuggestion("s", routine.id.value, 3), routine, AutomationReadiness(), {},
                { error("must not turn on") }, { edits++ }, { dismissed++ })
        } }
        compose.onNodeWithText("Edit").performScrollTo().performClick()
        compose.onNodeWithText("No thanks").performScrollTo().performClick()
        assertEquals(1, edits)
        assertEquals(1, dismissed)
    }
}
