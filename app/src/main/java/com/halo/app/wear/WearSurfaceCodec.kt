package com.halo.app.wear

import com.halo.runtime.cognition.interaction.*
import org.json.JSONArray
import org.json.JSONObject

/** Explicit allowlist: no runtime context, tool arguments, history, or credentials. */
object WearSurfaceCodec {
    const val MAX_BYTES = 12_000
    fun encode(surface: InteractionSurface?): ByteArray {
        val json = JSONObject().put("version", 1)
        if (surface != null) {
            json.put("id", surface.id.value.take(160)).put("kind", surface.kind.name)
                .put("title", surface.title.orEmpty().take(160))
                .put("prompt", surface.message.orEmpty().take(1500))
                .put("expiresAt", surface.expiresAt?.toEpochMilli())
            val confirmation = surface.content as? InteractionContent.Confirmation
            if (confirmation != null) {
                val detail = listOfNotNull(confirmation.subject, confirmation.detail, confirmation.risk?.summary).joinToString("\n")
                json.put("prompt", detail.take(1500)).put("canConfirm", detail.length <= 1500)
            }
            val choice = surface.content as? InteractionContent.Choice
            if (choice != null) {
                json.put("prompt", choice.prompt.take(1500))
                json.put("options", JSONArray().apply { choice.options.take(8).forEach {
                    put(JSONObject().put("id", it.id.take(160)).put("label", it.label.take(160)))
                } })
            }
        }
        return json.toString().toByteArray(Charsets.UTF_8).also { require(it.size <= MAX_BYTES) }
    }
    fun canConfirm(surface: InteractionSurface): Boolean {
        val confirmation = surface.content as? InteractionContent.Confirmation ?: return false
        return listOfNotNull(confirmation.subject, confirmation.detail, confirmation.risk?.summary).joinToString("\n").length <= 1500
    }
    fun response(bytes: ByteArray): InteractionResponse {
        require(bytes.size <= 4096)
        val json = JSONObject(bytes.toString(Charsets.UTF_8))
        require(json.getInt("version") == 1)
        val id = json.getString("id").also { require(it.length in 1..160) }
        val surface = InteractionSurfaceId(id)
        return when (json.getString("response")) {
            "CONFIRM" -> InteractionResponse.Confirmed(surface)
            "DENY" -> InteractionResponse.Denied(surface)
            "CANCEL" -> InteractionResponse.Cancelled(surface)
            "SELECT" -> InteractionResponse.ChoiceSelected(surface,
                setOf(json.getString("option").also { require(it.length in 1..160) }))
            "TEXT" -> InteractionResponse.TextSubmitted(surface,
                json.getString("value").also { require(it.length in 1..800) })
            else -> error("Unsupported watch response")
        }
    }
}
