package com.halo.app.ui.screens.settings

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.halo.app.automation.RoutineArguments
import com.halo.app.ui.theme.HostlessHaloTheme
import com.halo.runtime.cognition.routine.*
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class, qualifiers = "en-w360dp-h780dp",
    shadows = [BrandBook3PowerManager::class])
class RoutineBrandBookUiTest {
    @get:Rule val compose = createComposeRule()
    private val blocked = mutableStateOf(false)
    private var runs = 0
    private var deletes = 0
    private fun show(scale: Float = 1f) {
        val now = Instant.parse("2026-10-21T12:00:00Z")
        val original = Routine(RoutineId("test"), "My routine", false,
            RoutineTrigger.Once(now.plusSeconds(600)), actions = listOf(messageAction("My message")),
            createdAt = now, updatedAt = now, delivery = RoutineDeliveryPolicy.SilentActivity)
        val routine = original.copy(grantDigest = RoutineArguments.grant(original))
        compose.setContent { HostlessHaloTheme(fontScale = scale) {
            RoutineDetail(routine, null, false, AutomationReadiness(), "internal token=secret", blocked.value,
                onBack = {}, onEdit = {}, onDuplicate = {}, onDelete = { deletes++ },
                onRun = { runs++ }, onEnable = {}, onActivity = {}, onNotifications = {}, onInbox = {})
        } }
    }

    @Test fun confirmRechecksAvailabilityAndCancelDoesNotExecute() {
        show()
        compose.onNodeWithText("Run now").performScrollTo().performClick()
        assertEquals(0, runs)
        compose.runOnIdle { blocked.value = true }
        compose.onNode(isDialog()).assertExists()
        compose.onAllNodesWithText("Run now").filter(isEnabled()).assertCountEquals(0)
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(0, runs)
        compose.runOnIdle { blocked.value = false }
        compose.onNodeWithText("Run now").performScrollTo().performClick()
        compose.onAllNodesWithText("Run now").onLast().performClick()
        assertEquals(1, runs)
    }

    @Test fun largeTextDeleteStaysReachableAndCancelKeepsRoutine() {
        show(2f)
        compose.onNodeWithText("internal token=secret").assertDoesNotExist()
        compose.onNodeWithText("Delete").performScrollTo().performClick()
        assertEquals(0, deletes)
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(0, deletes)
        compose.onNodeWithText("Delete").performScrollTo().performClick()
        compose.onAllNodesWithText("Delete").onLast().performClick()
        assertEquals(1, deletes)
    }
}
