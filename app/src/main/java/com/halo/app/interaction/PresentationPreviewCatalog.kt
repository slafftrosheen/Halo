package com.halo.app.interaction

import com.halo.runtime.cognition.interaction.PresentationChecklistItem
import com.halo.runtime.cognition.interaction.PresentationChoice
import com.halo.runtime.cognition.interaction.PresentationEmphasis
import com.halo.runtime.cognition.interaction.PresentationNode
import com.halo.runtime.cognition.interaction.PresentationStatusState
import com.halo.runtime.cognition.interaction.PresentationStepState
import com.halo.runtime.cognition.interaction.StructuredPresentation

/**
 * MINING-P2-001 static presentation preview.
 *
 * Deliberately fixed, hard-coded sample data used by System Check so the renderer can be inspected on
 * a real device without a model, a tool call, a provider or a network request. Nothing here is
 * generated, and no probe runs because someone opened this screen.
 *
 * The `ChoiceGroup` is inert by construction: its input request does not exist, no response handler is
 * bound, and tapping an option reaches the renderer's `null` callback. It exists so the accessibility
 * and visual treatment of a choice can be reviewed during phone stabilization.
 */
object PresentationPreviewCatalog {

    fun staticPreview(): StructuredPresentation = StructuredPresentation(
        root = PresentationNode.Card(
            nodeId = "preview-root",
            title = "Preview (static sample)",
            children = listOf(
                PresentationNode.Heading(nodeId = "preview-heading", text = "Device"),
                PresentationNode.Caption(nodeId = "preview-caption", text = "Sample data · no model call"),
                PresentationNode.Row(
                    nodeId = "preview-row",
                    children = listOf(
                        PresentationNode.Status(
                            nodeId = "preview-status",
                            label = "Backend",
                            state = PresentationStatusState.SUCCESS,
                            detail = "GPU",
                        ),
                        PresentationNode.Badge(nodeId = "preview-badge", label = "bounded"),
                    ),
                ),
                PresentationNode.KeyValue(nodeId = "preview-kv-battery", label = "Battery", value = "68%"),
                PresentationNode.KeyValue(nodeId = "preview-kv-charging", label = "Charging", value = "Yes"),
                PresentationNode.KeyValue(nodeId = "preview-kv-storage", label = "Storage free", value = "42%"),
                PresentationNode.Progress(
                    nodeId = "preview-progress",
                    label = "Preparation",
                    progress = 0.4f,
                    state = PresentationStatusState.INFO,
                ),
                PresentationNode.Divider(nodeId = "preview-divider"),
                PresentationNode.BulletList(
                    nodeId = "preview-bullets",
                    items = listOf(
                        "Local model stays resident",
                        "Memory search is lexical today",
                        "Semantic memory is optional",
                    ),
                ),
                PresentationNode.Checklist(
                    nodeId = "preview-checklist",
                    label = "Plan",
                    items = listOf(
                        PresentationChecklistItem("preview-step-1", "Find this week's notes", PresentationStepState.COMPLETED),
                        PresentationChecklistItem("preview-step-2", "Summarize", PresentationStepState.COMPLETED),
                        PresentationChecklistItem("preview-step-3", "Draft message", PresentationStepState.ACTIVE),
                        PresentationChecklistItem("preview-step-4", "Send message", PresentationStepState.PENDING),
                    ),
                ),
                PresentationNode.Comparison(
                    nodeId = "preview-comparison",
                    columns = listOf("Model", "Size", "Backend"),
                    rows = listOf(
                        listOf("Qwen 0.6B", "329 MB", "GPU"),
                        listOf("Qwen 1.7B", "932 MB", "GPU"),
                    ),
                ),
                PresentationNode.Text(
                    nodeId = "preview-text-strong",
                    text = "Emphasis is typographic, never a color the model chooses.",
                    emphasis = PresentationEmphasis.STRONG,
                ),
                PresentationNode.Text(
                    nodeId = "preview-text-muted",
                    text = "Choice groups below are inert samples.",
                    emphasis = PresentationEmphasis.MUTED,
                ),
                PresentationNode.ChoiceGroup(
                    nodeId = "preview-choice",
                    inputRequestId = "preview-only",
                    prompt = "Continue?",
                    options = listOf(
                        PresentationChoice("preview-yes", "Continue"),
                        PresentationChoice("preview-no", "Stop"),
                    ),
                ),
                PresentationNode.ImageReference(
                    nodeId = "preview-image",
                    assetId = "sample-asset",
                    caption = "Images resolve through Halo-owned assets only.",
                ),
            ),
        ),
    )
}
