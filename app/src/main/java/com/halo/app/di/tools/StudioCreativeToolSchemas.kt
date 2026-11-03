package com.halo.app.di.tools

import com.halo.app.data.studio.*

/** These closed data contracts are shared by every model route, never inferred from labels. */
internal object StudioCreativeToolSchemas {
    private fun integer(min:Int,max:Int)=mapOf("type" to "integer","minimum" to min,"maximum" to max)
    private fun enumeration(values:List<String>)=mapOf("type" to "string","enum" to values)
    private fun closed(fields:Map<String,Any>)=mapOf("type" to "object","properties" to fields,"required" to fields.keys.toList(),"additionalProperties" to false)
    val typography=closed(mapOf("family" to enumeration(StudioFont.entries.map { it.name }),"weight" to mapOf("type" to "integer","enum" to listOf(400,700)),
        "italic" to mapOf("type" to "boolean"),"alignment" to enumeration(StudioTextAlignment.entries.map { it.name }),
        "letterSpacing" to integer(-100,500),"lineSpacing" to integer(500,2500)))
    val effect=closed(mapOf("id" to mapOf("type" to "string","pattern" to "^[A-Za-z0-9_-]{1,100}$"),
        "kind" to enumeration(StudioEffectKind.entries.map { it.name }),"enabled" to mapOf("type" to "boolean"),
        "amount" to integer(-1000,1000),"radius" to integer(0,64),"color" to integer(Int.MIN_VALUE,Int.MAX_VALUE),
        "secondaryColor" to integer(Int.MIN_VALUE,Int.MAX_VALUE),"seed" to integer(Int.MIN_VALUE,Int.MAX_VALUE)))
    val fields:Map<String,Any> = mapOf("typography" to typography,"blend" to enumeration(StudioBlend.entries.map { it.name }),
        "effects" to mapOf("type" to "array","maxItems" to 8,"items" to effect),
        "texture" to enumeration(StudioTexture.entries.map { it.name }),"textureSeed" to integer(Int.MIN_VALUE,Int.MAX_VALUE),"strokeOnly" to mapOf("type" to "boolean"))
    val operations=listOf("add_line","add_texture","add_frame","duplicate_layer","group_layers","ungroup_layer","layout_layers","apply_style","fill_area")
    val style=enumeration(StudioCreativePresets.styles.map {it.id})
    val layout=enumeration(StudioLayout.entries.map { it.name })
    val ids=mapOf("type" to "array","minItems" to 1,"maxItems" to StudioDocument.MAX_LAYERS,"uniqueItems" to true,
        "items" to mapOf("type" to "string","pattern" to "^[A-Za-z0-9_-]{1,100}$"))
}
