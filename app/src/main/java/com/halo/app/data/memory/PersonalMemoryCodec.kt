package com.halo.app.data.memory

import com.halo.runtime.cognition.memory.*
import org.json.JSONObject
import org.json.JSONArray
import java.time.Instant

/** Bounded public conclusions and opaque references only; no prompts or scratchpads. */
object PersonalMemoryCodec {
    fun evidence(refs: List<MemoryEvidenceRef>) = JSONArray(refs.take(MemoryLimits.EVIDENCE).map { JSONObject().put("kind",it.kind.name).put("id",it.id).put("revision",it.revision) }).toString()
    fun decodeEvidence(raw: String): List<MemoryEvidenceRef> = runCatching {
        require(raw.length <= 6000); val a=JSONArray(raw); require(a.length() <= MemoryLimits.EVIDENCE)
        (0 until a.length()).map { val j=a.getJSONObject(it); MemoryEvidenceRef(MemoryEvidenceKind.valueOf(j.getString("kind")),j.getString("id"),optional(j,"revision")) }
    }.getOrDefault(emptyList())
    private fun optional(j: JSONObject,k: String) = if(j.isNull(k)) null else j.getString(k)
    private fun strings(a: JSONArray) = (0 until a.length()).map { a.getString(it) }
    fun episode(e: Episode) = JSONObject().put("id",e.id.value).put("interaction",e.interactionId).put("run",e.runId).put("start",e.startedAt.toEpochMilli()).put("end",e.endedAt.toEpochMilli())
        .put("outcome",e.outcome.name).put("summary",e.summary).put("objects",JSONArray(e.referencedObjectIds.toList())).put("memories",JSONArray(e.referencedMemoryIds.toList()))
        .put("tools",JSONArray(e.toolIds)).put("unknown",e.unknownCompletion).put("scopeId",e.scopeId).toString()
    fun decodeEpisode(raw: String): Episode { require(raw.length <= 12000);val j=JSONObject(raw);val tools=strings(j.getJSONArray("tools"))
        return Episode(EpisodeId(j.getString("id")),optional(j,"interaction"),optional(j,"run"),Instant.ofEpochMilli(j.getLong("start")),Instant.ofEpochMilli(j.getLong("end")),
            EpisodeOutcome.valueOf(j.getString("outcome")),j.getString("summary"),strings(j.getJSONArray("objects")).toSet(),strings(j.getJSONArray("memories")).toSet(),tools,
            WorkflowSignature.of(tools),j.getBoolean("unknown"),optional(j,"scopeId")) }
    fun proposal(p: MemoryProposal) = JSONObject().put("id",p.id.value).put("content",p.content).put("kind",p.kind.name).put("scope",p.scope.name).put("scopeId",p.scopeId)
        .put("confidence",p.confidence).put("origin",p.origin.name).put("evidence",JSONArray(evidence(p.evidence))).put("conflicts",JSONArray(p.conflictMemoryIds)).put("created",p.createdAt.toEpochMilli())
        .put("sensitivity",p.sensitivity.name).put("subject",p.subject).toString()
    fun decodeProposal(raw: String): MemoryProposal { require(raw.length <= 12000);val j=JSONObject(raw);return MemoryProposal(MemoryProposalId(j.getString("id")),j.getString("content"),MemoryKind.valueOf(j.getString("kind")),
        MemoryScope.valueOf(j.getString("scope")),optional(j,"scopeId"),j.getDouble("confidence").toFloat(),MemoryProposalOrigin.valueOf(j.getString("origin")),decodeEvidence(j.getJSONArray("evidence").toString()),
        strings(j.getJSONArray("conflicts")),Instant.ofEpochMilli(j.getLong("created")),MemorySensitivity.valueOf(j.getString("sensitivity")),j.getString("subject")) }
    fun suggestion(s: PersonalSuggestion) = JSONObject().put("id",s.id).put("fingerprint",s.fingerprint).put("kind",s.kind.name).put("title",s.title.take(80)).put("content",s.content.take(600))
        .put("count",s.evidenceCount).put("first",s.firstObserved.toEpochMilli()).put("last",s.lastObserved.toEpochMilli()).put("confidence",s.confidence).put("reason",s.reason.name).put("target",s.targetId).put("tools",JSONArray(s.tools)).toString()
    fun decodeSuggestion(raw: String): PersonalSuggestion { require(raw.length <= 12000);val j=JSONObject(raw);return PersonalSuggestion(j.getString("id"),j.getString("fingerprint"),PersonalSuggestionKind.valueOf(j.getString("kind")),j.getString("title"),
        j.getString("content"),j.getInt("count"),Instant.ofEpochMilli(j.getLong("first")),Instant.ofEpochMilli(j.getLong("last")),j.getDouble("confidence").toFloat(),PersonalSuggestionReason.valueOf(j.getString("reason")),optional(j,"target"),strings(j.getJSONArray("tools"))) }
}
