package com.halo.app.di.tools

import com.halo.app.data.everyday.OrganizerCommitmentCodec
import com.halo.app.everyday.*
import com.halo.app.everyday.organizer.*
import com.halo.app.everyday.collectors.*
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.time.*

class OrganizerOperationTools(private val owner: OrganizerOperations, private val sources: CollectorRepository,
    private val research: OrganizerResearch, private val canAccess: () -> Boolean, private val authority: () -> (() -> Unit),
    private val clock: Clock = Clock.systemUTC(), private val elapsedMs: () -> Long, private val bootId: () -> String,
    private val ready: suspend () -> Unit = {}, private val readState: suspend () -> Any = { Unit },
    private val retrieveArticle: suspend (Map<String, Any?>) -> JSONObject = { error("Article adapter is unavailable") }) : ToolExecutor {
    override fun definitions() = OrganizerOperationContracts.definitions
    override suspend fun execute(toolCall: ToolCall) = execute(toolCall, ToolExecutionContext())
    override suspend fun execute(toolCall: ToolCall, context: ToolExecutionContext): ToolExecutionResult = withContext(Dispatchers.IO) {
        var enteredWrite = false
        try {
            val definition = definitions().find { it.name == toolCall.functionName } ?: throw IllegalArgumentException("Unknown operation")
            EverydayToolContracts.validate(toolCall.arguments, definition.parameters)
            ready(); require(canAccess()) { "Open an unlocked adult Organizer session" }
            val check = authority(); check()
            val query = definition.name in OrganizerOperationContracts.queries
            require(query || !context.interactionId.isNullOrBlank())
            val read = if (query) EverydayReadAuthority.capture() else null
            val a = toolCall.arguments
            if (definition.name == "halo_commitment_action" && a["action"] != "PROMOTE") require(a.keys == setOf("id", "version", "action"))
            if ("id" in a && !query) {
                require("version" in a)
                if (definition.name != "halo_research_source") owner.organizer.requireCurrent(a.getValue("id") as String, (a.getValue("version") as Number).toLong())
            } else require("version" !in a)
            val rawResult = EverydayWriteAuthority.run(check) {
                enteredWrite = !query
                dispatch(definition.name, a)
            }
            check(); ensureActive(); read?.let(EverydayReadAuthority::requireCurrent)
            val result = if (query) EverydayToolReadback.project(definition.name, a, rawResult,
                "${readState()}|$read|${owner.organizer.repository.records(true).map { it.id to it.version }}|${sources.states().map { Triple(it.source.id,it.source.version,it.snapshot?.contentRevision) }}") else EverydayToolReadback.receipt(definition.name, rawResult)
            val encoded = result.toString(); check(encoded.length <= 128000)
            EverydayToolContracts.validate(EverydayResultContracts.value(result), OrganizerOperationContracts.result(definition.name))
            check(); ensureActive(); read?.let(EverydayReadAuthority::requireCurrent)
            ToolExecutionResult.Success(toolCall.id, toolCall.functionName, encoded)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            if (enteredWrite) ToolExecutionResult.Failed(toolCall.id, toolCall.functionName,
                "The operation did not produce a confirmed receipt. Read the exact Organizer/source state before continuing; never replay this write.",
                completionUnknown = true, code = "ORGANIZER_OPERATION_UNCERTAIN")
            else ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, "Organizer authority, arguments or exact source changed. Read the current state and review the operation.", ToolRejectionKind.RESOURCE_UNAVAILABLE)
        }
    }
    private fun Map<String, Any?>.text(key: String) = getValue(key) as String
    private fun Map<String, Any?>.long(key: String) = (getValue(key) as Number).toLong()
    private suspend fun dispatch(name: String, a: Map<String, Any?>): JSONObject = when (name) {
        "halo_commitments_query" -> {
            val all = owner.organizer.repository.records()
            val section = (a["section"] as? String)?.takeUnless { it == "ALL" }
            val zone = ZoneId.systemDefault(); val now = clock.instant(); val from = now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()
            val occurrenceIds = if (section == "TODAY") owner.organizer.agenda(from, from.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()).filter { !it.completed }.map { it.record.id }.toSet() else null
            val outstanding = mutableSetOf<String>()
            if (section == "TODAY") all.filter { it.kind == OrganizerKind.REMINDER }.forEach { record ->
                if (com.halo.app.everyday.briefing.OutstandingReminders.project(record, owner.organizer.repository.overrides(record.id), owner.organizer.repository.receipts(record.id), from, zone).isNotEmpty()) outstanding += record.id
            }
            val eligible = if (section == null) all else OrganizerSections.records(OrganizerSection.valueOf(section), all, now, zone, occurrenceIds, outstanding)
            val terms = (a["query"] as? String).orEmpty().trim().lowercase(java.util.Locale.ROOT).split(Regex("\\s+")).filter { it.isNotBlank() }
            val rows = eligible.filter { row -> (a["id"] == null || row.id == a["id"]) && (a["owner"] == null || row.commitment.owner.name == a["owner"]) && terms.all { it in "${row.title} ${row.details} ${row.commitment.intent}".lowercase(java.util.Locale.ROOT) } }
            val page = EverydayToolReadback.pageRows(rows.map { summary(it) }, "records", a)
            page.put("capabilities", JSONArray(owner.capabilities.list().map { JSONObject().put("id", it.id).put("argumentsSchemaJson", JSONObject(it.argumentSchema).toString()) }))
        }
        "halo_commitment_save" -> {
            val old = (a["id"] as? String)?.let { owner.organizer.requireCurrent(it, a.long("version")) }
            require(old == null || old.commitment.execution == null) { "Stop the current plan before editing it" }
            val responsibility = CommitmentOwner.valueOf(a.text("owner")); val purpose = CommitmentPurpose.valueOf(a.text("purpose"))
            require(old == null || old.commitment.owner == responsibility) { "Use an explicit ownership transition" }
            val actions = (a["actions"] as? List<*>).orEmpty().map { row -> (row as Map<*, *>).let { OrganizerPlannedAction(it["capability"] as String, it["argumentsJson"] as String) } }
            if (actions.isNotEmpty()) owner.capabilities.validate(actions)
            val refs = (a["sources"] as? List<*>).orEmpty().map { row -> (row as Map<*, *>).let { OrganizerSourceRef(it["id"] as String, (it["version"] as Number).toLong()) } }
            refs.forEach { ref -> val source = requireNotNull(sources.state(ref.id)); require(source.source.version == ref.version && source.source.selected && source.source.enabled && !source.source.muted) }
            val saved = owner.saveDefinition(old?.id, old?.version, a.text("title"), (a["intent"] as? String).orEmpty(), responsibility, purpose, actions, refs)
            receipt(saved)
        }
        "halo_commitment_action" -> {
            val id = a.text("id"); val version = a.long("version")
            val value = when (val action = a.text("action")) {
                "PREPARE" -> owner.prepare(id, version)
                "ARM" -> owner.arm(id, version)
                "RUN_NOW" -> owner.runNow(id, version)
                "STOP" -> owner.stop(id, version)
                "TAKE_OVER" -> owner.transfer(id, version, CommitmentOwner.ME)
                "DELEGATE" -> owner.transfer(id, version, CommitmentOwner.HALO)
                "MAKE_SHARED" -> owner.transfer(id, version, CommitmentOwner.SHARED)
                "WATCH" -> owner.watch(id, version)
                "PROMOTE" -> owner.promote(id, version, (a["targetKind"] as? String)?.let(OrganizerKind::valueOf) ?: OrganizerKind.TASK,
                    (a["time"] as? Map<*, *>)?.let { time -> OrganizerTime(LocalDateTime.parse(time["start"] as String),
                        (time["end"] as? String)?.let(LocalDateTime::parse), time["zoneId"] as String, time["allDay"] == true, time["followDeviceZone"] == true) },
                    (a["owner"] as? String)?.let(CommitmentOwner::valueOf), (a["purpose"] as? String)?.let(CommitmentPurpose::valueOf) ?: CommitmentPurpose.STANDARD)
                "ACKNOWLEDGE" -> owner.acknowledge(id, version)
                "ACKNOWLEDGE_RECOVERY" -> owner.acknowledgeRecovery(id, version)
                else -> owner.organizer.stopwatch(id, version, action)
            }
            receipt(value)
        }
        "halo_research_query" -> EverydayToolReadback.pageRows(
            research.search(a.text("query"), (a["limit"] as? Number)?.toInt() ?: 10).map { e -> JSONObject().put("id", e.id).put("title", e.title).put("url", e.canonicalUrl)
                .put("sourceId", e.sourceId).put("sourceVersion", e.sourceVersion).put("provider", e.provider).put("sourceType", e.sourceType)
                .put("retrievedAt", e.retrievedAt.toString()).put("verifiedAt", e.verifiedAt.toString()).put("stale", e.stale).put("passage", e.passage)
                .put("publishedAt", e.publishedAt?.toString()).put("updatedAt", e.updatedAt?.toString()).put("lexicalRelevance", e.lexicalRelevance).put("verificationKind", e.verificationKind)
                .apply { e.libraryGeneration?.let { value -> put("libraryGeneration", value) } } }, "evidence", a).put("query", a.text("query")).put("networkRequested", false)
        "halo_research_article" -> retrieveArticle(a)
        "halo_research_source" -> {
            val spec = ResearchSourceQuery(a.text("query"), a.text("provider"))
            val saved = research.saveSource(spec, a.text("language"), a["selected"] == true, a["id"] as? String, (a["version"] as? Number)?.toLong())
            JSONObject().put("id", saved.id).put("version", saved.version).put("provider", spec.providerId).put("query", spec.query)
                .put("selected", saved.selected).put("saved", true).put("fetched", false)
        }
        else -> error("Unknown Organizer operation")
    }
    private suspend fun receipt(record: OrganizerRecord) = JSONObject().put("id", record.id).put("version", record.version).put("record", summary(record)).put("saved", true).put("deliveryConfirmed", false)
    private suspend fun summary(record: OrganizerRecord): JSONObject {
        val available = owner.canReadResult(record)
        return JSONObject().apply {
        val work = record.commitment
        put("id", record.id); put("version", record.version); put("title", record.title); put("kind", record.kind.name)
        put("owner", work.owner.name); put("purpose", work.purpose.name); put("state", OrganizerResponsibility.state(record).name); put("intent", work.intent)
        put("actions", JSONArray(work.actions.map { JSONObject().put("capability", it.capability).put("argumentsJson", it.argumentsJson) }))
        put("sources", JSONArray(work.sources.map { JSONObject().put("id", it.id).put("version", it.version) }))
        put("start", record.time?.start?.toString()); put("zoneId", record.time?.zoneId); put("nextHumanAction", work.nextHumanAction)
        work.execution?.let { put("planId", it.planId); put("routineId", it.routineId); put("runId", it.runId); put("scheduling", it.scheduling) }
        put("resultAvailable", available)
        work.result?.takeIf { available }?.let { put("result", JSONObject().put("summary", it.summary).put("confirmedAt", it.confirmedAt.toString())
            .put("evidenceIds", JSONArray(it.evidenceIds)).put("conditionMatched", it.conditionMatched).put("newMatch", it.newMatch).put("messages", JSONArray(it.messages)).put("waiting", it.waiting)
            .put("sourceRefs", JSONArray(it.sourceRefs.map { ref -> JSONObject().put("id", ref.id).put("version", ref.version) }))) }
        work.recovery?.let { put("recovery", JSONObject().put("planId", it.planId).put("routineId", it.routineId).put("runId", it.runId)
            .put("recordedAt", it.recordedAt.toString()).put("uncertain", it.uncertain).put("reviewed", it.reviewed)) }
        work.stopwatch?.let { put("stopwatchMs", it.elapsed(clock.instant(), elapsedMs(), bootId())); put("stopwatchRunning", it.startedAt != null); put("laps", JSONArray(it.laps)) }
    }
    }
}
