package com.halo.runtime.cognition.interaction

/**
 * MINING-P2-001 bounded presentation codec.
 *
 * `runtime:cognition` is a plain JVM library with no JSON dependency (and adding one for a closed,
 * tiny tree would be the wrong trade). This is a deliberately minimal, dependency-free line format
 * used for one thing only: carrying a validated presentation inside committed assistant-response
 * metadata so Home can rebuild it after process death.
 *
 * Properties that matter:
 *
 * - **Total**: [decode] returns null for anything it does not fully understand. Callers then render
 *   the text fallback. A newer schema, a truncated payload or a hostile string can never crash a
 *   conversation.
 * - **Bounded**: input is capped at [PresentationLimits.MAX_PAYLOAD_CHARS] before any parsing work.
 * - **Lossless for the closed catalog**: every node field has one unambiguous reading.
 *
 * Structural validation is still [PresentationValidator]'s job; the codec only round-trips.
 */
object PresentationCodec {

    private const val RECORD_SEPARATOR = '\u001E'
    private const val UNIT_SEPARATOR = '\u001F'
    private const val FIELD_EQUALS = '='
    private const val VERSION_HEADER = "v1"

    fun encode(presentation: StructuredPresentation): String {
        val builder = StringBuilder()
        builder.append(VERSION_HEADER)
        writeNode(builder, presentation.root, depth = 1)
        return builder.toString()
    }

    /** Returns null when the payload is missing, oversized, truncated or unrecognized. */
    fun decode(payload: String?): StructuredPresentation? {
        val raw = payload?.trim().orEmpty()
        if (raw.isEmpty() || raw.length > PresentationLimits.MAX_PAYLOAD_CHARS) return null
        val records = raw.split(RECORD_SEPARATOR)
        if (records.isEmpty() || records.first() != VERSION_HEADER) return null
        val root = readNode(records, index = 1) ?: return null
        if (root.nextIndex != records.size) return null
        return StructuredPresentation(root = root.node)
    }

    // ---- writing -------------------------------------------------------------

    private fun writeNode(builder: StringBuilder, node: PresentationNode, depth: Int) {
        val fields = mutableListOf("$depth", node.nodeId, typeOf(node))
        when (node) {
            is PresentationNode.Text -> fields += listOf("t", node.text, "em", node.emphasis.name)
            is PresentationNode.Heading -> fields += listOf("t", node.text)
            is PresentationNode.Caption -> fields += listOf("t", node.text)
            is PresentationNode.KeyValue -> fields += listOf("l", node.label, "v", node.value)
            is PresentationNode.Badge -> fields += listOf("l", node.label)
            is PresentationNode.Status -> {
                fields += listOf("l", node.label, "s", node.state.name)
                node.detail?.let { fields += listOf("d", it) }
            }
            is PresentationNode.Progress -> {
                fields += listOf("l", node.label, "s", node.state.name)
                fields += listOf("p", node.progress?.toString() ?: "-")
                node.detail?.let { fields += listOf("d", it) }
            }
            is PresentationNode.BulletList -> node.items.forEach { fields += listOf("i", it) }
            is PresentationNode.Checklist -> {
                node.label?.let { fields += listOf("l", it) }
                node.items.forEach { item -> fields += listOf("c", "${item.stepId}\u001D${item.state.name}\u001D${item.text}") }
            }
            is PresentationNode.Comparison -> {
                node.columns.forEach { fields += listOf("col", it) }
                node.rows.forEach { row -> fields += listOf("row", row.joinToString("\u001D")) }
            }
            is PresentationNode.ImageReference -> {
                fields += listOf("a", node.assetId)
                node.caption?.let { fields += listOf("c", it) }
            }
            is PresentationNode.Divider -> Unit
            is PresentationNode.Row -> Unit
            is PresentationNode.Column -> Unit
            is PresentationNode.Card -> node.title?.let { fields += listOf("t", it) }
            is PresentationNode.ChoiceGroup -> {
                fields += listOf("q", node.inputRequestId, "pr", node.prompt, "m", node.allowMultiple.toString())
                node.options.forEach { option ->
                    fields += listOf(
                        "o",
                        "${option.optionId}\u001D${option.label}\u001D${option.description.orEmpty()}",
                    )
                }
            }
        }
        builder.append(RECORD_SEPARATOR)
        builder.append(fields.joinToString(UNIT_SEPARATOR.toString()) { encodeToken(it) })
        val children = childrenOf(node)
        children.forEach { child -> writeNode(builder, child, depth + 1) }
    }

    private fun typeOf(node: PresentationNode): String = when (node) {
        is PresentationNode.Text -> "TEXT"
        is PresentationNode.Heading -> "HEADING"
        is PresentationNode.Caption -> "CAPTION"
        is PresentationNode.KeyValue -> "KEYVALUE"
        is PresentationNode.Badge -> "BADGE"
        is PresentationNode.Status -> "STATUS"
        is PresentationNode.Progress -> "PROGRESS"
        is PresentationNode.BulletList -> "BULLETS"
        is PresentationNode.Checklist -> "CHECKLIST"
        is PresentationNode.Comparison -> "COMPARISON"
        is PresentationNode.ImageReference -> "IMAGE"
        is PresentationNode.Divider -> "DIVIDER"
        is PresentationNode.Row -> "ROW"
        is PresentationNode.Column -> "COLUMN"
        is PresentationNode.Card -> "CARD"
        is PresentationNode.ChoiceGroup -> "CHOICE"
    }

    private fun childrenOf(node: PresentationNode): List<PresentationNode> = when (node) {
        is PresentationNode.Row -> node.children
        is PresentationNode.Column -> node.children
        is PresentationNode.Card -> node.children
        else -> emptyList()
    }

    /** Only the format's own separators and the escape character are escaped. */
    private fun encodeToken(value: String): String = value
        .replace("%", "%25")
        .replace(RECORD_SEPARATOR.toString(), "%1E")
        .replace(UNIT_SEPARATOR.toString(), "%1F")
        .replace("\n", "%0A")
        .replace("\r", "%0D")

    private fun decodeToken(value: String): String = value
        .replace("%1E", RECORD_SEPARATOR.toString())
        .replace("%1F", UNIT_SEPARATOR.toString())
        .replace("%0A", "\n")
        .replace("%0D", "\r")
        // Percent is decoded last so a literal "%25" cannot become a separator.
        .replace("%25", "%")

    // ---- reading -------------------------------------------------------------

    private data class Read(val node: PresentationNode, val nextIndex: Int)

    private fun readNode(records: List<String>, index: Int): Read? {
        val record = records.getOrNull(index) ?: return null
        val tokens = record.split(UNIT_SEPARATOR).map(::decodeToken)
        if (tokens.size < 3) return null
        val depth = tokens[0].toIntOrNull() ?: return null
        if (depth !in 1..PresentationLimits.MAX_DEPTH || index > PresentationLimits.MAX_NODE_COUNT) return null
        val nodeId = tokens[1]
        val type = tokens[2]
        val fields = mutableListOf<Pair<String, String>>()
        var cursor = 3
        while (cursor < tokens.size) {
            val key = tokens[cursor]
            val value = tokens.getOrNull(cursor + 1)
            if (value == null) return null
            fields += key to value
            cursor += 2
        }
        fun first(key: String): String? = fields.firstOrNull { it.first == key }?.second
        fun all(key: String): List<String> = fields.filter { it.first == key }.map { it.second }

        var nextIndex = index + 1

        fun readChildren(): List<PresentationNode>? {
            val children = mutableListOf<PresentationNode>()
            while (true) {
                val peek = records.getOrNull(nextIndex) ?: break
                val peekTokens = peek.split(UNIT_SEPARATOR)
                val peekDepth = peekTokens.firstOrNull()?.let { decodeToken(it) }?.toIntOrNull() ?: return null
                if (peekDepth <= depth) break
                if (peekDepth != depth + 1) return null
                val child = readNode(records, nextIndex) ?: return null
                children += child.node
                nextIndex = child.nextIndex
            }
            return children
        }

        val node: PresentationNode = when (type) {
            "TEXT" -> PresentationNode.Text(
                nodeId = nodeId,
                text = first("t") ?: return null,
                emphasis = runCatching { PresentationEmphasis.valueOf(first("em") ?: "NORMAL") }
                    .getOrDefault(PresentationEmphasis.NORMAL),
            )
            "HEADING" -> PresentationNode.Heading(nodeId, first("t") ?: return null)
            "CAPTION" -> PresentationNode.Caption(nodeId, first("t") ?: return null)
            "KEYVALUE" -> PresentationNode.KeyValue(nodeId, first("l") ?: return null, first("v") ?: return null)
            "BADGE" -> PresentationNode.Badge(nodeId, first("l") ?: return null)
            "STATUS" -> PresentationNode.Status(
                nodeId,
                first("l") ?: return null,
                runCatching { PresentationStatusState.valueOf(first("s") ?: "NEUTRAL") }
                    .getOrDefault(PresentationStatusState.NEUTRAL),
                first("d"),
            )
            "PROGRESS" -> PresentationNode.Progress(
                nodeId,
                first("l") ?: return null,
                first("p")?.takeIf { it != "-" }?.toFloatOrNull(),
                runCatching { PresentationStatusState.valueOf(first("s") ?: "INFO") }
                    .getOrDefault(PresentationStatusState.INFO),
                first("d"),
            )
            "BULLETS" -> PresentationNode.BulletList(nodeId, all("i"))
            "CHECKLIST" -> PresentationNode.Checklist(
                nodeId,
                all("c").mapNotNull { raw ->
                    val parts = raw.split('\u001D')
                    if (parts.size < 3) return@mapNotNull null
                    PresentationChecklistItem(
                        stepId = parts[0],
                        state = runCatching { PresentationStepState.valueOf(parts[1]) }
                            .getOrDefault(PresentationStepState.PENDING),
                        text = parts[2],
                    )
                },
                first("l"),
            )
            "COMPARISON" -> PresentationNode.Comparison(
                nodeId,
                all("col"),
                all("row").map { it.split('\u001D') },
            )
            "IMAGE" -> PresentationNode.ImageReference(nodeId, first("a") ?: return null, first("c"))
            "DIVIDER" -> PresentationNode.Divider(nodeId)
            "ROW" -> PresentationNode.Row(nodeId, readChildren() ?: return null)
            "COLUMN" -> PresentationNode.Column(nodeId, readChildren() ?: return null)
            "CARD" -> PresentationNode.Card(nodeId, readChildren() ?: return null, first("t"))
            "CHOICE" -> PresentationNode.ChoiceGroup(
                nodeId,
                first("q") ?: return null,
                first("pr") ?: return null,
                all("o").mapNotNull { raw ->
                    val parts = raw.split('\u001D')
                    if (parts.size < 2) return@mapNotNull null
                    PresentationChoice(
                        optionId = parts[0],
                        label = parts[1],
                        description = parts.getOrNull(2)?.takeIf { it.isNotEmpty() },
                    )
                },
                first("m")?.toBooleanStrictOrNull() ?: false,
            )
            else -> return null
        }
        return Read(node = node, nextIndex = nextIndex)
    }
}
