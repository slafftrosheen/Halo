package com.halo.app.screen

import org.json.JSONArray
import org.json.JSONObject

/** Versioned local presentation schema. Existing source references/configuration remain intact. */
internal object ScreenLayoutCodec {
    fun encode(layout: ScreenLayout): String = JSONObject().put("schema", 2).put("id", layout.id)
        .put("name", layout.name).put("version", layout.version)
        .put("appearance", JSONObject().put("font", layout.appearance.font.name)
            .put("background", layout.appearance.background.name).put("translucent", layout.appearance.translucent))
        .put("cards", JSONArray().apply { screenCardIdentities(layout.cards).forEach { card ->
            val type = card.typography.bounded()
            val json = JSONObject().put("type", card.widgetType).put("itemId", card.itemId).put("cardId", card.presentationId)
                .put("size", card.size.name).put("pinned", card.pinned).put("archived", card.archived)
                .put("config", JSONObject(card.config)).put("typography", JSONObject()
                    .put("title", type.titlePercent).put("value", type.valuePercent).put("clock", type.clockPercent))
            card.placement?.bounded()?.let { p -> json.put("placement", JSONObject().put("column", p.column)
                .put("row", p.row).put("width", p.width).put("height", p.height)) }
            put(json)
        } }).toString()

    fun decode(raw: String?): ScreenLayout? = runCatching {
        if (raw == null || raw.length > 512_000) return null
        val json = JSONObject(raw)
        val array = json.getJSONArray("cards")
        val cards = (0 until minOf(array.length(), 24)).mapNotNull { index -> decodeCard(array.optJSONObject(index)) }
        val appearance = json.optJSONObject("appearance")
        ScreenLayout(json.getString("id"), json.getString("name"), screenCardIdentities(cards), json.optInt("version", 1),
            ScreenAppearance(enumValue(appearance?.optString("font"), ScreenFont.APP),
                enumValue(appearance?.optString("background"), ScreenBackground.PLAIN), appearance?.optBoolean("translucent") ?: false))
    }.getOrNull()

    private fun decodeCard(c: JSONObject?): ScreenCard? = runCatching {
        if (c == null) return null
        val type = c.getString("type")
        if (type !in ScreenWidgets.types) return null
        val text = c.optJSONObject("typography")
        val itemId = c.optString("itemId").takeUnless { it.isBlank() || it == "null" }
        ScreenCard(type, itemId,
            CardSize.valueOf(c.getString("size")), c.optBoolean("pinned"),
            c.optJSONObject("config")?.let { config -> config.keys().asSequence().associateWith { config.optString(it) } }.orEmpty(),
            c.optBoolean("archived", false), c.optJSONObject("placement")?.let { p ->
                ScreenPlacement(p.optInt("column"), p.optInt("row"), p.optInt("width", 12), p.optInt("height", 1)).bounded()
            }, ScreenCardTypography(text?.optInt("title", 100) ?: 100, text?.optInt("value", 100) ?: 100,
                text?.optInt("clock", 100) ?: 100).bounded(),
            c.optString("cardId").takeIf { it.isNotBlank() && it != "null" && it.length <= 512 } ?: "$type:${itemId.orEmpty()}")
    }.getOrNull()

    private inline fun <reified T : Enum<T>> enumValue(value: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: fallback
}
