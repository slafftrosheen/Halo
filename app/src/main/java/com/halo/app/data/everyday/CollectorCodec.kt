package com.halo.app.data.everyday

import com.halo.app.everyday.collectors.*
import com.halo.app.everyday.organizer.*
import java.time.Instant
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

/** Bounded, typed local format. No raw HTML, executable reader state or remote resource references. */
internal object CollectorCodec {
    private fun JSONObject.text(key: String): String? = if (isNull(key)) null else getString(key)
    private fun JSONObject.instant(key: String): Instant? = if (isNull(key)) null else Instant.ofEpochMilli(getLong(key))
    private fun JSONObject.number(key: String): Double? = if (isNull(key)) null else getDouble(key).also { require(it.isFinite()) }
    private fun JSONObject.integer(key: String): Int? = if (isNull(key)) null else getInt(key)
    private fun array(values: List<JSONObject>) = JSONArray(values)
    private fun JSONArray.rows(limit: Int = 100): List<JSONObject> { require(length() <= limit); return (0 until length()).map(::getJSONObject) }
    private fun place(value: WeatherPlace) = JSONObject().put("name", value.name).put("lat", value.latitude).put("lon", value.longitude).put("zone", value.zoneId)
    private fun place(value: JSONObject) = WeatherPlace(value.getString("name"), value.getDouble("lat"), value.getDouble("lon"), value.getString("zone"))
    fun encode(source: CollectorSource): String = JSONObject().put("schema", 1).put("id", source.id).put("title", source.title)
        .put("publisher", source.publisher).put("kind", source.kind.name).put("category", source.category.name).put("language", source.language)
        .put("endpoint", source.endpoint).put("attribution", source.attributionUrl).put("selected", source.selected).put("enabled", source.enabled)
        .put("muted", source.muted).put("refresh", source.refreshMinutes).put("place", source.place?.let(::place))
        .put("childApproved", source.childApproved).put("version", source.version).toString()
    fun source(body: String): CollectorSource = parse(body).let { json ->
        require(json.getInt("schema") == 1)
        CollectorSource(json.getString("id"), json.getString("title"), json.getString("publisher"), CollectorKind.valueOf(json.getString("kind")),
            SourceCategory.valueOf(json.getString("category")), json.getString("language"), json.text("endpoint"), json.text("attribution"),
            json.getBoolean("selected"), json.getBoolean("enabled"), json.getBoolean("muted"), json.getInt("refresh"),
            json.optJSONObject("place")?.let(::place), json.getBoolean("childApproved"), json.getLong("version"))
    }
    fun encode(payload: CollectorPayload): String = JSONObject().put("schema", 1).apply {
        CollectorPayloadLimits.validate(payload)
        when (payload) {
            is CollectorPayload.Articles -> { put("type", "articles"); put("items", array(payload.items.map { item -> JSONObject()
                .put("id", item.id).put("title", item.title).put("text", item.text).put("url", item.url).put("publisher", item.publisher)
                .put("published", item.publishedAt?.toEpochMilli()).put("language", item.language).put("categories", JSONArray(item.categories)) })) }
            is CollectorPayload.Warnings -> { put("type", "warnings"); put("items", array(payload.items.map { item -> JSONObject()
                .put("id", item.id).put("title", item.title).put("text", item.text).put("url", item.url).put("publisher", item.publisher)
                .put("area", item.area).put("severity", item.severity).put("onset", item.onset?.toEpochMilli()).put("expires", item.expires?.toEpochMilli()) })) }
            is CollectorPayload.Local -> { put("type", "local"); put("items", array(payload.items.map { item -> JSONObject()
                .put("id", item.id).put("kind", item.kind).put("title", item.title).put("summary", item.summary).put("route", item.route)
                .put("revision", item.revision).put("at", item.occurredAt?.toEpochMilli()) })) }
            is CollectorPayload.Weather -> { put("type", "weather"); val fact = payload.fact
                put("place", place(fact.place)); put("observed", fact.observedAt?.toEpochMilli()); put("temperature", fact.temperatureC)
                put("code", fact.weatherCode); put("attribution", fact.attribution); put("attributionUrl", fact.attributionUrl)
                put("days", array(fact.days.map { day -> JSONObject().put("date", day.date.toString()).put("max", day.maximumC)
                    .put("min", day.minimumC).put("rain", day.precipitationProbability).put("code", day.weatherCode) })) }
            is CollectorPayload.Calendar -> { put("type", "calendar"); put("records", JSONArray(payload.preview.records.map(OrganizerCodec::encode)))
                put("exceptions", JSONArray(payload.preview.exceptions.map(OrganizerCodec::encode)))
                put("issues", array(payload.preview.issues.map { issue -> JSONObject().put("item", issue.item).put("uid", issue.uid)
                    .put("reason", issue.reason).put("warning", issue.warning) })) }
        }
    }.toString().also { require(it.length <= 4 * 1024 * 1024) }

    fun payload(body: String): CollectorPayload = parse(body).let { json ->
        require(json.getInt("schema") == 1)
        when (json.getString("type")) {
            "articles" -> CollectorPayload.Articles(json.getJSONArray("items").rows().map { item -> ArticleFact(item.getString("id"), item.getString("title"),
                item.getString("text"), item.text("url"), item.getString("publisher"), item.instant("published"), item.getString("language"),
                item.getJSONArray("categories").let { categories -> require(categories.length() <= 20); (0 until categories.length()).map(categories::getString) }) })
            "warnings" -> CollectorPayload.Warnings(json.getJSONArray("items").rows().map { item -> WarningFact(item.getString("id"), item.getString("title"),
                item.getString("text"), item.text("area"), item.text("severity"), item.instant("onset"), item.instant("expires"), item.text("url"), item.getString("publisher")) })
            "local" -> CollectorPayload.Local(json.getJSONArray("items").rows().map { item -> LocalFact(item.getString("id"), item.getString("kind"),
                item.getString("title"), item.getString("summary"), item.getString("route"), item.text("revision"), item.instant("at")) })
            "weather" -> CollectorPayload.Weather(WeatherFact(place(json.getJSONObject("place")), json.instant("observed"), json.number("temperature"), json.integer("code"),
                json.getJSONArray("days").rows(16).map { day -> WeatherDay(LocalDate.parse(day.getString("date")), day.number("max"), day.number("min"), day.integer("rain"), day.integer("code")) },
                json.getString("attribution"), json.getString("attributionUrl")))
            "calendar" -> CollectorPayload.Calendar(CalendarImportPreview(
                json.getJSONArray("records").let { values -> require(values.length() <= 100); (0 until values.length()).map { OrganizerCodec.decode(values.getString(it)) } },
                json.getJSONArray("exceptions").let { values -> require(values.length() <= 2000); (0 until values.length()).map { OrganizerCodec.override(values.getString(it)) } },
                json.getJSONArray("issues").rows(2000).map { item -> CalendarImportIssue(item.getInt("item"), item.text("uid"), item.getString("reason"), item.getBoolean("warning")) }))
            else -> error("Collector snapshot requires a newer Halo version")
        }
    }
    fun snapshot(value: CollectorSnapshotEntity) = CollectorSnapshot(value.sourceId, value.sourceVersion, payload(value.payload).also(CollectorPayloadLimits::validate), value.contentRevision,
        Instant.ofEpochMilli(value.retrievedAt), Instant.ofEpochMilli(value.verifiedAt), Instant.ofEpochMilli(value.expiresAt), value.etag, value.lastModified, value.effectiveUrl)
    fun entity(value: CollectorSnapshot) = CollectorSnapshotEntity(value.sourceId, value.sourceVersion, value.contentRevision, value.retrievedAt.toEpochMilli(),
        value.verifiedAt.toEpochMilli(), value.expiresAt.toEpochMilli(), encode(value.payload), value.etag, value.lastModified, value.effectiveUrl)
    fun attempt(value: CollectorAttemptEntity) = CollectorAttempt(value.sourceId, value.at?.let(Instant::ofEpochMilli), CollectorAttemptState.valueOf(value.state),
        value.consecutiveFailures, value.nextAllowedAt?.let(Instant::ofEpochMilli), value.message)
    fun entity(sourceVersion: Long, value: CollectorAttempt, token: String? = null) = CollectorAttemptEntity(value.sourceId, sourceVersion, token,
        value.at?.toEpochMilli(), value.state.name, value.consecutiveFailures, value.nextAllowedAt?.toEpochMilli(), value.message)
    private fun parse(body: String): JSONObject = SafeCollectorJson.objectValue(body, 4 * 1024 * 1024)
}
