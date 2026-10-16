package com.halo.app.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.halo.app.language.uiText
import com.halo.app.ui.design.HaloShapes
import com.halo.app.ui.fx.HaloFxToolPhase
import com.halo.app.ui.fx.HaloToolFx
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.agent.AgentRun
import com.halo.runtime.cognition.agent.AgentStep
import com.halo.runtime.cognition.continuity.RunContinuityResolver
import kotlinx.coroutines.delay

internal enum class HomeToolReceiptKind { CONFIRMED, UNVERIFIED, FAILED }

internal data class HomeToolResultReceipt(
    val stepId: String,
    val toolId: String,
    val kind: HomeToolReceiptKind,
    val timestampMs: Long,
)

internal fun homeToolReceiptKind(success: Boolean, truth: ActionTruth): HomeToolReceiptKind =
    when {
        !success || truth == ActionTruth.FAILED -> HomeToolReceiptKind.FAILED
        truth == ActionTruth.CONFIRMED -> HomeToolReceiptKind.CONFIRMED
        else -> HomeToolReceiptKind.UNVERIFIED
    }

/**
 * Real persisted tool receipts, scoped to the active conversation or bound
 * runtime run. A provider answer, a selected tool, and dispatch alone do NOT
 * prove external action success.
 */
internal fun homeLatestToolReceipt(
    runs: List<AgentRun>,
    conversationId: String,
    runId: String?,
): HomeToolResultReceipt? {
    if (conversationId.isBlank()) return null
    return runs.asSequence()
        .filter { run ->
            RunContinuityResolver.conversationId(run) == conversationId ||
                (runId != null && (run.id.value == runId || run.rootRunId?.value == runId))
        }
        .flatMap { run ->
            run.steps.asSequence().mapNotNull { step ->
                when (step) {
                    is AgentStep.ToolOutcomeRecorded -> HomeToolResultReceipt(
                        "${run.id.value}:${step.toolCallId}:${homeToolReceiptKind(step.success, step.actionTruth)}",
                        step.toolId, homeToolReceiptKind(step.success, step.actionTruth),
                        step.createdAt.toEpochMilli(),
                    )
                    is AgentStep.ToolCompleted -> HomeToolResultReceipt(
                        "${run.id.value}:${step.toolCallId}:${homeToolReceiptKind(step.success, step.actionTruth)}",
                        step.toolId, homeToolReceiptKind(step.success, step.actionTruth),
                        step.createdAt.toEpochMilli(),
                    )
                    is AgentStep.ToolFailed -> HomeToolResultReceipt(
                        "${run.id.value}:${step.toolCallId}:FAILED",
                        step.toolId, HomeToolReceiptKind.FAILED,
                        step.createdAt.toEpochMilli(),
                    )
                    else -> null
                }?.takeIf { homeFxTool(it.toolId) != null }
            }
        }
        .maxWithOrNull(compareBy<HomeToolResultReceipt> { it.timestampMs }.thenBy { it.stepId })
}

/** A journal event is shown only if it arrived while this Home screen
 * was mounted and foregrounded. Old persisted receipts never get re-celebrated.
 */
internal fun homeReceiptIsNew(receipt: HomeToolResultReceipt?, openedAtMs: Long): Boolean =
    receipt != null && receipt.timestampMs >= openedAtMs

@Composable
internal fun HomeToolResultFx(receipt: HomeToolResultReceipt?, conversationId: String?) {
    val openedAt = remember(conversationId) { System.currentTimeMillis() }
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    var lastSeen by remember(conversationId) { mutableStateOf(receipt?.stepId) }
    var visibleReceipt by remember(conversationId) { mutableStateOf<HomeToolResultReceipt?>(null) }
    LaunchedEffect(conversationId, receipt?.stepId) {
        val latestId = receipt?.stepId
        if (latestId != null && latestId != lastSeen &&
            homeReceiptIsNew(receipt, openedAt) &&
            lifecycle.isAtLeast(Lifecycle.State.RESUMED)
        ) visibleReceipt = receipt
        lastSeen = latestId
        if (visibleReceipt != null) {
            delay(1350L)
            visibleReceipt = null
        }
    }
    val current = visibleReceipt ?: return
    val tool = homeFxTool(current.toolId) ?: return
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = HaloShapes.card,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (current.kind == HomeToolReceiptKind.CONFIRMED) {
                key(current.stepId) {
                    HaloToolFx(tool, HaloFxToolPhase.DONE, modifier = Modifier.size(64.dp))
                }
            } else {
                HaloIcon(
                    glyph = if (current.kind == HomeToolReceiptKind.FAILED) HaloGlyph.ERROR else HaloGlyph.TOOL,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = if (current.kind == HomeToolReceiptKind.FAILED)
                        MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
            Column(Modifier.weight(1f)) {
                val title = when (current.kind) {
                    HomeToolReceiptKind.CONFIRMED -> "Confirmed"
                    HomeToolReceiptKind.UNVERIFIED -> "Sent · not verified"
                    HomeToolReceiptKind.FAILED -> "Could not finish"
                }
                Text(uiText(title), style = MaterialTheme.typography.titleSmall)
                Text(uiText(homeFxToolLabel(tool)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
