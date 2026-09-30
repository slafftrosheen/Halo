package com.halo.runtime.cognition.interaction

import com.halo.runtime.cognition.agent.AgentRunId

/**
 * MINING-P2-001 adaptive interaction surfaces.
 *
 * Halo already owns [InteractionSurface]. This file extends its vocabulary with a **closed**,
 * versioned structured-presentation node set — it does not add a second surface system and it is
 * deliberately not A2UI.
 *
 * Hard rules encoded by the types below:
 *
 * - The catalog is closed. There is no generic container, no `component` escape hatch, and no node
 *   that carries code, a callback, an Intent, a URL to fetch, or a tool name.
 * - Nothing here is execution truth. A presentation is a VIEW over durable state
 *   ([InteractionSurface], `AgentRun`, `AgentStep`). It cannot grant approval, run a tool, change
 *   settings, create a routine, send a message or delete memory.
 * - A user choice only ever references an **already durable** input request. [ChoiceGroup] carries an
 *   `inputRequestId` and option ids that must map to legal [InteractionResponse] values; it can never
 *   invent an action.
 * - An [ImageReference] carries a Halo-owned `assetId`, never a remote URL.
 *
 * Harvested conceptually from kotlin-adk's A2UI (versioned UI messages, closed component catalog,
 * create/update/delete surface concepts, host renderer registry). Its durable-state model, arbitrary
 * action dispatch and generic model-generated Compose were deliberately rejected.
 */

/** Explicit schema version. A renderer that meets a newer version falls back to text. */
const val HALO_PRESENTATION_SCHEMA_V1: Int = 1
const val CURRENT_PRESENTATION_SCHEMA_VERSION: Int = HALO_PRESENTATION_SCHEMA_V1

/** Text emphasis. Hosts map this to their own typography; the model never picks colors. */
enum class PresentationEmphasis { NORMAL, STRONG, MUTED }

/** Portable status state. Never an arbitrary color. */
enum class PresentationStatusState { NEUTRAL, INFO, SUCCESS, WARNING, ERROR }

/** Checklist states, shared with plan presentation (PENDING/ACTIVE/COMPLETED/FAILED/SKIPPED). */
enum class PresentationStepState { PENDING, ACTIVE, COMPLETED, FAILED, SKIPPED }

/**
 * One bounded option in a [PresentationNode.ChoiceGroup].
 *
 * [optionId] must be a legal choice id of the bound durable input request. There is no action field
 * of any kind — no tool name, no Intent, no URI, no callback.
 */
data class PresentationChoice(
    val optionId: String,
    val label: String,
    val description: String? = null,
)

/** One checklist/list row. [stepId] is a stable id for update-in-place projection. */
data class PresentationChecklistItem(
    val stepId: String,
    val text: String,
    val state: PresentationStepState,
)

/**
 * The closed presentation catalog (v1). Every node is a bounded data class; no node can execute
 * anything.
 */
sealed interface PresentationNode {

    /** Stable node identity, used for update-in-place and for duplicate detection. */
    val nodeId: String

    data class Text(
        override val nodeId: String,
        val text: String,
        val emphasis: PresentationEmphasis = PresentationEmphasis.NORMAL,
    ) : PresentationNode

    data class Heading(
        override val nodeId: String,
        val text: String,
    ) : PresentationNode

    data class Caption(
        override val nodeId: String,
        val text: String,
    ) : PresentationNode

    data class KeyValue(
        override val nodeId: String,
        val label: String,
        val value: String,
    ) : PresentationNode

    data class Badge(
        override val nodeId: String,
        val label: String,
    ) : PresentationNode

    data class Status(
        override val nodeId: String,
        val label: String,
        val state: PresentationStatusState,
        val detail: String? = null,
    ) : PresentationNode

    /** [progress] is null for indeterminate. Never NaN, never outside 0..1. */
    data class Progress(
        override val nodeId: String,
        val label: String,
        val progress: Float? = null,
        val state: PresentationStatusState = PresentationStatusState.INFO,
        val detail: String? = null,
    ) : PresentationNode

    data class BulletList(
        override val nodeId: String,
        val items: List<String>,
    ) : PresentationNode

    data class Checklist(
        override val nodeId: String,
        val items: List<PresentationChecklistItem>,
        val label: String? = null,
    ) : PresentationNode

    /** Constrained comparison. Not an HTML table; bounded rows, columns and cell length. */
    data class Comparison(
        override val nodeId: String,
        val columns: List<String>,
        val rows: List<List<String>>,
    ) : PresentationNode

    /**
     * Reference to a Halo-owned or already-authorized asset. [assetId] is resolved by the host
     * through existing workspace/image systems; a remote URL is not representable here.
     */
    data class ImageReference(
        override val nodeId: String,
        val assetId: String,
        val caption: String? = null,
    ) : PresentationNode

    data class Divider(override val nodeId: String) : PresentationNode

    data class Row(
        override val nodeId: String,
        val children: List<PresentationNode>,
    ) : PresentationNode

    data class Column(
        override val nodeId: String,
        val children: List<PresentationNode>,
    ) : PresentationNode

    data class Card(
        override val nodeId: String,
        val children: List<PresentationNode>,
        val title: String? = null,
    ) : PresentationNode

    /**
     * A choice surface that resolves an ALREADY DURABLE input request. If [inputRequestId] does not
     * exist, the surface is rejected: a generated surface can never create new execution capability.
     */
    data class ChoiceGroup(
        override val nodeId: String,
        val inputRequestId: String,
        val prompt: String,
        val options: List<PresentationChoice>,
        val allowMultiple: Boolean = false,
    ) : PresentationNode
}

/** A complete, bounded structured presentation. */
data class StructuredPresentation(
    val schemaVersion: Int = CURRENT_PRESENTATION_SCHEMA_VERSION,
    val root: PresentationNode,
)

/**
 * Where a presentation came from. Kept for expanded Activity/debug detail — never shown prominently
 * to normal users.
 */
data class PresentationProvenance(
    val sourceRunId: AgentRunId? = null,
    val sourceInteractionId: String? = null,
    val providerId: String? = null,
    val modelId: String? = null,
    val sourceToolInvocationId: String? = null,
    val generatedAtMs: Long = 0L,
) {
    /** Bounded, content-free line for debug surfaces. */
    fun debugLine(): String = buildString {
        append("schema=").append(CURRENT_PRESENTATION_SCHEMA_VERSION)
        sourceToolInvocationId?.let { append(" tool=").append(it.take(24)) }
        providerId?.let { append(" provider=").append(it.take(40)) }
        modelId?.let { append(" model=").append(it.take(60)) }
    }
}

/**
 * High-level presentation templates (Part Z).
 *
 * A 0.6B model must not have to design layouts. A template is a tiny, flat, deterministic recipe that
 * Halo expands into nodes. `CUSTOM` additionally accepts an explicit node tree for more capable
 * models — under exactly the same validator limits.
 */
enum class PresentationTemplate {
    KEY_VALUE_SUMMARY,
    COMPARISON,
    CHECKLIST,
    SEARCH_RESULTS,
    STATUS,
    CHOICE,
    CUSTOM,
    ;

    companion object {
        fun parse(raw: String?): PresentationTemplate? = when (raw?.trim()?.uppercase()) {
            null, "" -> null
            "KEY_VALUE_SUMMARY", "KEYVALUE", "KEY_VALUE" -> KEY_VALUE_SUMMARY
            "COMPARISON", "COMPARE" -> COMPARISON
            "CHECKLIST", "STEPS", "PLAN" -> CHECKLIST
            "SEARCH_RESULTS", "SEARCH" -> SEARCH_RESULTS
            "STATUS" -> STATUS
            "CHOICE" -> CHOICE
            "CUSTOM" -> CUSTOM
            else -> null
        }
    }
}

/**
 * A flat, model-friendly description of what to present. This is what `halo_present` accepts; Halo
 * expands it deterministically through the templates instead of trusting a nested tree by default.
 */
data class PresentationDirective(
    val template: PresentationTemplate,
    val title: String? = null,
    val subtitle: String? = null,
    /** KEY_VALUE_SUMMARY / STATUS / COMPARISON rows or STATUS facts. */
    val entries: List<PresentationDirectiveEntry> = emptyList(),
    /** CHECKLIST / SEARCH_RESULTS items. */
    val items: List<PresentationDirectiveItem> = emptyList(),
    /** COMPARISON columns, excluding the leading row-label column. */
    val columns: List<String> = emptyList(),
    val status: PresentationStatusState = PresentationStatusState.INFO,
    val progress: Float? = null,
    /** CHOICE only: the durable input request this choice resolves. */
    val inputRequestId: String? = null,
    val allowMultiple: Boolean = false,
    /** CUSTOM only: the explicit tree, validated under the same limits. */
    val customRoot: PresentationNode? = null,
    /** Trailing prose conclusion; also the TTS/fallback text for the whole presentation. */
    val conclusion: String? = null,
)

data class PresentationDirectiveEntry(
    val label: String,
    val value: String,
    val state: PresentationStatusState = PresentationStatusState.INFO,
)

data class PresentationDirectiveItem(
    val text: String,
    val state: PresentationStepState = PresentationStepState.PENDING,
    val detail: String? = null,
)

/** Hard bounds. The validator enforces these; the renderer relies on them. */
object PresentationLimits {
    const val MAX_DEPTH = 6
    const val MAX_NODE_COUNT = 120
    const val MAX_TOTAL_TEXT_CHARS = 6_000
    const val MAX_LIST_ITEMS = 20
    const val MAX_CHECKLIST_ITEMS = 20
    const val MAX_COMPARISON_ROWS = 12
    const val MAX_COMPARISON_COLUMNS = 5
    const val MAX_COMPARISON_CELL_CHARS = 120
    const val MAX_CHOICES = 6
    const val MAX_TEXT_CHARS = 700
    const val MAX_HEADING_CHARS = 140
    const val MAX_CAPTION_CHARS = 300
    const val MAX_LABEL_CHARS = 80
    const val MAX_VALUE_CHARS = 300
    const val MAX_DETAIL_CHARS = 300
    const val MAX_ASSET_ID_CHARS = 160
    const val MAX_LIST_ITEM_CHARS = 300
    const val MAX_TITLE_CHARS = 140

    /** Refuses a payload before it is even parsed, so a huge body never allocates a tree. */
    const val MAX_PAYLOAD_CHARS = 24_000
}
