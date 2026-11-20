package com.halo.app.everyday.briefing

import com.halo.app.everyday.collectors.CollectorConflict
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import org.json.JSONArray
import org.json.JSONObject

data class DayContextItem(val id: String, val title: String, val at: Instant?, val version: String,
    val reference: Map<String, Any?> = emptyMap())
data class DayContextSnapshot(val sections: Map<String, List<DayContextItem>>, val coverage: Map<String, String>)

/** A paged read of canonical owners, not a summary generator or another orchestration engine. */
class DayContextReader(private val load: suspend (Instant, Instant) -> DayContextSnapshot,
    private val body: suspend (String, DayContextItem) -> String?) {
    suspend fun read(arguments: Map<String, Any?>): JSONObject {
        val from = Instant.parse(arguments["from"] as String)
        val until = Instant.parse(arguments["until"] as String)
        require(until > from && Duration.between(from, until) <= Duration.ofHours(48))
        val zone = ZoneId.of(arguments["zoneId"] as? String ?: ZoneId.systemDefault().id)
        val section = arguments["section"] as? String ?: "MANIFEST"
        val offset = (arguments["offset"] as? Number)?.toInt() ?: 0
        val textOffset = (arguments["textOffset"] as? Number)?.toInt() ?: 0
        require(section in SECTIONS + "MANIFEST" && offset in 0..10000 && textOffset in 0..2_000_000)
        val snapshot = load(from, until)
        val version = version(snapshot, from, until)
        if (arguments["sourceVersion"] != null && arguments["sourceVersion"] != version) throw CollectorConflict("Day context changed; read a new manifest")
        if (section != "MANIFEST") require(arguments["sourceVersion"] != null)
        val result = JSONObject().put("sourceVersion", version).put("from", from.toString()).put("until", until.toString())
            .put("zoneId", zone.id).put("untrustedContent", true).put("coverage", JSONObject(snapshot.coverage))
        if (section == "MANIFEST") return result.put("sections", JSONArray(SECTIONS.map {
            JSONObject().put("section", it).put("count", snapshot.sections[it].orEmpty().size).put("offset", 0)
        })).put("instructions", "Read every nonempty section using this sourceVersion. Follow nextOffset. Index text is not full content: read itemId and nextTextOffset, and source_get/source_image for source references. Cite origins, distinguish unknown dates, and state missing or unread coverage. This read does not refresh external sources.")
        val rows = snapshot.sections[section].orEmpty()
        val itemId = arguments["itemId"] as? String
        val selected = if (itemId != null) listOf(requireNotNull(rows.find { it.id == itemId })) else rows.drop(offset).take(12)
        require(offset <= rows.size)
        result.put("section", section).put("totalItems", rows.size)
            .put("nextOffset", if (itemId == null && offset + 12 < rows.size) offset + 12 else JSONObject.NULL)
            .put("items", JSONArray(selected.map { item ->
                JSONObject().put("id", item.id).put("title", item.title).put("at", item.at?.toString()).put("version", item.version)
                    .put("reference", JSONObject(item.reference)).apply {
                        if (itemId != null) {
                            val text = body(section, item)
                            require(textOffset <= (text?.length ?: 0))
                            put("text", text?.drop(textOffset)?.take(TEXT_PAGE)).put("totalCharacters", text?.length ?: 0)
                            put("nextTextOffset", if (text != null && textOffset + TEXT_PAGE < text.length) textOffset + TEXT_PAGE else JSONObject.NULL)
                        }
                    }
            }))
        // Bodies may be read outside a database transaction. Revalidate every owner before disclosing them.
        if (version(load(from, until), from, until) != version) throw CollectorConflict("Day context changed while reading")
        return result
    }
    private fun version(snapshot: DayContextSnapshot, from: Instant, until: Instant): String {
        val encoded = buildString {
            append(from).append('|').append(until)
            snapshot.coverage.toSortedMap().forEach { (key, value) -> append('\n').append(key).append(':').append(value) }
            snapshot.sections.toSortedMap().forEach { (section, rows) ->
                append('\n').append(section)
                rows.forEach { item ->
                    append('\n').append(item.id).append('|').append(item.version).append('|').append(item.at)
                    append('|').append(JSONObject.quote(item.title))
                    append('|').append(JSONObject(item.reference.toSortedMap()).toString())
                }
            }
        }
        return MessageDigest.getInstance("SHA-256").digest(encoded.toByteArray()).joinToString("") { "%02x".format(it) }
    }
    companion object {
        const val TEXT_PAGE = 12000
        val SECTIONS = listOf("SOURCES", "ORGANIZER", "WORKSPACE", "NOTIFICATIONS", "ACTIVITY")
    }
}
