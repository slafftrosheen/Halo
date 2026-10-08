package com.halo.app.daily

import com.halo.cognition.language.InteractionId
import com.halo.runtime.cognition.agent.*
import com.halo.runtime.cognition.attention.*
import com.halo.runtime.cognition.routine.*
import com.halo.app.data.study.*
import com.halo.sdk.runtime.InteractionAudioRouteState
import com.halo.app.ui.shell.dailyOwnerRoute
import com.halo.app.ui.shell.primaryNavigationRoute
import com.halo.sdk.runtime.study.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class DailyAssistantProjectorTest {
    private val now = Instant.parse("2026-10-08T09:00:00Z")
    private fun attention(id: String = "approval", intent: DeliveryIntent = DeliveryIntent.RequestApproval(id, "PRIVATE"),
        source: AttentionSource = AttentionSource.DirectUserRequest, interaction: String? = null,
        expiry: Instant? = null, urgency: AttentionUrgency = AttentionUrgency.WHEN_CONVENIENT,
        created: Instant = now.minusSeconds(10)) = QueuedAttentionRequest(
        AttentionRequest(AttentionRequestId(id), source, interaction?.let(::InteractionId), deliveryIntent = intent,
            expiresAt = expiry, urgency = urgency, createdAt = created), AttentionDecisionReason.QUIET_MODE, created)
    private fun card(id: String = "card", lesson: String = "lesson", at: Instant = now) = StudyPracticeItem(id, lesson,
        "PRIVATE QUESTION", "PRIVATE ANSWER", null, null, true, 0, at.toEpochMilli(), 0,
        createdAtEpochMs = now.minusSeconds(100).toEpochMilli(), lastReviewedAtEpochMs = null)
    private fun routine(id: String, at: Instant?, enabled: Boolean = true) = Routine(RoutineId(id), id, enabled,
        RoutineTrigger.Once(now), createdAt = now.minusSeconds(500), updatedAt = now, nextRunAt = at)
    private fun project(input: DailyAssistantInput) = DailyAssistantProjector.project(input, now)

    @Test fun priorityChangesWhenApprovalAndInboxResolve() {
        val input = DailyAssistantInput(attention = listOf(attention()), notifications = 8, replyable = 2,
            dueCards = listOf(card()), learningCount = 4, showLearning = true)
        assertEquals(DailyAssistantKind.APPROVAL, project(input).primary!!.kind)
        assertEquals(DailyAssistantKind.NOTIFICATIONS, project(input.copy(attention = emptyList())).primary!!.kind)
        assertEquals(DailyAssistantKind.STUDY_REVIEW, project(input.copy(attention = emptyList(), notifications = 0, replyable = 0)).primary!!.kind)
        assertEquals(3, project(input).secondary.size)
    }
    @Test fun oldestNeedThenStableIdBreaksTies() {
        val rows = listOf(attention("b"), attention("a"), attention("older", created = now.minusSeconds(50)))
        assertEquals(listOf("attention:older", "attention:a", "attention:b"), project(DailyAssistantInput(attention = rows)).items.map { it.id })
    }
    @Test fun expiredAttentionNeverRevived() {
        val result = project(DailyAssistantInput(attention = listOf(attention("old", expiry = now), attention("valid"))))
        assertEquals(listOf("attention:valid"), result.items.map { it.id })
    }
    @Test fun attentionWinsOverSameRoutineFailure() {
        val run = RoutineRunSummary("run", RoutineId("r"), "run", now.minusSeconds(10), RoutineRunState.FAILED)
        val result = project(DailyAssistantInput(attention = listOf(attention(intent = DeliveryIntent.ActionRequired("PRIVATE"),
            source = AttentionSource.Routine, interaction = "run")), routineRuns = listOf(run)))
        assertEquals(1, result.items.size)
        assertEquals("attention:approval", result.primary!!.id)
        assertEquals(DailyAssistantKind.AUTOMATION_FAILURE, result.primary!!.kind)
        assertEquals(DailyAssistantPriority.NEEDS_YOU_NOW, result.primary!!.priority)
        assertEquals(DailyAssistantAction.OpenAutomation("r"), result.primary!!.action)
    }
    @Test fun routineFailuresIsolateHistoryAndUseLatestFailurePerRoutine() {
        val runs = listOf(RoutineRunSummary("ok", RoutineId("a"), "ok", now, RoutineRunState.COMPLETED),
            RoutineRunSummary("old", RoutineId("b"), "old", now.minusSeconds(86401), RoutineRunState.FAILED),
            RoutineRunSummary("fail1", RoutineId("c"), "fail1", now.minusSeconds(60), RoutineRunState.BLOCKED),
            RoutineRunSummary("fail2", RoutineId("c"), "fail2", now.minusSeconds(20), RoutineRunState.PARTIAL))
        assertEquals(listOf("routine:failure:fail2"), project(DailyAssistantInput(routineRuns = runs)).items.map { it.id })
    }
    @Test fun upcomingUsesStoredFutureTimeOnly() {
        val rows = listOf(routine("a", now.plusSeconds(7200)), routine("b", now.plusSeconds(1800)),
            routine("disabled", now.plusSeconds(600), false), routine("far", now.plusSeconds(259200)), routine("pending", null))
        val input = DailyAssistantInput(routines = rows)
        assertEquals("b", project(input).nextRoutine!!.routineId)
        repeat(4) { project(input) }
        assertEquals(rows, input.routines)
        assertNull(project(DailyAssistantInput(routines = listOf(rows[3], rows[4]))).nextRoutine)
        assertTrue(project(input).isCaughtUp)
        assertEquals(0, project(input).counts.needsYou)
    }
    @Test fun studyTargetsOldestDueAndRetainsReviewState() {
        val first = card("a", "A", now.minusSeconds(10))
        val oldest = card("b", "B", now.minusSeconds(60))
        val input = DailyAssistantInput(dueCards = listOf(first, oldest))
        val result = project(input)
        assertEquals(DailyAssistantAction.OpenStudy("B", true), result.primary!!.action)
        assertEquals("2 cards due across 2 lessons", result.primary!!.summary)
        assertEquals(listOf(first, oldest), input.dueCards)
        assertEquals(0, oldest.reviewCount)
    }
    @Test fun privateInboxAndLearningContentsAreAbsent() {
        val result = project(DailyAssistantInput(notifications = 8, replyable = 2, learningCount = 3, showLearning = true))
        assertEquals(1, result.items.count { it.kind == DailyAssistantKind.NOTIFICATIONS })
        assertEquals("8 notifications · 2 replyable", result.primary!!.summary)
        assertFalse(result.toString().contains("PRIVATE"))
        assertEquals(0, project(DailyAssistantInput(learningCount = 3)).counts.learningSuggestions)
    }
    private fun waitingRun(resolved: Boolean = false, completed: Boolean = false): AgentRun {
        val id = AgentRunId("run")
        val input = AgentInputRequest.Clarification(AgentInputRequestId("input"), id, now.minusSeconds(30), question = "PRIVATE", field = "x")
        val steps = mutableListOf<AgentStep>(AgentStep.InputRequested(AgentStepId("s"), id, input.createdAt, input.id, input))
        if (resolved) steps += AgentStep.InputResolved(AgentStepId("r"), id, now, input.id, "resolved")
        return AgentRun(id, InteractionId("i"), "PRIVATE", if (completed) AgentRunState.COMPLETED else AgentRunState.WAITING_FOR_CLARIFICATION,
            createdAt = now.minusSeconds(60), updatedAt = now, steps = steps)
    }
    @Test fun unresolvedInputTargetsExactRunAndDoesNotCopyQuestion() {
        val result = project(DailyAssistantInput(runs = listOf(waitingRun())))
        assertEquals(DailyAssistantKind.CLARIFICATION, result.primary!!.kind)
        assertEquals(DailyAssistantAction.OpenActivity("run"), result.primary!!.action)
        assertFalse(result.toString().contains("PRIVATE"))
        assertTrue(project(DailyAssistantInput(runs = listOf(waitingRun(completed = true)))).items.isEmpty())
    }
    @Test fun inputDedupAndResolvedJournalAuthority() {
        val queued = attention(intent = DeliveryIntent.RequestClarification("input", "PRIVATE"))
        assertEquals(1, project(DailyAssistantInput(attention = listOf(queued), runs = listOf(waitingRun()))).items.size)
        assertTrue(project(DailyAssistantInput(attention = listOf(queued), runs = listOf(waitingRun(resolved = true)))).items.isEmpty())
    }
    @Test fun childWaitingDoesNotBecomeRootNeed() {
        val child = waitingRun().copy(parentRunId = AgentRunId("parent"))
        assertTrue(project(DailyAssistantInput(runs = listOf(child))).items.isEmpty())
    }
    @Test fun readyAndAssociatedAreNotBothConnected() {
        val result = project(DailyAssistantInput(readyDeviceLabels = listOf("HeyCyan")))
        assertEquals("HeyCyan ready", result.companion!!.label)
        assertTrue(result.items.isEmpty())
    }
    @Test fun endedVoiceIsLastReplyWithoutWarning() {
        val result = project(DailyAssistantInput(voice = DailyVoiceFact("HeyCyan", "HeyCyan", "HeyCyan", InteractionAudioRouteState.ENDED)))
        assertEquals("Last voice reply · HeyCyan → HeyCyan", result.companion!!.voice)
        assertTrue(result.items.isEmpty())
    }
    @Test fun lostVoiceTargetsOwnerAndHidesIdentifiers() {
        val result = project(DailyAssistantInput(voice = DailyVoiceFact("HeyCyan", "HeyCyan", null, InteractionAudioRouteState.LOST)))
        assertEquals(DailyAssistantAction.OpenVoice, result.primary!!.action)
        assertEquals("External device", safeDeviceLabel("AA:BB:CC:DD:EE:FF"))
    }
    @Test fun emptyProjectionIsCaughtUpAndDeterministic() {
        val input = DailyAssistantInput()
        val result = project(input)
        assertEquals("You're caught up", result.summary)
        assertNull(result.primary)
        repeat(10) { assertEquals(result, project(input)) }
    }
    @Test fun everyTypedActionMapsToExistingOwnerAndEncodesIds() {
        val actions = listOf(DailyAssistantAction.OpenInbox, DailyAssistantAction.OpenActivity("a/b"),
            DailyAssistantAction.OpenLearningSuggestions, DailyAssistantAction.OpenStudy("lesson", true),
            DailyAssistantAction.OpenAutomation("routine"), DailyAssistantAction.OpenDevices, DailyAssistantAction.OpenVoice,
            DailyAssistantAction.OpenProvider(null))
        assertEquals(listOf("notifications", "activity?runId=a%2Fb", "notebook/memory?section=Suggestions",
            "notebook/study/lesson?review=true", "automation?routineId=routine", "devices", "voice_settings", "provider_setup"), actions.map(::dailyOwnerRoute))
    }
    @Test fun dueReviewSuppressesResumeAndOtherwiseNewestLessonWins() {
        fun lesson(id: String, updated: Long) = StudySessionRecord(id, id, StudyLanguagePreferences("lv", "ru", "lv"),
            emptyList(), null, null, null, null, TutorStage.HINT, StudySessionLifecycle.ACTIVE,
            null, false, null, 1, updated)
        val lessons = listOf(lesson("A", 100), lesson("B", 200))
        assertEquals(DailyAssistantAction.OpenStudy("B", false), project(DailyAssistantInput(lessons = lessons)).primary!!.action)
        val due = project(DailyAssistantInput(lessons = lessons, dueCards = listOf(card(lesson = "A"))))
        assertEquals(0, due.items.count { it.kind == DailyAssistantKind.STUDY_RESUME })
    }
    @Test fun focusedOwnerRoutesKeepTheirPrimaryTab() {
        assertEquals("activity", primaryNavigationRoute("activity?runId={runId}"))
        assertEquals("settings", primaryNavigationRoute("automation?routineId={routineId}"))
    }

    @Test fun completedJournalCannotReviveQueuedInput() {
        val queued = attention(intent = DeliveryIntent.RequestClarification("input", "PRIVATE"))
        assertTrue(project(DailyAssistantInput(attention = listOf(queued), runs = listOf(waitingRun(completed = true)))).items.isEmpty())
    }
    @Test fun queuedRoutineFailureStillOutranksUpcoming() {
        val failure = RoutineRunSummary("run", RoutineId("r"), "run", now, RoutineRunState.FAILED)
        val result = project(DailyAssistantInput(attention = listOf(attention(intent = DeliveryIntent.Inform(UserFacingContent("PRIVATE", "PRIVATE")),
            source = AttentionSource.Routine, interaction = "run")), routineRuns = listOf(failure), routines = listOf(routine("next", now.plusSeconds(30)))))
        assertEquals(DailyAssistantKind.AUTOMATION_FAILURE, result.primary!!.kind)
        assertEquals(1, result.counts.needsYou)
        assertFalse(result.toString().contains("PRIVATE"))
    }

}
