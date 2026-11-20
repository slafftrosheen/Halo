package com.halo.app.everyday.collectors

import com.halo.app.data.everyday.CollectorCodec
import com.halo.app.data.everyday.OrganizerCodec
import org.json.JSONArray
import org.json.JSONObject

/** Lossless pagination over saved content; continuation carries exact source/content versions in the calling tool. */
object SavedSourcePages {
    const val TEXT_PAGE = 12_000
    fun read(payload: CollectorPayload?, arguments: Map<String, Any?>, content: (String?) -> ArticleContent?): JSONObject {
        val offset = (arguments["offset"] as? Number)?.toInt() ?: 0
        val textOffset = (arguments["textOffset"] as? Number)?.toInt() ?: 0
        val itemId = arguments["articleId"] ?: arguments["itemId"]
        require(offset in 0..100 && textOffset in 0..2_000_000)
        val result = JSONObject().put("untrustedContent", true)
        fun page(text: String): String {
            require(textOffset <= text.length)
            result.put("textOffset", textOffset).put("totalCharacters", text.length)
                .put("nextTextOffset", if (textOffset + TEXT_PAGE < text.length) textOffset + TEXT_PAGE else JSONObject.NULL)
            return text.drop(textOffset).take(TEXT_PAGE)
        }
        fun <T> rows(values: List<T>, id: (T) -> String): List<T> {
            result.put("totalItems", values.size)
            if (itemId != null) { result.put("nextOffset", JSONObject.NULL); return listOf(requireNotNull(values.find { id(it) == itemId })) }
            require(offset <= values.size)
            result.put("nextOffset", if (offset + 5 < values.size) offset + 5 else JSONObject.NULL)
            return values.drop(offset).take(5)
        }
        val selected = when (payload) {
            is CollectorPayload.Articles -> CollectorPayload.Articles(rows(payload.items) { it.id }.map { article ->
                if (itemId == null) article.copy(text = article.text.take(1000)) else {
                    val body = content(article.contentKey)
                    result.put("bodyAvailable", body != null).put("contentStatus", body?.status ?: "FEED_ONLY")
                        .put("articleRetrievedAt", body?.retrievedAt)
                        .put("omittedImages", body?.omittedImages ?: 0)
                        .put("imageLimit", ArticleExtractor.MAX_IMAGES)
                        .put("imageLimitReached", (body?.images ?: article.images).size >= ArticleExtractor.MAX_IMAGES)
                        .put("missingImages", (body?.images ?: article.images).count { it.assetKey == null })
                    article.copy(text = page(body?.text ?: article.text), images = body?.images ?: article.images)
                }
            })
            is CollectorPayload.Local -> CollectorPayload.Local(rows(payload.items) { it.id }.map {
                it.copy(summary = if (itemId == null) it.summary.take(1000) else page(it.summary))
            })
            is CollectorPayload.Warnings -> CollectorPayload.Warnings(rows(payload.items) { it.id }.map {
                it.copy(text = if (itemId == null) it.text.take(1000) else page(it.text))
            })
            is CollectorPayload.Calendar -> {
                result.put("type", "calendar").put("items", JSONArray(rows(payload.preview.records) { it.id }.map {
                    JSONObject(OrganizerCodec.encode(it.copy(details = if (itemId == null) it.details.take(1000) else page(it.details))))
                }))
                null
            }
            else -> payload
        }
        selected?.let { JSONObject(CollectorCodec.encode(it)) }?.let { encoded -> encoded.keys().forEach { result.put(it, encoded.get(it)) } }
        if (payload == null) result.put("available", false)
        return result
    }
}
