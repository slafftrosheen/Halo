package com.halo.app.di.tools

import android.app.Application
import com.halo.app.study.*
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class StudySessionToolsTest {
    private class Owner : ActiveStudyControl {
        var dispatched: ActiveStudyCommand? = null
        val snapshot = ActiveStudySnapshot("lesson", "study-view:" + "a".repeat(64), "ASK", false, false, false,
            "STANDARD", emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), null, null, null, null)
        override suspend fun current() = snapshot
        override suspend fun dispatch(sessionId: String, sourceVersion: String, command: ActiveStudyCommand,
            admitted: () -> Boolean): ActiveStudyDispatch {
            check(admitted() && sessionId == snapshot.sessionId && sourceVersion == snapshot.sourceVersion)
            dispatched = command
            return ActiveStudyDispatch("queued", sessionId, command.action)
        }
    }
    @Test fun scopedDispatchKeepsLiteralLearnerTextAndNeverClaimsNativeCompletion() = runBlocking {
        val owner = Owner(); val tools = StudySessionTools(owner) { true }
        val read = tools.execute(ToolCall("read", "halo_study_current", emptyMap())) as ToolExecutionResult.Success
        assertEquals(ActionTruth.ACKNOWLEDGED, read.actionTruth)
        val result = tools.execute(ToolCall("send", "halo_study_session_action", mapOf("sessionId" to "lesson",
            "sourceVersion" to owner.snapshot.sourceVersion, "action" to "submit_attempt_feedback", "text" to "I think one half")),
            ToolExecutionContext(interactionId = "current")) as ToolExecutionResult.Success
        assertEquals("I think one half", owner.dispatched!!.text)
        assertEquals(ActionTruth.DISPATCHED, result.actionTruth)
        assertFalse(JSONObject(result.output).getBoolean("completionConfirmed"))
    }
    @Test fun fieldsAreClosedAndCurrentInteractionAdmissionIsRequired() = runBlocking {
        val owner = Owner(); var allowed = false
        val tools = StudySessionTools(owner) { allowed }
        val base = mapOf("sessionId" to "lesson", "sourceVersion" to owner.snapshot.sourceVersion, "action" to "advance")
        val context = ToolExecutionContext(interactionId = "current")
        assertTrue(tools.execute(ToolCall("no", "halo_study_session_action", base), context) is ToolExecutionResult.Rejected)
        allowed = true
        assertTrue(tools.execute(ToolCall("no", "halo_study_session_action", base)) is ToolExecutionResult.Rejected)
        listOf("text", "cloudAllowed", "confirmed", "allowBillable", "providerId").forEach {
            assertTrue(tools.execute(ToolCall("bad", "halo_study_session_action", base + (it to "injected")), context) is ToolExecutionResult.Rejected)
        }
        assertNull(owner.dispatched)
        tools.definitions().forEach { assertNotNull(StudySessionOperability.forDefinition(it)) }
    }
}
