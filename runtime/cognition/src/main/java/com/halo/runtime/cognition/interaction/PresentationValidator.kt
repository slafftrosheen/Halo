package com.halo.runtime.cognition.interaction

/**
 * MINING-P2-001 presentation validator.
 *
 * A model-generated presentation is untrusted presentation data. Every structural rule is enforced
 * here, before anything is published or persisted, and a structurally invalid payload is **rejected
 * with a bounded reason list** — never silently truncated, because a half-valid UI tree would
 * misrepresent what the model actually asked for.
 *
 * Depth, node count, total text and list sizes are hard ceilings so a hostile or confused model
 * cannot turn one tool call into an unbounded render job.
 */
object PresentationValidator {

    sealed interface Result {
        data class Valid(val nodeCount: Int, val totalTextChars: Int) : Result
        data class Invalid(val reasons: List<String>) : Result
    }

    /** Structural rules that need external knowledge the tree itself does not carry. */
    fun interface ChoiceBindingResolver {
        /**
         * Returns true when [inputRequestId] names a durable input request whose option ids include
         * every id in [optionIds]. A generated surface may only resolve an existing request.
         */
        fun allows(inputRequestId: String, optionIds: Set<String>): Boolean
    }

    /** Strict mode: no choice node may be present at all (used when no run is bound). */
    val NO_CHOICES: ChoiceBindingResolver = ChoiceBindingResolver { _, _ -> false }

    fun validate(
        presentation: StructuredPresentation,
        choices: ChoiceBindingResolver = NO_CHOICES,
    ): Result {
        if (presentation.schemaVersion > CURRENT_PRESENTATION_SCHEMA_VERSION) {
            return Result.Invalid(listOf("unknown presentation schema version ${presentation.schemaVersion}"))
        }
        if (presentation.schemaVersion < 1) {
            return Result.Invalid(listOf("presentation schema version must be positive"))
        }
        val reasons = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        var nodeCount = 0
        var textChars = 0

        fun visit(node: PresentationNode, depth: Int) {
            nodeCount++
            if (depth > PresentationLimits.MAX_DEPTH) {
                if (reasons.none { it.startsWith("presentation exceeds") }) {
                    reasons += "presentation exceeds maximum depth of ${PresentationLimits.MAX_DEPTH}"
                }
                return
            }
            if (node.nodeId.isBlank()) reasons += "node id must not be blank"
            if (!seen.add(node.nodeId)) reasons += "duplicate node id ${node.nodeId.take(24)}"

            fun bounded(value: String, limit: Int, what: String): Boolean {
                if (value.any { it.code < 32 && it !in setOf('\n', '\r', '\t') }) reasons += "$what contains unsupported control characters"
                textChars += value.length
                if (value.length > limit) {
                    reasons += "$what exceeds $limit characters"
                    return false
                }
                return true
            }

            when (node) {
                is PresentationNode.Text -> {
                    if (node.text.isBlank()) reasons += "text node must not be blank"
                    bounded(node.text, PresentationLimits.MAX_TEXT_CHARS, "text")
                }
                is PresentationNode.Heading -> {
                    if (node.text.isBlank()) reasons += "heading must not be blank"
                    bounded(node.text, PresentationLimits.MAX_HEADING_CHARS, "heading")
                }
                is PresentationNode.Caption -> {
                    if (node.text.isBlank()) reasons += "caption must not be blank"
                    bounded(node.text, PresentationLimits.MAX_CAPTION_CHARS, "caption")
                }
                is PresentationNode.KeyValue -> {
                    if (node.label.isBlank()) reasons += "key/value label must not be blank"
                    bounded(node.label, PresentationLimits.MAX_LABEL_CHARS, "key/value label")
                    bounded(node.value, PresentationLimits.MAX_VALUE_CHARS, "key/value value")
                }
                is PresentationNode.Badge -> {
                    if (node.label.isBlank()) reasons += "badge label must not be blank"
                    bounded(node.label, PresentationLimits.MAX_LABEL_CHARS, "badge label")
                }
                is PresentationNode.Status -> {
                    if (node.label.isBlank()) reasons += "status label must not be blank"
                    bounded(node.label, PresentationLimits.MAX_LABEL_CHARS, "status label")
                    node.detail?.let { bounded(it, PresentationLimits.MAX_DETAIL_CHARS, "status detail") }
                }
                is PresentationNode.Progress -> {
                    if (node.label.isBlank()) reasons += "progress label must not be blank"
                    bounded(node.label, PresentationLimits.MAX_LABEL_CHARS, "progress label")
                    node.detail?.let { bounded(it, PresentationLimits.MAX_DETAIL_CHARS, "progress detail") }
                    node.progress?.let { value ->
                        if (value.isNaN() || value < 0f || value > 1f) {
                            reasons += "progress must be between 0 and 1 or null"
                        }
                    }
                }
                is PresentationNode.BulletList -> {
                    if (node.items.isEmpty()) reasons += "bullet list must not be empty"
                    if (node.items.size > PresentationLimits.MAX_LIST_ITEMS) {
                        reasons += "bullet list exceeds ${PresentationLimits.MAX_LIST_ITEMS} items"
                    }
                    node.items.forEach { item ->
                        if (item.isBlank()) reasons += "bullet list items must not be blank"
                        bounded(item, PresentationLimits.MAX_LIST_ITEM_CHARS, "bullet list item")
                    }
                }
                is PresentationNode.Checklist -> {
                    if (node.items.isEmpty()) reasons += "checklist must not be empty"
                    if (node.items.size > PresentationLimits.MAX_CHECKLIST_ITEMS) {
                        reasons += "checklist exceeds ${PresentationLimits.MAX_CHECKLIST_ITEMS} items"
                    }
                    val stepIds = node.items.map { it.stepId }
                    if (stepIds.size != stepIds.toSet().size) reasons += "checklist step ids must be unique"
                    node.items.forEach { item ->
                        if (item.stepId.isBlank()) reasons += "checklist step id must not be blank"
                        if (item.text.isBlank()) reasons += "checklist item text must not be blank"
                        bounded(item.text, PresentationLimits.MAX_LIST_ITEM_CHARS, "checklist item")
                    }
                    node.label?.let { bounded(it, PresentationLimits.MAX_LABEL_CHARS, "checklist label") }
                }
                is PresentationNode.Comparison -> validateComparison(node, reasons) { text ->
                    textChars += text.length
                }
                is PresentationNode.ImageReference -> {
                    if (node.assetId.isBlank()) {
                        reasons += "image reference requires an asset id"
                    } else {
                        bounded(node.assetId, PresentationLimits.MAX_ASSET_ID_CHARS, "image asset id")
                        // A remote source is not representable, and a smuggled one is rejected loudly.
                        val lowered = node.assetId.lowercase()
                        if (lowered.startsWith("http://") || lowered.startsWith("https://") ||
                            lowered.startsWith("file:") || lowered.startsWith("content:") ||
                            lowered.startsWith("javascript:") || lowered.startsWith("intent:")
                        ) {
                            reasons += "image reference must use a Halo asset id, not an external source"
                        }
                    }
                    node.caption?.let { bounded(it, PresentationLimits.MAX_CAPTION_CHARS, "image caption") }
                }
                is PresentationNode.Divider -> Unit
                is PresentationNode.Row -> if (node.children.isEmpty()) {
                    reasons += "row must contain at least one child"
                } else {
                    node.children.forEach { visit(it, depth + 1) }
                }
                is PresentationNode.Column -> if (node.children.isEmpty()) {
                    reasons += "column must contain at least one child"
                } else {
                    node.children.forEach { visit(it, depth + 1) }
                }
                is PresentationNode.Card -> {
                    if (node.children.isEmpty()) reasons += "card must contain at least one child"
                    node.title?.let { bounded(it, PresentationLimits.MAX_TITLE_CHARS, "card title") }
                    node.children.forEach { visit(it, depth + 1) }
                }
                is PresentationNode.ChoiceGroup -> {
                    if (node.inputRequestId.isBlank()) {
                        reasons += "choice group must reference a durable input request"
                    }
                    if (node.prompt.isBlank()) reasons += "choice group prompt must not be blank"
                    bounded(node.prompt, PresentationLimits.MAX_TEXT_CHARS, "choice prompt")
                    if (node.options.isEmpty()) reasons += "choice group requires at least one option"
                    if (node.options.size > PresentationLimits.MAX_CHOICES) {
                        reasons += "choice group exceeds ${PresentationLimits.MAX_CHOICES} options"
                    }
                    val optionIds = node.options.map { it.optionId }
                    if (optionIds.size != optionIds.toSet().size) reasons += "choice option ids must be unique"
                    node.options.forEach { option ->
                        if (option.optionId.isBlank()) reasons += "choice option id must not be blank"
                        if (option.label.isBlank()) reasons += "choice option label must not be blank"
                        bounded(option.label, PresentationLimits.MAX_LABEL_CHARS, "choice option label")
                        option.description?.let { bounded(it, PresentationLimits.MAX_CAPTION_CHARS, "choice option description") }
                    }
                    if (node.inputRequestId.isNotBlank() && optionIds.all { it.isNotBlank() } &&
                        !choices.allows(node.inputRequestId, optionIds.toSet())
                    ) {
                        reasons += "choice group references an unknown or mismatched input request"
                    }
                    if (node.allowMultiple && node.options.size > PresentationLimits.MAX_CHOICES) {
                        reasons += "multi-select choice group exceeds ${PresentationLimits.MAX_CHOICES} options"
                    }
                }
            }
        }

        visit(presentation.root, depth = 1)

        if (nodeCount > PresentationLimits.MAX_NODE_COUNT) {
            reasons += "presentation exceeds ${PresentationLimits.MAX_NODE_COUNT} nodes"
        }
        if (textChars > PresentationLimits.MAX_TOTAL_TEXT_CHARS) {
            reasons += "presentation exceeds ${PresentationLimits.MAX_TOTAL_TEXT_CHARS} text characters"
        }

        return if (reasons.isEmpty()) {
            Result.Valid(nodeCount = nodeCount, totalTextChars = textChars)
        } else {
            // Bounded failure: the caller reports a few reasons, never the whole tree.
            Result.Invalid(reasons.distinct().take(MAX_REPORTED_REASONS))
        }
    }

    private fun validateComparison(
        node: PresentationNode.Comparison,
        reasons: MutableList<String>,
        account: (String) -> Unit,
    ) {
        if (node.columns.isEmpty()) reasons += "comparison requires at least one column"
        if (node.columns.size > PresentationLimits.MAX_COMPARISON_COLUMNS) {
            reasons += "comparison exceeds ${PresentationLimits.MAX_COMPARISON_COLUMNS} columns"
        }
        if (node.rows.isEmpty()) reasons += "comparison requires at least one row"
        if (node.rows.size > PresentationLimits.MAX_COMPARISON_ROWS) {
            reasons += "comparison exceeds ${PresentationLimits.MAX_COMPARISON_ROWS} rows"
        }
        node.columns.forEach { column ->
            if (column.isBlank()) reasons += "comparison column labels must not be blank"
            account(column)
            if (column.length > PresentationLimits.MAX_COMPARISON_CELL_CHARS) {
                reasons += "comparison column label exceeds ${PresentationLimits.MAX_COMPARISON_CELL_CHARS} characters"
            }
        }
        node.rows.forEach { row ->
            // Each row carries a leading label cell plus one cell per column.
            if (row.size != node.columns.size + 1) {
                if (reasons.none { it.startsWith("comparison rows must") }) {
                    reasons += "comparison rows must contain a label plus one cell per column"
                }
            }
            row.forEach { cell ->
                account(cell)
                if (cell.isEmpty()) reasons += "comparison cells must not be empty"
                if (cell.length > PresentationLimits.MAX_COMPARISON_CELL_CHARS) {
                    reasons += "comparison cell exceeds ${PresentationLimits.MAX_COMPARISON_CELL_CHARS} characters"
                }
            }
        }
    }

    const val MAX_REPORTED_REASONS = 6
}
