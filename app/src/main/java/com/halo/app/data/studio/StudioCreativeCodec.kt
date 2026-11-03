package com.halo.app.data.studio

import org.json.JSONObject

/** Shared strict encoding for persistence and the data-only assistant editing surface. */
object StudioCreativeCodec {
    fun typography(value: StudioTypography): JSONObject = JSONObject().put("family", value.family.name)
        .put("weight", value.weight).put("italic", value.italic).put("alignment", value.alignment.name)
        .put("letterSpacing", value.letterSpacing).put("lineSpacing", value.lineSpacing)
    fun readTypography(value: JSONObject): StudioTypography {
        exact(value, "family", "weight", "italic", "alignment", "letterSpacing", "lineSpacing")
        return StudioTypography(StudioFont.valueOf(text(value,"family")), integer(value,"weight"),
            value.get("italic").let { require(it is Boolean); it }, StudioTextAlignment.valueOf(text(value,"alignment")),
            integer(value,"letterSpacing"), integer(value,"lineSpacing")).also(StudioTypography::requireValid)
    }
    fun effect(value: StudioEffect): JSONObject = JSONObject().put("id", value.id).put("kind", value.kind.name)
        .put("enabled", value.enabled).put("amount", value.amount).put("radius", value.radius)
        .put("color", value.color).put("secondaryColor", value.secondaryColor).put("seed", value.seed)
    fun readEffect(value: JSONObject): StudioEffect {
        exact(value,"id","kind","enabled","amount","radius","color","secondaryColor","seed")
        return StudioEffect(text(value,"id"), StudioEffectKind.valueOf(text(value,"kind")),
            value.get("enabled").let { require(it is Boolean); it }, integer(value,"amount"), integer(value,"radius"),
            integer(value,"color"), integer(value,"secondaryColor"), integer(value,"seed")).also(StudioEffect::requireValid)
    }
    private fun exact(value: JSONObject, vararg keys: String) { require(value.keys().asSequence().toSet() == keys.toSet()) }
    private fun text(value: JSONObject, key: String): String = value.get(key).let { require(it is String); it }
    private fun integer(value: JSONObject, key: String): Int = value.get(key).let {
        require(it is Number); val n = it.toDouble()
        require(n.isFinite() && n % 1 == 0.0 && n in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble()); n.toInt()
    }
}
