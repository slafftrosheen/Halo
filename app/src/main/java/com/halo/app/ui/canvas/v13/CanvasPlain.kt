package com.halo.app.ui.canvas.v13

/**
 * halo 13.0 · Canvas in plain words. Keeps all 12 CanvasNodeType values; shows them as 5 verbs.
 * Names mirror com.halo.app.data.notebook.CanvasNodeType at repo 99aac2f.
 */
enum class Depth { SIMPLE, STANDARD, PRO }

enum class CanvasVerb(val labelKey: String, val minDepth: Depth) {
    WRITE("mk13_canvas_write", Depth.SIMPLE),
    DRAW("mk13_canvas_draw", Depth.SIMPLE),
    PICTURE("mk13_canvas_picture", Depth.SIMPLE),
    SHAPE("mk13_canvas_shape", Depth.STANDARD),
    CONNECT("mk13_canvas_connect", Depth.STANDARD),
}

enum class CanvasStart(val labelKey: String) { BLANK("mk13_start_blank"), MIND_MAP("mk13_start_mind_map"), MOODBOARD("mk13_start_moodboard"), COMPARE("mk13_start_compare") }

object CanvasPlain {
    /** Which verb creates a node type. GROUP, TAG and COMMENT live in the long-press menu; FRAME under "More" (Pro). */
    fun verbFor(nodeType: String): CanvasVerb? = when (nodeType) {
        "TEXT", "NOTE", "STICKY" -> CanvasVerb.WRITE
        "SKETCH", "INK_SELECTION" -> CanvasVerb.DRAW
        "IMAGE", "FILE" -> CanvasVerb.PICTURE
        "SHAPE", "FRAME" -> CanvasVerb.SHAPE
        else -> null
    }

    fun bar(depth: Depth, kids: Boolean): List<CanvasVerb> =
        CanvasVerb.entries.filter { (if (kids) Depth.SIMPLE else depth) >= it.minDepth }

    fun framesVisible(depth: Depth, kids: Boolean) = !kids && depth == Depth.PRO

    /** Tidy up only ever proposes. It needs at least 6 loose cards and is offered at most once per board per day. */
    fun offerTidyUp(looseCards: Int, offeredToday: Boolean, depth: Depth) =
        depth != Depth.SIMPLE && looseCards >= 6 && !offeredToday
}
