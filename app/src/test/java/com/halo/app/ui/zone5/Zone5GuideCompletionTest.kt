package com.halo.app.ui.zone5

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.halo.app.ai.IntentQualificationFixture
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.presence.*
import com.halo.app.ui.theme.HostlessHaloTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Zone5GuideCompletionTest {
    @get:Rule val compose = createComposeRule()
    @Test fun openingAddDoesNotCompleteGuidanceButAnOwnerReceiptDoesAndPersists() {
        val storage = IntentQualificationFixture.MemoryStorage()
        val session = Zone5GuideSession(storage)
        val receipt = mutableStateOf(false)
        val holder = mutableStateOf<ShellPageProjection?>(null)
        var calls = 0
        compose.setContent { HostlessHaloTheme {
            CompositionLocalProvider(LocalZone5GuideSession provides session, LocalShellPageActions provides holder) {
                BindShellPageActions(ShellPageOwner("studio", "project", completedGuideIds = if (receipt.value) setOf("studio-layer.v1") else emptySet()),
                    listOf(ShellPageAction("page.studio.add-layer", "Add layer", HaloGlyph.NOTE) { calls++ }))
            }
        } }
        compose.runOnIdle {
            assertTrue(holder.value!!.invoke("page.studio.add-layer"))
            assertEquals(1, calls)
            assertFalse(session.suppressed("studio-layer.v1"))
            receipt.value = true
        }
        compose.runOnIdle {
            assertTrue(session.suppressed("studio-layer.v1"))
            assertTrue(Zone5GuideSession(storage).suppressed("studio-layer.v1"))
        }
    }
    @Test fun requestedGuidanceNeverExecutesAndPauseExitAndObjectChangeDoNotChangeWork() {
        val session = Zone5GuideSession()
        val holder = mutableStateOf<ShellPageProjection?>(null)
        var calls = 0
        val owner = ShellPageOwner("note", "first", helpDescription = "Write and keep a note.",
            helpSteps = listOf(ShellGuideStep("note-insert.v1", "page.note.insert", "Add text")))
        compose.setContent { HostlessHaloTheme {
            CompositionLocalProvider(LocalZone5GuideSession provides session, LocalShellPageActions provides holder) {
                BindShellPageActions(owner, listOf(ShellPageAction("page.note.insert", "Add text", HaloGlyph.NOTE) { calls++ }))
                holder.value?.let { Zone5TaskHelp(it, false, { id -> it.invoke(id) }, {}) }
            }
        } }
        compose.onNodeWithText("Guide me one step at a time").performClick()
        compose.runOnIdle { assertEquals(0, calls); assertTrue(session.applies(owner)); assertFalse(session.suppressed("note-insert.v1")) }
        compose.onNodeWithText("Pause guidance").performClick()
        compose.runOnIdle { assertFalse(session.applies(owner)); assertEquals(0, calls); session.start(owner); session.observe(owner.copy(objectId = "second")); assertTrue(session.paused) }
        compose.onNodeWithText("Exit guidance").performClick()
        compose.runOnIdle { assertNull(session.guidedFamily); assertEquals(0, calls) }
    }
    @Test fun blockedRequestedStepShowsTheActualReasonAndDoesNotDismissHelp() {
        val session = Zone5GuideSession()
        val holder = mutableStateOf<ShellPageProjection?>(null)
        var closes = 0
        var calls = 0
        val owner = ShellPageOwner("study", helpDescription = "Choose a learning source.",
            disabledActionReasons = mapOf("page.study.next" to "Choose an available model first."),
            helpSteps = listOf(ShellGuideStep("study-start", "page.study.next", "Start the lesson")))
        compose.setContent { HostlessHaloTheme {
            CompositionLocalProvider(LocalZone5GuideSession provides session, LocalShellPageActions provides holder) {
                BindShellPageActions(owner, listOf(ShellPageAction("page.study.next", "Start lesson", HaloGlyph.NEXT, false) { calls++ }))
                androidx.compose.foundation.layout.Column { holder.value?.let {
                    Zone5TaskHelp(it, false, { id -> it.invoke(id) }, { closes++ })
                } }
            }
        } }
        compose.onNodeWithText("Choose an available model first.").assertIsDisplayed()
        compose.onNodeWithText("Start lesson").assertIsNotEnabled()
        compose.onNodeWithText("Guide me one step at a time").performClick()
        compose.runOnIdle { assertEquals(0, closes); assertEquals(0, calls); assertTrue(session.applies(owner)) }
    }

}
