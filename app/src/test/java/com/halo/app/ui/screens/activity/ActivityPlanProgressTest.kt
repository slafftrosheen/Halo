package com.halo.app.ui.screens.activity

import com.halo.cognition.language.InteractionId
import com.halo.runtime.cognition.agent.*
import com.halo.runtime.cognition.interaction.*
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class ActivityPlanProgressTest {
    @Test fun countsUseBoundedChecklistStatesAndTerminalOnlySuppressesWaiting() {
        val presentation = PresentationTemplates.checklist("Arbitrary source title", listOf(
            PresentationDirectiveItem("3 / 3 steps complete", PresentationStepState.COMPLETED),
            PresentationDirectiveItem("Waiting for approval", PresentationStepState.FAILED),
            PresentationDirectiveItem("Source text", PresentationStepState.PENDING)))
        assertEquals(ActivityPlanProgress(1, 3, true), activityPlanProgress(presentation, false))
        assertEquals(ActivityPlanProgress(1, 3, false), activityPlanProgress(presentation, true))
        assertEquals("Arbitrary source title", (presentation.root as PresentationNode.Card).children
            .filterIsInstance<PresentationNode.Heading>().single().text)
    }

    @Test fun routineReceiptPhasesAndCanonicalSourceAreNotDeduplicatedOrTranslated() {
        val id = AgentRunId("run")
        val at = Instant.EPOCH
        val steps = listOf(
            AgentStep.PlanCreated(AgentStepId("plan"), id, at, "plan", "Triggered private source; 0 conditions, 1 sequential actions"),
            AgentStep.ToolCompleted(AgentStepId("receipt-one"), id, at, "call", "halo_routine_message", "Private tool output", true),
            AgentStep.ToolCompleted(AgentStepId("receipt-two"), id, at, "call", "halo_routine_message", "Private tool output", true))
        val run = AgentRun(id, InteractionId("interaction"), "Private user request", AgentRunState.COMPLETED,
            createdAt = at, updatedAt = at, steps = steps)
        val story = run.toStory()
        assertEquals(ActivityPlanProgress(3, 3, false), story.typedPlanProgress)
        assertEquals("Plan", story.planTitleKey)
        assertTrue(story.steps.all { it.hostOwnedCopy })
        assertFalse(story.steps.last().hostOwnedMessageCopy)
        assertEquals(steps.map { it.id.value }, story.steps.map { it.presentationId })
        assertEquals(steps, run.steps)
        val items = (story.planPresentation!!.root as PresentationNode.Card).children
            .filterIsInstance<PresentationNode.Checklist>().single().items
        assertEquals("Triggered private source; 0 conditions, 1 sequential actions", items.first().text)
        assertEquals(listOf("halo_routine_message: completed", "halo_routine_message: completed"), items.drop(1).map { it.text })
        assertFalse(ActivityStep("external", "Updated your task", "Updated your task").hostOwnedCopy)
    }
}
