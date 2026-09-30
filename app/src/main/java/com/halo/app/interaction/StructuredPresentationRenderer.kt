package com.halo.app.interaction

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.halo.app.ui.design.HaloSpacing
import com.halo.runtime.cognition.interaction.PresentationEmphasis
import com.halo.runtime.cognition.interaction.PresentationNode
import com.halo.runtime.cognition.interaction.PresentationStepState
import com.halo.runtime.cognition.interaction.PresentationStatusState
import com.halo.runtime.cognition.interaction.StructuredPresentation

/**
 * MINING-P2-001 structured presentation renderer.
 *
 * Renders the CLOSED runtime catalog. It knows nothing about tools, models or providers, and it can
 * execute nothing: a [PresentationNode.ChoiceGroup] is the only interactive element and it emits the
 * existing [com.halo.runtime.cognition.interaction.InteractionResponse] path through
 * [onChoiceSelected], which resolves an already durable input request.
 *
 * Presentation stays Halo-shaped: theme surfaces, Halo spacing, no neon "AI" styling, no banner
 * announcing that content was generated, and status conveyed by text as well as color so color is
 * never the only signal.
 */
enum class PresentationDensity { FULL, COMPACT }

@Composable
fun StructuredPresentationRenderer(
    presentation: StructuredPresentation,
    modifier: Modifier = Modifier,
    density: PresentationDensity = PresentationDensity.FULL,
    onChoiceSelected: ((inputRequestId: String, optionIds: Set<String>) -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            modifier = Modifier.padding(HaloSpacing.md),
            verticalArrangement = Arrangement.spacedBy(HaloSpacing.xs),
        ) {
            RenderNode(
                node = presentation.root,
                density = density,
                nested = false,
                onChoiceSelected = onChoiceSelected,
            )
        }
    }
}

@Composable
private fun RenderNode(
    node: PresentationNode,
    density: PresentationDensity,
    nested: Boolean,
    onChoiceSelected: ((String, Set<String>) -> Unit)?,
) {
    when (node) {
        is PresentationNode.Text -> Text(
            text = node.text,
            style = when (node.emphasis) {
                PresentationEmphasis.STRONG -> MaterialTheme.typography.bodyLarge
                PresentationEmphasis.MUTED -> MaterialTheme.typography.bodySmall
                PresentationEmphasis.NORMAL -> MaterialTheme.typography.bodyMedium
            },
            fontWeight = if (node.emphasis == PresentationEmphasis.STRONG) FontWeight.Medium else null,
            color = if (node.emphasis == PresentationEmphasis.MUTED) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )

        is PresentationNode.Heading -> Text(
            text = node.text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
        )

        is PresentationNode.Caption -> Text(
            text = node.text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        is PresentationNode.KeyValue -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                node.label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                node.value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                // The pair reads as one statement for accessibility.
                modifier = Modifier.semantics { contentDescription = "${node.label}: ${node.value}" },
            )
        }

        is PresentationNode.Badge -> Text(
            text = node.label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp))
                .padding(horizontal = HaloSpacing.sm, vertical = 2.dp),
        )

        is PresentationNode.Status -> {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // A short neutral marker plus explicit words: never color alone.
                Surface(
                    color = statusColor(node.state),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.width(8.dp),
                ) { Text("", modifier = Modifier.padding(4.dp)) }
                Column(Modifier.padding(start = HaloSpacing.sm)) {
                    Text(
                        text = "${node.label} · ${statusCopy(node.state)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.semantics {
                            contentDescription = "${node.label}: ${statusCopy(node.state)}"
                        },
                    )
                    node.detail?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        is PresentationNode.Progress -> {
            val fraction = node.progress
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(node.label, style = MaterialTheme.typography.bodySmall)
                if (fraction == null) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "${node.label}: in progress, amount unknown" },
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f) },
                    )
                }
                node.detail?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        is PresentationNode.BulletList -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            node.items.forEach { item ->
                Text("• $item", style = MaterialTheme.typography.bodyMedium)
            }
        }

        is PresentationNode.Checklist -> Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            node.label?.let { Text(it, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium) }
            node.items.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stepMarker(item.state),
                        modifier = Modifier.width(18.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        item.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (item.state == PresentationStepState.COMPLETED) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.semantics { stateDescription = stepCopy(item.state) },
                    )
                }
            }
        }

        is PresentationNode.Comparison -> if (density == PresentationDensity.COMPACT) {
            // A comparison cannot be squeezed into an overlay: degrade honestly instead.
            Text(
                "Comparison available in Halo",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    node.columns.forEach { column ->
                        Text(
                            column,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                node.rows.forEach { row ->
                    // Linear reading order: label first, then each cell in column order.
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { cell ->
                            Text(
                                cell,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }

        is PresentationNode.ImageReference -> {
            // The renderer resolves only Halo-owned assets; it never fetches a URL.
            Text(
                node.caption ?: "Image available in Halo",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        is PresentationNode.Divider -> Surface(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
        ) { Text("", modifier = Modifier.padding(0.5.dp)) }

        is PresentationNode.Row -> Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(HaloSpacing.sm),
        ) {
            node.children.forEach { child ->
                Column(Modifier.weight(1f)) {
                    RenderNode(child, density, nested = true, onChoiceSelected = onChoiceSelected)
                }
            }
        }

        is PresentationNode.Column -> Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(HaloSpacing.xs),
        ) {
            node.children.forEach { child -> RenderNode(child, density, nested = true, onChoiceSelected = onChoiceSelected) }
        }

        is PresentationNode.Card -> Column(
            Modifier
                .fillMaxWidth()
                .background(
                    if (nested) MaterialTheme.colorScheme.surface else Color.Transparent,
                    RoundedCornerShape(10.dp),
                )
                .padding(if (nested) HaloSpacing.sm else 0.dp),
            verticalArrangement = Arrangement.spacedBy(HaloSpacing.xs),
        ) {
            node.title?.let { Text(it, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium) }
            node.children.forEach { child -> RenderNode(child, density, nested = true, onChoiceSelected = onChoiceSelected) }
        }

        is PresentationNode.ChoiceGroup -> Column(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(node.prompt, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            node.options.forEach { option ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = false,
                            onClick = { onChoiceSelected?.invoke(node.inputRequestId, setOf(option.optionId)) },
                        )
                        .padding(vertical = HaloSpacing.xs),
                ) {
                    Text(option.label, style = MaterialTheme.typography.bodyMedium)
                    option.description?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** Optional host affordance: only shown when a host explicitly offers persistence. */
@Composable
fun SavePresentationToNotebookAction(onSave: () -> Unit) {
    TextButton(onClick = onSave) { Text("Save to Notebook") }
}

private fun statusCopy(state: PresentationStatusState): String = when (state) {
    PresentationStatusState.NEUTRAL -> "Not started"
    PresentationStatusState.INFO -> "In progress"
    PresentationStatusState.SUCCESS -> "Done"
    PresentationStatusState.WARNING -> "Needs attention"
    PresentationStatusState.ERROR -> "Failed"
}

private fun stepCopy(state: PresentationStepState): String = when (state) {
    PresentationStepState.PENDING -> "Not started"
    PresentationStepState.ACTIVE -> "In progress"
    PresentationStepState.COMPLETED -> "Complete"
    PresentationStepState.FAILED -> "Failed"
    PresentationStepState.SKIPPED -> "Skipped"
}

private fun stepMarker(state: PresentationStepState): String = when (state) {
    PresentationStepState.PENDING -> "○"
    PresentationStepState.ACTIVE -> "●"
    PresentationStepState.COMPLETED -> "✓"
    PresentationStepState.FAILED -> "✕"
    PresentationStepState.SKIPPED -> "—"
}

@Composable
private fun statusColor(state: PresentationStatusState): Color = when (state) {
    PresentationStatusState.SUCCESS -> MaterialTheme.colorScheme.primary
    PresentationStatusState.WARNING -> MaterialTheme.colorScheme.tertiary
    PresentationStatusState.ERROR -> MaterialTheme.colorScheme.error
    PresentationStatusState.INFO -> MaterialTheme.colorScheme.secondary
    PresentationStatusState.NEUTRAL -> MaterialTheme.colorScheme.outline
}
