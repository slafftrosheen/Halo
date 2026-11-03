package com.halo.app.data.studio

import java.util.UUID

/** Portable local capabilities; every advertised value has a renderer implementation. */
enum class StudioBlend { NORMAL, MULTIPLY, SCREEN, OVERLAY, DARKEN, LIGHTEN, ADD }
enum class StudioFont { SANS, SERIF, MONOSPACE }
enum class StudioTextAlignment { START, CENTER, END }
data class StudioTypography(val family: StudioFont = StudioFont.SANS, val weight: Int = 400,
    val italic: Boolean = false, val alignment: StudioTextAlignment = StudioTextAlignment.START,
    val letterSpacing: Int = 0, val lineSpacing: Int = 1000) {
    fun requireValid() { require(weight in setOf(400,700) && letterSpacing in -100..500 && lineSpacing in 500..2500) }
}
enum class StudioTexture { PAPER, CANVAS, CONCRETE, FABRIC, FILM_GRAIN, DUST, SCRATCHES, NOISE }
enum class StudioEffectKind {
    BRIGHTNESS, CONTRAST, EXPOSURE, SATURATION, TEMPERATURE, TINT, HIGHLIGHTS, SHADOWS,
    GRAYSCALE, DUOTONE, BLUR, SHARPEN, GRAIN, VIGNETTE, POSTERIZE, PIXELATE, EMBOSS,
    EDGES, GLOW, DROP_SHADOW, INNER_SHADOW, BLOOM, FLARE, LIGHT_LEAK, HAZE, GRADIENT_LIGHT, REFLECTION
}
data class StudioEffect(val id: String = UUID.randomUUID().toString(), val kind: StudioEffectKind,
    val enabled: Boolean = true, val amount: Int = 500, val radius: Int = 8,
    val color: Int = 0xffffbf80.toInt(), val secondaryColor: Int = 0xff18203a.toInt(), val seed: Int = 1) {
    fun requireValid() { require(id.matches(Regex("[A-Za-z0-9_-]{1,100}")) && amount in -1000..1000 && radius in 0..64) }
}
enum class StudioLayout { ALIGN_LEFT, ALIGN_CENTER, ALIGN_RIGHT, ALIGN_TOP, ALIGN_MIDDLE, ALIGN_BOTTOM,
    DISTRIBUTE_HORIZONTAL, DISTRIBUTE_VERTICAL }
enum class StudioMode { DESIGN, PAINT, AI }
enum class StudioBrush { PEN, PENCIL, MARKER, WASH }
data class StudioBrushPreset(val id: StudioBrush, val width: Int, val opacity: Int)
data class StudioStylePreset(val id:String,val color:Int,val opacity:Int,val typography:StudioTypography)
object StudioCreativePresets {
    val brushes = listOf(StudioBrushPreset(StudioBrush.PEN, 5, 1000), StudioBrushPreset(StudioBrush.PENCIL, 2, 650),
        StudioBrushPreset(StudioBrush.MARKER, 25, 450), StudioBrushPreset(StudioBrush.WASH, 40, 180))
    val palette = listOf(0xff18203a.toInt(), 0xffffffff.toInt(), 0xffee6c66.toInt(), 0xffffc857.toInt(),
        0xff5bbda8.toInt(), 0xff648add.toInt(), 0xffb18ad1.toInt(), 0xffbd8d67.toInt())
    val styles=listOf(StudioStylePreset("EDITORIAL",0xff18203a.toInt(),1000,StudioTypography(StudioFont.SERIF,700)),
        StudioStylePreset("WARM",0xffbd6858.toInt(),1000,StudioTypography(StudioFont.SANS,700)),
        StudioStylePreset("CAPTION",0xff525769.toInt(),850,StudioTypography(StudioFont.MONOSPACE,400)))
    fun styled(layer:StudioLayer,id:String):StudioLayer {
        val style=requireNotNull(styles.find {it.id==id})
        require(layer.kind !in setOf(StudioLayerKind.IMAGE,StudioLayerKind.GROUP)) {"Choose a text, shape, paint or texture layer"}
        return layer.copy(color=style.color,opacity=style.opacity,typography=if(layer.kind==StudioLayerKind.TEXT) style.typography else layer.typography)
    }
}

fun StudioDocument.ancestors(layer: StudioLayer): List<StudioLayer> {
    val result = mutableListOf<StudioLayer>()
    var parent = layer.parentId
    while (parent != null) {
        require(result.size < 4 && result.none { it.id == parent }) { "Invalid layer hierarchy" }
        val group = requireNotNull(layers.find { it.id == parent && it.kind == StudioLayerKind.GROUP })
        result += group; parent = group.parentId
    }
    return result
}
fun StudioDocument.isEditable(layer: StudioLayer): Boolean = !layer.locked && ancestors(layer).none { it.locked }
fun StudioDocument.isVisible(layer: StudioLayer): Boolean = layer.visible && ancestors(layer).all { it.visible && it.opacity > 0 }
fun StudioDocument.descendants(id: String): List<StudioLayer> = layers.filter { layer -> ancestors(layer).any { it.id == id } }

/** Global project coordinates keep a group/ungroup lossless without baking pixels. */
internal fun StudioDocument.transformChildren(old: StudioLayer, updated: StudioLayer): List<StudioLayer> {
    if (old.kind != StudioLayerKind.GROUP || old.bounds == updated.bounds && old.rotation == updated.rotation) return layers
    val children = descendants(old.id).map { it.id }.toSet()
    val angle = Math.toRadians((updated.rotation - old.rotation).toDouble())
    val cosine = kotlin.math.cos(angle); val sine = kotlin.math.sin(angle)
    return layers.map { child ->
        if (child.id !in children) child else {
            val dx = (child.bounds.x + child.bounds.width / 2.0 - old.bounds.x) * updated.bounds.width / old.bounds.width - updated.bounds.width / 2.0
            val dy = (child.bounds.y + child.bounds.height / 2.0 - old.bounds.y) * updated.bounds.height / old.bounds.height - updated.bounds.height / 2.0
            val width = (child.bounds.width.toDouble() * updated.bounds.width / old.bounds.width).toInt().coerceAtLeast(1)
            val height = (child.bounds.height.toDouble() * updated.bounds.height / old.bounds.height).toInt().coerceAtLeast(1)
            child.copy(bounds = StudioRect((updated.bounds.x + updated.bounds.width / 2.0 + dx*cosine - dy*sine - width/2.0).toInt(),
                (updated.bounds.y + updated.bounds.height / 2.0 + dx*sine + dy*cosine - height/2.0).toInt(), width, height),
                rotation = ((child.rotation + updated.rotation - old.rotation + 540) % 360) - 180)
        }
    }
}
