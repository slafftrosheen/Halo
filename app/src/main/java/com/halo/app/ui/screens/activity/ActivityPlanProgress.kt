package com.halo.app.ui.screens.activity

import androidx.compose.runtime.Composable
import com.halo.app.language.uiText
import com.halo.runtime.cognition.agent.AgentStep
import com.halo.runtime.cognition.interaction.PlanPresentationProjector
import com.halo.runtime.cognition.interaction.PresentationNode
import com.halo.runtime.cognition.interaction.PresentationStepState
import com.halo.runtime.cognition.interaction.StructuredPresentation

/** The title is explicitly host-owned here, including the phone transaction branch.
 * Remove only the leading heading just requested from this known producer; generic
 * presentations and journal item/summary text never enter this title projection. */
internal fun activityJournalPresentation(steps: List<AgentStep>): StructuredPresentation {
    val projected = PlanPresentationProjector.fromJournal(steps, title = "Plan")
    val card = projected.root as? PresentationNode.Card ?: return projected
    val heading = card.children.firstOrNull() as? PresentationNode.Heading ?: return projected
    return if (heading.nodeId == "title") projected.copy(root = card.copy(children = card.children.drop(1)))
        else projected
}

/** Transient host copy inputs from the same bounded checklist; never parse journal prose. */
data class ActivityPlanProgress(val completed: Int, val total: Int, val waiting: Boolean)

internal fun activityPlanProgress(presentation: StructuredPresentation, terminal: Boolean): ActivityPlanProgress? {
    fun checklist(node: PresentationNode): PresentationNode.Checklist? = when (node) {
        is PresentationNode.Checklist -> node
        is PresentationNode.Card -> node.children.firstNotNullOfOrNull { checklist(it) }
        is PresentationNode.Column -> node.children.firstNotNullOfOrNull { checklist(it) }
        is PresentationNode.Row -> node.children.firstNotNullOfOrNull { checklist(it) }
        else -> null
    }
    val items = checklist(presentation.root)?.items ?: return null
    if (items.isEmpty()) return null
    return ActivityPlanProgress(items.count { it.state == PresentationStepState.COMPLETED }, items.size,
        !terminal && items.any { it.state == PresentationStepState.PENDING })
}

@Composable
internal fun localizedActivityProgress(progress: ActivityPlanProgress): String {
    val counts = uiText("%1\$d / %2\$d steps complete", progress.completed, progress.total)
    return if (progress.waiting) "$counts · ${uiText("Waiting for approval")}" else counts
}
