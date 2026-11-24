package com.halo.app.ui.notes.v13

/**
 * halo 13.0 · drawings inside notes, and plain block names.
 * A drawing retains its board as the Workspace reference. Presentation metadata identifies the sketch
 * that begins the drawing, including the first sheet when the drawing spans several Canvas cards.
 */
object NoteDrawing {
    const val PREFIX = "canvas-sketch:"
    private val ID = Regex("[A-Za-z0-9-]{1,80}")

    fun ref(canvasId: String, nodeId: String): String {
        require(canvasId.matches(ID) && nodeId.matches(ID))
        return "$PREFIX$canvasId/$nodeId"
    }

    /** Returns (canvasId, nodeId) or null if this REFERENCE is not a drawing. */
    fun parse(refObjectId: String?): Pair<String, String>? {
        if (refObjectId == null || !refObjectId.startsWith(PREFIX)) return null
        val parts = refObjectId.removePrefix(PREFIX).split('/')
        if (parts.size != 2 || !parts.all { it.matches(ID) }) return null
        return parts[0] to parts[1]
    }

    fun target(block: com.halo.app.data.notebook.NoteBlock): Pair<String, String>? =
        parse(block.presentation.drawing)?.takeIf {
            block.type == com.halo.app.data.notebook.NoteBlockType.REFERENCE && it.first == block.refObjectId
        }
}

enum class Depth { SIMPLE, STANDARD, PRO }

/** Plain names for the 11 NoteBlockType values at 99aac2f, and when each shows in the bar. */
object NoteBlocks {
    val label = mapOf(
        "PARAGRAPH" to "mk13_block_text", "HEADING" to "mk13_block_title", "HEADING_2" to "mk13_block_heading",
        "HEADING_3" to "mk13_block_small_heading", "BULLET_LIST" to "mk13_block_list", "NUMBERED_LIST" to "mk13_block_numbered",
        "CHECKLIST" to "mk13_block_tick_list", "QUOTE" to "mk13_block_quote", "CODE" to "mk13_block_code",
        "DIVIDER" to "mk13_block_line", "REFERENCE" to "mk13_block_link",
    )
    /** Five buttons; everything else sits under "More". CODE only at Pro. */
    val bar = listOf("HEADING", "BULLET_LIST", "CHECKLIST", "DRAWING", "MORE")
    fun inMore(type: String, depth: Depth) = when (type) {
        "CODE" -> depth == Depth.PRO
        "HEADING_2", "HEADING_3", "NUMBERED_LIST", "QUOTE", "DIVIDER", "REFERENCE" -> depth != Depth.SIMPLE
        else -> false
    }
    /** "Suggesting" (tracked NoteChange) and fonts are Pro. */
    fun suggestingAvailable(depth: Depth) = depth == Depth.PRO
}
