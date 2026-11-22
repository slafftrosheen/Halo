package com.halo.app.data.notebook

import org.json.JSONArray
import org.json.JSONObject

data class CanvasReviewPin(
    val author: String,
    val text: String,
    val replies: List<NoteCommentReply> = emptyList(),
    val resolved: Boolean = false,
) {
    fun encode(): String = JSONObject().put("author", author).put("text", text)
        .put("replies", JSONArray(replies.map(NoteCommentReply::encode))).put("resolved", resolved).toString()

    fun valid() = author.length <= 24 && text.isNotBlank() && text.length <= 1200 && replies.size <= 25 &&
        replies.all { it.author.length <= 24 && it.text.isNotBlank() && it.text.length <= 1200 }

    companion object {
        fun decode(raw: String): CanvasReviewPin? = runCatching {
            val json = JSONObject(raw)
            val rows = json.optJSONArray("replies") ?: JSONArray()
            val replies = (0 until rows.length().coerceAtMost(25)).mapNotNull { index ->
                val reply = rows.optJSONObject(index) ?: return@mapNotNull null
                NoteCommentReply(reply.optString("author"), reply.optString("text"))
            }
            CanvasReviewPin(json.optString("author"), json.optString("text"), replies, json.optBoolean("resolved"))
                .takeIf(CanvasReviewPin::valid)
        }.getOrNull()
    }
}
