package com.halo.app.data.everyday

import com.halo.app.everyday.briefing.*
import com.halo.app.everyday.collectors.CollectorAttemptState
import com.halo.app.everyday.collectors.SafeCollectorJson
import java.time.*
import org.json.JSONArray
import org.json.JSONObject

internal object BriefCodec {
    private fun parse(body: String) = SafeCollectorJson.objectValue(body, 8 * 1024 * 1024, maximumTokens = 1_000_000).also { require(it.getInt("schema") == 1) }
    private fun JSONObject.text(key: String): String? = if (isNull(key)) null else getString(key)
    private fun JSONObject.instant(key: String): Instant? = if (isNull(key)) null else Instant.ofEpochMilli(getLong(key))
    private fun JSONArray.rows(limit: Int) = (0 until length().also { require(it <= limit) }).map(::getJSONObject)
    private fun JSONArray.strings(limit: Int) = (0 until length().also { require(it <= limit) }).map(::getString)
    private fun objectValue() = JSONObject().put("schema", 1)
    private fun fact(value: BriefFact) = JSONObject().put("id", value.id).put("kind", value.kind.name).put("title", value.title)
        .put("detail", value.detail).put("source", value.sourceId).put("revision", value.revision).put("route", value.route)
        .put("priority", value.priority).put("at", value.at?.toEpochMilli()).put("url", value.externalUrl).put("values", JSONObject(value.values))
    private fun fact(value: JSONObject) = BriefFact(value.getString("id"), BriefFactKind.valueOf(value.getString("kind")), value.getString("title"),
        value.getString("detail"), value.getString("source"), value.getString("revision"), value.getString("route"), value.getInt("priority"),
        value.instant("at"), value.text("url"), value.getJSONObject("values").let { fields ->
            require(fields.length() <= 30); fields.keys().asSequence().associateWith { fields.getString(it).also { text -> require(text.length <= 2000) } }
        }).also { require(it.id.length <= 2500 && it.title.length <= 1000 && it.detail.length <= 16000 && it.route.length <= 4096) }
    private fun stamp(value: BriefSourceStamp) = JSONObject().put("id", value.id).put("version", value.version).put("title", value.title)
        .put("publisher", value.publisher).put("url", value.attributionUrl).put("external", value.external).put("retrieved", value.retrievedAt?.toEpochMilli())
        .put("verified", value.verifiedAt?.toEpochMilli()).put("expires", value.expiresAt?.toEpochMilli()).put("attempt", value.attempt.name).put("child", value.childApproved)
    private fun stamp(value: JSONObject) = BriefSourceStamp(value.getString("id"), value.getLong("version"), value.getString("title"), value.getString("publisher"),
        value.text("url"), value.getBoolean("external"), value.instant("retrieved"), value.instant("verified"), value.instant("expires"),
        CollectorAttemptState.valueOf(value.getString("attempt")), value.getBoolean("child"))
    fun encode(value: EverydayBrief): String = objectValue().put("id", value.id).put("period", value.period.name).put("audience", value.audience.name)
        .put("date", value.date.toString()).put("zone", value.zoneId).put("at", value.generatedAt.toEpochMilli())
        .put("facts", JSONArray(value.facts.map(::fact))).put("sources", JSONArray(value.sources.map(::stamp)))
        .put("changes", JSONArray(value.changes.map { change -> JSONObject().put("kind", change.kind.name).put("id", change.factId)
            .put("source", change.sourceId).put("fact", change.fact?.let(::fact)) }))
        .put("baselineAt", value.baselineAt?.toEpochMilli()).put("missing", value.baselineMissing)
        .put("uncompared", JSONArray(value.comparisonUnavailableSources.sorted())).put("omitted", value.omittedFacts)
        .put("omittedChanges", value.omittedChanges)
        .put("fingerprint", value.inputFingerprint).toString().also { require(it.length <= 8 * 1024 * 1024) }
    fun document(body: String): EverydayBrief = parse(body).let { value -> EverydayBrief(value.getString("id"), BriefPeriod.valueOf(value.getString("period")),
        BriefAudience.valueOf(value.getString("audience")), LocalDate.parse(value.getString("date")), ZoneId.of(value.getString("zone")).id,
        Instant.ofEpochMilli(value.getLong("at")), value.getJSONArray("facts").rows(40).map(::fact), value.getJSONArray("sources").rows(101).map(::stamp),
        value.getJSONArray("changes").rows(200).map { change -> BriefChange(BriefChangeKind.valueOf(change.getString("kind")), change.getString("id"),
            change.getString("source"), change.optJSONObject("fact")?.let(::fact)) }, value.instant("baselineAt"), value.getBoolean("missing"),
        value.getJSONArray("uncompared").strings(101).toSet(), value.getInt("omitted"), value.getInt("omittedChanges"), value.getString("fingerprint")) }
    fun encode(value: BriefBaseline): String = objectValue().put("date", value.date.toString()).put("zone", value.zoneId).put("audience", value.audience.name)
        .put("at", value.capturedAt.toEpochMilli()).put("sources", JSONArray(value.observedSources.sorted())).put("facts", JSONArray(value.facts.map { fact ->
            JSONObject().put("id", fact.id).put("source", fact.sourceId).put("revision", fact.revision).put("kind", fact.kind.name) })).toString()
    fun baseline(body: String) = parse(body).let { value -> BriefBaseline(LocalDate.parse(value.getString("date")), ZoneId.of(value.getString("zone")).id,
        BriefAudience.valueOf(value.getString("audience")), Instant.ofEpochMilli(value.getLong("at")), value.getJSONArray("facts").rows(20000).map { fact ->
            BriefFingerprint(fact.getString("id"), fact.getString("source"), fact.getString("revision"), BriefFactKind.valueOf(fact.getString("kind"))) },
        value.getJSONArray("sources").strings(101).toSet()) }
    fun encode(value: BriefSettings): String = objectValue().put("version", value.version).put("proactive", value.proactive)
        .put("morning", value.morning.toString()).put("evening", value.evening.toString()).put("quiet", value.quietEnabled)
        .put("quietStart", value.quietStart.toString()).put("quietEnd", value.quietEnd.toString()).put("headlines", value.headlineLimit).put("tasks", value.taskLimit)
        .put("pinned", JSONArray(value.pinnedRecordIds.sorted())).put("childRecords", JSONArray(value.childRecordIds.sorted())).toString()
    fun settings(body: String) = parse(body).let { value -> BriefSettings(value.getLong("version"), value.getBoolean("proactive"),
        LocalTime.parse(value.getString("morning")), LocalTime.parse(value.getString("evening")), value.getBoolean("quiet"),
        LocalTime.parse(value.getString("quietStart")), LocalTime.parse(value.getString("quietEnd")), value.getInt("headlines"), value.getInt("tasks"),
        value.getJSONArray("pinned").strings(100).toSet(), value.getJSONArray("childRecords").strings(100).toSet()) }
}
