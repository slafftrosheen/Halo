package com.halo.app.ui.screens.settings

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import com.halo.app.ui.theme.HostlessHaloTheme
import com.halo.runtime.cognition.routine.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class, qualifiers = "en-w360dp-h780dp", shadows = [BrandBook3PowerManager::class])
class RoutineTrialUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun explicitTrialConfirmationAndRecreationNeverSaveOrRepeat() {
        val restore = StateRestorationTester(compose)
        val draft = RoutineTemplate.BATTERY.buildDraft()
        val state = mutableStateOf(RoutineTrialState())
        var calls = 0
        var saves = 0
        var stops = 0
        restore.setContent { HostlessHaloTheme {
            EverydayRoutineEditor(draft, null, emptyList(), AutomationReadiness(), {}, { "review" }, { saves++ }, {}, {},
                trial = state.value, onTryOnce = { calls++; state.value = RoutineTrialState(routineTrialKey(it), running = true) },
                onStopTrial = { stops++ })
        } }
        compose.onNodeWithText("Review & save").performScrollTo().performClick()
        compose.onNodeWithText("Try it once").performScrollTo().performClick()
        assertEquals(0, calls)
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(0, calls)
        compose.onNodeWithText("Try it once").performScrollTo().performClick()
        compose.onNodeWithText("Run trial").performClick()
        assertEquals(1, calls)
        compose.onNodeWithText("Save off").performScrollTo().assertIsNotEnabled()
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Try it once").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Stop trial").performScrollTo().performClick()
        assertEquals(1, stops)
        assertEquals(1, calls)
        assertEquals(0, saves)
    }

    @Test fun failedLatestRunKeepsPreviousSuccessVisible() {
        val routine = RoutineTemplate.BATTERY.buildDraft().routine
        val successful = RoutineRunSummary("success", routine.id, "agent", routine.createdAt, RoutineRunState.COMPLETED,
            reason = "My earlier useful result")
        val failure = successful.copy(id = "failure", state = RoutineRunState.FAILED, reason = "private error",
            triggerAt = routine.createdAt.plusSeconds(60))
        compose.setContent { HostlessHaloTheme {
            RoutineDetail(routine, failure, false, AutomationReadiness(), null, false,
                {}, {}, {}, {}, {}, {}, {}, {}, {}, previousSuccess = successful)
        } }
        compose.onNodeWithText("Failed").performScrollTo().assertExists()
        compose.onNodeWithText("Last successful result").performScrollTo().assertExists()
        compose.onNodeWithText("My earlier useful result").performScrollTo().assertExists()
        compose.onNodeWithText("private error").assertDoesNotExist()
    }
}
