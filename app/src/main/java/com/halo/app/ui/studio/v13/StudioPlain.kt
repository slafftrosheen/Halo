package com.halo.app.ui.studio.v13

/**
 * halo 13.0 · Studio in plain words, and smudge without stopping.
 * Mirrors data/studio at 99aac2f: StudioPreset, StudioShapeOperation, StudioFillStyle, StudioSymmetryAxis,
 * StudioProjectFormat, StudioDocument.MAX_LAYERS = 32, smudge batches of 12 strokes.
 */
object StudioPlain {
    val starts = linkedMapOf("SQUARE" to "mk13_studio_square", "POSTCARD" to "mk13_studio_postcard",
        "PORTRAIT" to "mk13_studio_tall", "BLANK" to "mk13_studio_blank", "PHOTO" to "mk13_studio_from_photo")
    val shapeOps = mapOf("WELD" to "mk13_studio_join", "TRIM" to "mk13_studio_cut_out", "INTERSECT" to "mk13_studio_overlap")
    val fills = mapOf("SOLID" to "mk13_studio_fill_solid", "LINEAR" to "mk13_studio_fill_across", "RADIAL" to "mk13_studio_fill_middle")
    val symmetry = mapOf("VERTICAL" to "mk13_studio_mirror_lr", "HORIZONTAL" to "mk13_studio_mirror_tb", "RADIAL" to "mk13_studio_kaleidoscope")
    val formats = mapOf("SVG" to "mk13_studio_shapes_file", "DXF" to "mk13_studio_cutter_file", "PDF" to "mk13_studio_pdf", "HALO" to "mk13_studio_project")
    const val LAYERS_TITLE = "mk13_studio_things"
}

/**
 * The engine still applies smudge in batches of at most 12 strokes (StudioSmudge requires 1..12).
 * 13.0 hides that: when the 12th stroke ends, the batch is applied in the background and a new one starts.
 * Undo stays per stroke: an applied batch keeps its strokes so undo can re-apply the batch without its last stroke.
 */
class SmudgeQueue(private val apply: (List<List<Pair<Float, Float>>>) -> Unit) {
    companion object { const val ENGINE_BATCH = 12 }
    private val pending = mutableListOf<List<Pair<Float, Float>>>()
    val history = mutableListOf<List<List<Pair<Float, Float>>>>()
    val pendingCount get() = pending.size
    /** Always true: the user can keep smudging. */
    val canDraw get() = true

    fun add(stroke: List<Pair<Float, Float>>) {
        require(stroke.isNotEmpty())
        pending += stroke
        if (pending.size == ENGINE_BATCH) flush()
    }
    fun flush() {
        if (pending.isEmpty()) return
        val batch = pending.toList(); pending.clear(); history += batch; apply(batch)
    }
}
