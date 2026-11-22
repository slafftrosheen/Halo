package com.halo.app.data.notebook

import org.json.JSONObject

enum class CanvasShapeKind(val label: String) { RECTANGLE("Rectangle"), ELLIPSE("Ellipse"), DIAMOND("Diamond"), ARROW("Arrow") }

data class CanvasVisualShape(
    val kind: CanvasShapeKind = CanvasShapeKind.RECTANGLE,
    val fill: Int? = 0xFFBDA9F6.toInt(),
    val outline: Int = 2,
    val corner: Int = 8,
) {
    fun encode(): String {
        require(outline in 0..12 && corner in 0..60)
        return JSONObject().put("kind", kind.name).put("fill", fill ?: JSONObject.NULL)
            .put("outline", outline).put("corner", corner).toString()
    }

    companion object {
        fun decode(raw: String): CanvasVisualShape? = runCatching {
            val source = JSONObject(raw)
            CanvasVisualShape(CanvasShapeKind.valueOf(source.getString("kind")),
                if (source.isNull("fill")) null else source.getInt("fill"), source.getInt("outline"), source.getInt("corner"))
                .also { require(it.outline in 0..12 && it.corner in 0..60) }
        }.getOrNull()
    }
}

enum class CanvasFramePreset(val label: String, val width: Float, val height: Float) {
    FREE("Free", 584f, 440f), A4("A4", 456f, 645f), WIDE("16:9", 640f, 360f), PHONE("Phone", 336f, 672f),
}

internal fun CanvasNodeType.isCanvasContainer(): Boolean = this == CanvasNodeType.GROUP || this == CanvasNodeType.FRAME

internal fun CanvasNode.containsCenter(other: CanvasNode): Boolean =
    other.id != id && other.x + other.width / 2 in x..(x + width) && other.y + other.height / 2 in y..(y + height)
