package com.halo.app.everyday.organizer

import com.halo.app.di.tools.EverydayToolContracts as Schema
import com.halo.app.everyday.briefing.*
import com.halo.app.everyday.collectors.*
import com.halo.runtime.cognition.routine.AutomationEnvironmentSnapshot
import org.json.JSONObject

data class OrganizerStepResult(val summary: String, val evidenceIds: List<String> = emptyList(), val conditionMatched: Boolean? = null,
    val messages: List<String> = emptyList(), val waiting: Boolean = false, val sourceRefs: List<OrganizerSourceRef> = emptyList())

object OrganizerResultEvidence {
    fun briefFingerprint(document: EverydayBrief): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(document.facts.sortedBy { it.id }.joinToString("\n") { "${it.id}|${it.sourceId}|${it.revision}" }.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

interface OrganizerWorkCapability {
    val id: String
    val argumentSchema: Map<String, Any>
    suspend fun execute(arguments: Map<String, Any?>, requireCurrent: suspend () -> Unit): OrganizerStepResult
}

/** Installed capabilities, not future package names or model-authored tool identifiers. */
class OrganizerWorkCapabilities(entries: List<OrganizerWorkCapability>) {
    private val installed = entries.associateBy { it.id }.also { require(it.size == entries.size) }
    fun list() = installed.values.toList()
    fun require(id: String) = requireNotNull(installed[id]) { "This capability is not installed; revise the plan or take over" }
    fun arguments(action: OrganizerPlannedAction): Map<String, Any?> {
        val decoded = com.halo.app.automation.RoutineArguments.decode(action.argumentsJson)
        Schema.validate(decoded, require(action.capability).argumentSchema)
        when (action.capability) {
            "monitor" -> if (decoded["predicate"] == "CHANGED") require((decoded["baseline"] as? String)?.isNotBlank() == true) { "Choose an observed source baseline" }
                else require((decoded["value"] as? String)?.isNotBlank() == true)
            "web.search" -> require((decoded["query"] as String).isNotBlank())
            "system.monitor" -> if (decoded["condition"] == "BATTERY_BELOW") require("percent" in decoded)
        }
        return decoded
    }
    fun validate(actions: List<OrganizerPlannedAction>) { require(actions.size in 1..6); actions.forEach(::arguments) }
}

class OrganizerBriefCapability(private val briefs: EverydayBriefService) : OrganizerWorkCapability {
    override val id = "summarize"
    override val argumentSchema = Schema.obj(mapOf("period" to Schema.choice(BriefPeriod.entries.map { it.name })), listOf("period"))
    override suspend fun execute(arguments: Map<String, Any?>, requireCurrent: suspend () -> Unit): OrganizerStepResult {
        requireCurrent()
        val brief = briefs.build(BriefPeriod.valueOf(arguments.getValue("period") as String))
        requireCurrent()
        return OrganizerStepResult(brief.facts.take(12).joinToString("\n") { it.title }, listOf("brief:${brief.id}:${OrganizerResultEvidence.briefFingerprint(brief)}"),
            messages = listOf(if (brief.facts.isEmpty()) "No selected facts are available for this brief." else "Brief built from selected local evidence."),
            sourceRefs = brief.sources.filter { source -> source.id != "halo:attention" && brief.facts.any { it.sourceId == source.id } }.map { OrganizerSourceRef(it.id, it.version) })
    }
}

class OrganizerResearchCapability(private val sources: CollectorRepository, private val refresh: CollectorCoordinator,
    private val research: OrganizerResearch) : OrganizerWorkCapability {
    override val id = "web.search"
    override val argumentSchema = Schema.obj(mapOf("query" to Schema.text(400), "sourceId" to Schema.text(160),
        "sourceVersion" to Schema.number(1, Long.MAX_VALUE), "refresh" to mapOf("type" to "boolean")), listOf("query", "sourceId", "sourceVersion"))
    override suspend fun execute(arguments: Map<String, Any?>, requireCurrent: suspend () -> Unit): OrganizerStepResult {
        val sourceId = arguments.getValue("sourceId") as String
        val version = (arguments.getValue("sourceVersion") as Number).toLong()
        fun accepted(state: CollectorState) = state.source.version == version && state.source.selected && state.source.enabled && !state.source.muted
        val initial = requireNotNull(sources.state(sourceId)); require(accepted(initial)) { "Research source changed or is not selected" }
        requireCurrent()
        if (arguments["refresh"] == true) refresh.refreshDetailed(sourceId, manual = false, expectedVersion = version)
        requireCurrent()
        val current = requireNotNull(sources.state(sourceId)); require(accepted(current)) { "Research authority changed" }
        val evidence = research.search(arguments.getValue("query") as String, limit = 8, sourceId = sourceId).filter { it.sourceId == sourceId && it.sourceVersion == version }
        require(evidence.isNotEmpty()) { "No matching retained evidence; select a suitable source and refresh in Power" }
        return OrganizerStepResult(evidence.take(8).joinToString("\n\n") { "${it.title}\n${it.passage.take(500)}\n${it.canonicalUrl.orEmpty()}\n${it.provider} · ${it.retrievedAt}" },
            evidence.take(8).map { "source:${it.sourceId}:${it.id}".take(256) }, messages = if (evidence.any { it.stale }) listOf("Some evidence is cached or stale.") else emptyList(),
            sourceRefs = evidence.map { OrganizerSourceRef(it.sourceId, it.sourceVersion) }.distinct())
    }
}

class OrganizerSourceMonitorCapability(private val sources: CollectorRepository, private val refresh: CollectorCoordinator) : OrganizerWorkCapability {
    override val id = "monitor"
    override val argumentSchema = Schema.obj(mapOf("sourceId" to Schema.text(160), "sourceVersion" to Schema.number(1, Long.MAX_VALUE),
        "baseline" to Schema.text(128), "predicate" to Schema.choice(listOf("CHANGED", "CONTAINS")), "value" to Schema.text(200),
        "refresh" to mapOf("type" to "boolean")), listOf("sourceId", "sourceVersion", "predicate"))
    override suspend fun execute(arguments: Map<String, Any?>, requireCurrent: suspend () -> Unit): OrganizerStepResult {
        val id = arguments.getValue("sourceId") as String; val version = (arguments.getValue("sourceVersion") as Number).toLong()
        suspend fun current(): CollectorState = requireNotNull(sources.state(id)).also {
            require(it.source.version == version && it.source.selected && it.source.enabled && !it.source.muted) { "Monitored source authority changed" }
        }
        current(); requireCurrent()
        if (arguments["refresh"] == true) refresh.refreshDetailed(id, manual = false, expectedVersion = version)
        requireCurrent()
        val state = current(); val snapshot = state.snapshot?.takeIf { it.sourceVersion == version }
        // Stale cached content is evidence about the cache, not a fresh observation of an external condition.
        if (snapshot == null || state.attempt.state !in setOf(CollectorAttemptState.CURRENT, CollectorAttemptState.NOT_MODIFIED) || snapshot.expiresAt <= java.time.Instant.now())
            return OrganizerStepResult(state.source.title, messages = listOf("No current permitted source observation is available. Waiting for the next reviewed check."), waiting = true,
                sourceRefs = listOf(OrganizerSourceRef(id, version)))
        val matched = when (arguments["predicate"]) {
            "CHANGED" -> { val baseline = arguments["baseline"] as? String; require(!baseline.isNullOrBlank()) { "Observe and choose a baseline before monitoring changes" }; snapshot.contentRevision != baseline }
            "CONTAINS" -> {
                val value = arguments["value"] as? String; require(!value.isNullOrBlank())
                val text = when (val payload = snapshot.payload) {
                    is CollectorPayload.Articles -> payload.items.joinToString("\n") { "${it.title} ${it.text}" }
                    is CollectorPayload.Warnings -> payload.items.joinToString("\n") { "${it.title} ${it.text}" }
                    is CollectorPayload.Weather -> "${payload.fact.temperatureC} ${payload.fact.weatherCode} ${payload.fact.days}"
                    is CollectorPayload.Local -> payload.items.joinToString("\n") { "${it.title} ${it.summary}" }
                    is CollectorPayload.Calendar -> payload.preview.records.joinToString("\n") { "${it.title} ${it.details}" }
                }
                text.contains(value, ignoreCase = true)
            }
            else -> throw IllegalArgumentException("Unsupported monitoring predicate")
        }
        return OrganizerStepResult(state.source.title, listOf("source:$id:${snapshot.contentRevision}".take(256)), matched,
            listOf(if (matched) "The reviewed source condition matched." else "No matching change in the observed source."), sourceRefs = listOf(OrganizerSourceRef(id, version)))
    }
}

class OrganizerSystemMonitorCapability(private val environment: suspend () -> AutomationEnvironmentSnapshot) : OrganizerWorkCapability {
    override val id = "system.monitor"
    override val argumentSchema = Schema.obj(mapOf("condition" to Schema.choice(listOf("BATTERY_BELOW", "NETWORK_AVAILABLE", "CHARGING")),
        "percent" to Schema.number(1, 100)), listOf("condition"))
    override suspend fun execute(arguments: Map<String, Any?>, requireCurrent: suspend () -> Unit): OrganizerStepResult {
        requireCurrent(); val observed = environment(); requireCurrent()
        val matched = when (arguments["condition"]) {
            "BATTERY_BELOW" -> requireNotNull(observed.batteryPercent) < (arguments.getValue("percent") as Number).toInt()
            "NETWORK_AVAILABLE" -> requireNotNull(observed.networkAvailable)
            "CHARGING" -> requireNotNull(observed.charging)
            else -> throw IllegalArgumentException("Unsupported system condition")
        }
        return OrganizerStepResult("", listOf("device:${arguments["condition"]}:${arguments["percent"]}:$matched"), matched,
            listOf(if (matched) "The reviewed device condition matched." else "The reviewed device condition has not matched."))
    }
}
