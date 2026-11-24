package com.halo.app.data.studio

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

internal data class StudioPixelSelection(
    val projectId: String, val projectRevision: Long, val ownerToken: String, val mask: StudioPixelMask,
)

internal enum class StudioPixelOperation(val receipt: String) {
    COPY("studio-pixel-copy"), ERASE("studio-pixel-erase"), SMUDGE("studio-pixel-smudge"),
}

internal sealed interface StudioPixelEdit {
    data class Select(
        val shape: StudioSelectionRequest, val feather: Int, val combine: StudioSelectionCombine, val sampleAll: Boolean = true,
    ) : StudioPixelEdit {
        init { require(feather in 0..200) }
    }
    data object Clear : StudioPixelEdit
    data class Apply(
        val operation: StudioPixelOperation, val name: String? = null, val strokes: List<StudioSmudgeStroke> = emptyList(),
    ) : StudioPixelEdit {
        init {
            require(if (operation == StudioPixelOperation.COPY) name != null && name.isNotBlank() && name.length <= 120 else name == null)
            require(if (operation == StudioPixelOperation.SMUDGE) strokes.size in 1..STUDIO_SMUDGE_DRAFT_LIMIT else strokes.isEmpty())
        }
    }
}

/** Typed and spoken Studio tools receive only explicit user-authored coordinates, in thousandths. */
internal fun studioPixelEdit(operation: String, fields: Map<String, Any?>): StudioPixelEdit? = when (operation) {
    "select_pixels" -> parseStudioPixelSelection(fields)
    "clear_pixel_selection" -> StudioPixelEdit.Clear.also { require(fields.isEmpty()) }
    "copy_selected_pixels" -> {
        require(fields.keys.all { it == "name" })
        val name = if ("name" in fields) fields["name"] as? String ?: error("Expected a layer name") else "Pixel selection"
        StudioPixelEdit.Apply(StudioPixelOperation.COPY, name)
    }
    "erase_selected_pixels" -> StudioPixelEdit.Apply(StudioPixelOperation.ERASE).also { require(fields.isEmpty()) }
    "smudge_pixels" -> {
        require(fields.keys.all { it in setOf("points", "strength", "radius", "wetMix") })
        StudioPixelEdit.Apply(StudioPixelOperation.SMUDGE, strokes = listOf(
            StudioSmudgeStroke(fields.artPoints(), fields.whole("strength", 60, 1..100),
                fields.whole("radius", 30, 4..120), fields.flag("wetMix", false))))
    }
    else -> null
}

private fun parseStudioPixelSelection(fields: Map<String, Any?>): StudioPixelEdit.Select {
    val axes = setOf("shape", "points", "feather", "combine")
    val points = if ("points" in fields) fields.artPoints() else emptyList()
    val kind = fields["shape"] as? String ?: error("Choose a selection shape")
    val shape = when (kind) {
        "rectangle", "ellipse" -> {
            require(fields.keys.all { it in axes } && points.size == 2)
            StudioSelectionRequest.Marquee(points[0], points[1], kind == "ellipse")
        }
        "lasso" -> {
            require(fields.keys.all { it in axes })
            StudioSelectionRequest.Lasso(points)
        }
        "wand" -> {
            require(fields.keys.all { it in axes || it in setOf("tolerance", "contiguous", "sampleAll") } && points.size == 1)
            StudioSelectionRequest.Wand(points.single(), fields.whole("tolerance", 12, 0..100), fields.flag("contiguous", true))
        }
        "subject" -> {
            require(fields.keys.all { it in axes || it in setOf("edgeRefine", "sampleAll") } && points.size <= 1)
            StudioSelectionRequest.Subject(fields.whole("edgeRefine", 3, 0..10), points.singleOrNull())
        }
        else -> error("Unsupported selection shape")
    }
    val combine = if ("combine" in fields) StudioSelectionCombine.valueOf(fields["combine"] as? String ?: error("Expected combine mode"))
        else StudioSelectionCombine.REPLACE
    return StudioPixelEdit.Select(shape, fields.whole("feather", 0, 0..200), combine, fields.flag("sampleAll", true))
}

private fun Map<String, Any?>.artPoints(): List<StudioArtPoint> {
    val points = this["points"] as? List<*> ?: error("Provide explicit artboard coordinates")
    require(points.size <= 384)
    return points.map { point ->
        val fields = point as? Map<*, *> ?: error("Expected an artboard coordinate")
        require(fields.keys == setOf("x", "y"))
        StudioArtPoint(whole(fields["x"], 0..1000) / 1000f, whole(fields["y"], 0..1000) / 1000f)
    }
}

private fun Map<String, Any?>.flag(key: String, default: Boolean): Boolean =
    if (key in this) this[key] as? Boolean ?: error("Expected a Boolean field") else default

private fun Map<String, Any?>.whole(key: String, default: Int, range: IntRange): Int =
    if (key in this) whole(this[key], range) else default

private fun whole(value: Any?, range: IntRange): Int {
    val number = (value as? Number)?.toDouble() ?: error("Expected an integer field")
    require(number.isFinite() && number % 1.0 == 0.0 && number >= range.first && number <= range.last)
    return number.toInt()
}

internal suspend fun maskStudioPixels(bitmap: Bitmap, selection: StudioPixelMask, copy: Boolean): Boolean {
    val line = IntArray(bitmap.width)
    var used = false
    for (y in 0 until bitmap.height) {
        if (y % 32 == 0) coroutineContext.ensureActive()
        bitmap.getPixels(line, 0, bitmap.width, 0, y, bitmap.width, 1)
        for (x in line.indices) {
            val original = line[x]
            val alpha = Color.alpha(original)
            val coverage = selection.cover(x, y, bitmap.width, bitmap.height)
            val nextAlpha = (alpha * (if (copy) coverage else 255 - coverage) + 127) / 255
            if (if (copy) nextAlpha > 0 else nextAlpha != alpha) used = true
            line[x] = if (nextAlpha == 0) 0 else (original and 0x00ffffff) or (nextAlpha shl 24)
        }
        bitmap.setPixels(line, 0, bitmap.width, 0, y, bitmap.width, 1)
    }
    return used
}
