package com.halo.app.everyday.organizer

import androidx.room.withTransaction
import com.halo.app.automation.RoutineArguments
import com.halo.app.automation.RoutineManagementActions
import com.halo.app.data.AppDatabase
import com.halo.app.data.routine.RoomRoutineRepository
import com.halo.runtime.cognition.agent.*
import com.halo.runtime.cognition.routine.*
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import java.time.*
import java.util.UUID

/** Commitment facade over canonical Organizer and the existing reviewed automation runner. */
class OrganizerOperations(private val db: AppDatabase, val organizer: OrganizerUseCases,
    private val routines: RoomRoutineRepository, private val management: () -> RoutineManagementActions,
    val capabilities: OrganizerWorkCapabilities, private val runs: AgentRunStore, private val sources: com.halo.app.everyday.collectors.CollectorRepository,
    private val cancelRun: suspend (String) -> Unit, private val notifyResult: suspend (OrganizerRecord, String) -> Unit,
    private val clock: Clock = Clock.systemUTC(), private val runAllowed: () -> Boolean = { true },
    private val readBrief: suspend (String) -> com.halo.app.everyday.briefing.EverydayBrief? = { null }) {
    private val repository get() = organizer.repository
    private val runAuthorities = java.util.concurrent.ConcurrentHashMap<String, () -> Unit>()
    private suspend fun requireSources(refs: List<OrganizerSourceRef>) {
        refs.distinct().forEach { ref ->
            val source = requireNotNull(sources.source(ref.id))
            require(source.version == ref.version && source.selected && source.enabled && !source.muted) { "Review the changed selected source before approving or disclosing work" }
        }
    }
    private suspend fun validateSources(record: OrganizerRecord) {
        val refs = record.commitment.sources + record.commitment.actions.mapNotNull { action ->
            val args = capabilities.arguments(action)
            (args["sourceId"] as? String)?.let { OrganizerSourceRef(it, (args.getValue("sourceVersion") as Number).toLong()) }
        }
        requireSources(refs)
    }
    suspend fun canReadResult(record: OrganizerRecord): Boolean {
        val result = record.commitment.result ?: return false
        return try {
            validateSources(record); requireSources(result.sourceRefs)
            result.evidenceIds.filter { it.startsWith("brief:") }.forEach { evidence ->
                val parts = evidence.split(':'); require(parts.size == 3)
                val current = requireNotNull(readBrief(parts[1])); require(OrganizerResultEvidence.briefFingerprint(current) == parts[2])
            }
            true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { false }
    }
    suspend fun saveDefinition(id: String?, version: Long?, title: String, intent: String, responsibility: CommitmentOwner,
        purpose: CommitmentPurpose, actions: List<OrganizerPlannedAction>, refs: List<OrganizerSourceRef>): OrganizerRecord {
        require((id == null) == (version == null))
        val old = id?.let { organizer.requireCurrent(it, requireNotNull(version)) }
        require(old == null || old.commitment.execution == null) { "Stop the current plan before editing it" }
        require(old == null || old.commitment.owner == responsibility) { "Use an explicit ownership transition" }
        if (actions.isNotEmpty()) capabilities.validate(actions)
        validateBaselines(actions.filter { action -> old?.commitment?.createdBy == CommitmentCreator.IMPORTED || action !in old?.commitment?.actions.orEmpty() })
        refs.forEach { ref -> val source = requireNotNull(sources.state(ref.id)); require(source.source.version == ref.version && source.source.selected && source.source.enabled && !source.source.muted) }
        val needsSetup = responsibility != CommitmentOwner.ME && actions.isEmpty()
        val recovery = old?.commitment?.recovery
        val needsRecovery = old?.commitment?.recoveryNeedsReview == true
        val commitment = OrganizerCommitment(responsibility, purpose, intent,
            if (needsSetup || needsRecovery) HaloWorkState.NEEDS_USER else if (responsibility != CommitmentOwner.ME || purpose == CommitmentPurpose.INBOX) HaloWorkState.DRAFT else HaloWorkState.READY,
            actions, refs, result = old?.commitment?.result,
            nextHumanAction = if (needsRecovery) "Inspect the previous execution and acknowledge its uncertain effects before continuing."
                else if (needsSetup) "Choose installed capabilities and review the concrete work plan, or take over." else null,
            createdBy = old?.commitment?.createdBy ?: CommitmentCreator.USER,
            stopwatch = if (purpose == CommitmentPurpose.STOPWATCH) old?.commitment?.stopwatch ?: StopwatchState() else null, recovery = recovery)
        return if (old == null) organizer.create(OrganizerKind.TASK, title, commitment = commitment)
            else repository.save(old.copy(title = title, commitment = commitment), old.version)
    }
    private suspend fun validateBaselines(actions: List<OrganizerPlannedAction>) {
        actions.filter { it.capability == "monitor" }.forEach { action ->
            val args = capabilities.arguments(action)
            if (args["predicate"] == "CHANGED" && args["sourceId"] != null) {
                val current = requireNotNull(sources.state(args["sourceId"] as String))
                require(current.source.version == (args.getValue("sourceVersion") as Number).toLong() && current.snapshot?.contentRevision == args["baseline"]) {
                    "Choose the actual retained source observation as the monitoring baseline"
                }
            }
        }
    }
    suspend fun promote(id: String, version: Long, kind: OrganizerKind = OrganizerKind.TASK, time: OrganizerTime? = null,
        owner: CommitmentOwner? = null, purpose: CommitmentPurpose = CommitmentPurpose.STANDARD): OrganizerRecord {
        val record = organizer.requireCurrent(id, version); require(record.commitment.purpose == CommitmentPurpose.INBOX)
        require(record.commitment.execution == null && record.commitment.state != HaloWorkState.RUNNING)
        require(kind in setOf(OrganizerKind.TASK, OrganizerKind.EVENT, OrganizerKind.REMINDER) && purpose in setOf(CommitmentPurpose.STANDARD, CommitmentPurpose.WATCH, CommitmentPurpose.ROUTINE))
        val responsibility = owner ?: record.commitment.owner
        return repository.promoteInbox(record.copy(kind = kind, time = time ?: record.time, parentId = if (kind == OrganizerKind.TASK) record.parentId else null,
            commitment = record.commitment.copy(owner = responsibility, purpose = purpose,
                state = if (record.commitment.recoveryNeedsReview) HaloWorkState.NEEDS_USER else if (responsibility == CommitmentOwner.ME) HaloWorkState.READY else if (record.commitment.actions.isEmpty()) HaloWorkState.NEEDS_USER else HaloWorkState.DRAFT,
                nextHumanAction = if (record.commitment.recoveryNeedsReview) "Inspect the previous execution and acknowledge its uncertain effects before continuing."
                    else if (responsibility != CommitmentOwner.ME && record.commitment.actions.isEmpty()) "Choose installed capabilities and review the concrete work plan, or take over." else null)), version)
    }
    suspend fun acknowledge(id: String, version: Long): OrganizerRecord {
        val record = organizer.requireCurrent(id, version)
        require(record.commitment.owner == CommitmentOwner.SHARED && record.commitment.state == HaloWorkState.NEEDS_USER &&
            record.commitment.result != null && !record.commitment.result.waiting && record.commitment.result.conditionMatched != false &&
            record.commitment.result.newMatch != false && canReadResult(record))
        val binding = requireNotNull(record.commitment.execution)
        require(binding.claimedStep == record.commitment.actions.lastIndex &&
            binding.runId?.let { routines.runResult(it)?.state } == RoutineRunState.COMPLETED) { "Wait for the exact reviewed work plan to finish before acknowledging your part" }
        return repository.save(record.copy(commitment = record.commitment.copy(state = if (record.repeat == null) HaloWorkState.COMPLETED else HaloWorkState.WAITING,
            nextHumanAction = null)), version)
    }
    private fun routineActions(record: OrganizerRecord, planId: String) = record.commitment.actions.indices.map { index ->
        RoutineAction(PERFORM, JSONObject().put("id", record.id).put("planId", planId).put("index", index).toString())
    }
    private fun fingerprint(record: OrganizerRecord, nonce: String): String {
        val payload = JSONObject().put("nonce", nonce).put("id", record.id).put("kind", record.kind.name).put("title", record.title)
            .put("details", record.details).put("owner", record.commitment.owner.name).put("purpose", record.commitment.purpose.name)
            .put("intent", record.commitment.intent).put("time", record.time?.toString()).put("repeat", record.repeat?.toString())
            .put("links", org.json.JSONArray(record.links.map { "${it.objectId}|${it.relation}" }.sorted()))
            .put("sources", org.json.JSONArray(record.commitment.sources.sortedBy { it.id }.map { JSONObject().put("id", it.id).put("version", it.version) }))
            .put("actions", org.json.JSONArray(record.commitment.actions.map { JSONObject().put("capability", it.capability).put("arguments", JSONObject(RoutineArguments.canonical(it.argumentsJson))) }))
        return java.security.MessageDigest.getInstance("SHA-256").digest(RoutineArguments.canonicalDefinitionJson(payload.toString(), 128000).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
    private fun trigger(record: OrganizerRecord): RoutineTrigger {
        val time = record.time ?: return RoutineTrigger.Once(Instant.parse("2199-01-01T00:00:00Z")) // manual-only, saved disabled
        val zone = ZoneId.of(time.zoneId)
        val repeat = record.repeat ?: return RoutineTrigger.Once(time.instant(zone))
        require(repeat.interval == 1 && repeat.count == null && repeat.until == null && repeat.frequency in setOf(RepeatFrequency.DAILY, RepeatFrequency.WEEKLY)) {
            "This automation scheduler supports daily or weekly recurrence; revise this work schedule"
        }
        require(!time.followDeviceZone) { "Choose an explicit automation time zone" }
        return RoutineTrigger.AtTime(time.start.toLocalTime(), if (repeat.frequency == RepeatFrequency.WEEKLY)
            repeat.weekdays.ifEmpty { setOf(time.start.dayOfWeek) } else emptySet(), zone, time.instant(zone))
    }
    suspend fun prepare(id: String, version: Long): OrganizerRecord {
        val record = organizer.requireCurrent(id, version)
        require(!record.commitment.recoveryNeedsReview) { "Review the previous uncertain execution before approving more work" }
        require(record.status == OrganizerStatus.ACTIVE && record.commitment.owner != CommitmentOwner.ME && record.commitment.state != HaloWorkState.RUNNING)
        require(record.commitment.execution == null) { "Stop the existing plan before reviewing a replacement" }
        capabilities.validate(record.commitment.actions)
        if (record.commitment.createdBy == CommitmentCreator.IMPORTED) validateBaselines(record.commitment.actions)
        validateSources(record)
        if (record.commitment.purpose == CommitmentPurpose.WATCH) require(record.commitment.actions.size == 1 &&
            record.commitment.actions.single().capability in setOf("monitor", "system.monitor")) { "A watch needs one concrete monitoring condition" }
        val nonce = UUID.randomUUID().toString(); val planId = fingerprint(record, nonce)
        val routineId = RoutineId("organizer:" + java.security.MessageDigest.getInstance("SHA-256").digest(record.id.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) })
        val old = routines.managementGet(routineId)
        val now = clock.instant()
        val draft = Routine(routineId, record.title.take(120), false, trigger(record), actions = routineActions(record, planId),
            createdAt = now, updatedAt = old?.routine?.updatedAt ?: now, delivery = RoutineDeliveryPolicy.SilentActivity)
        val binding = OrganizerExecutionBinding(planId, routineId.value, RoutineArguments.grant(draft), planNonce = nonce)
        var saved: OrganizerRecord? = null
        val receipt = management().save(draft, old?.sourceVersion) { persist ->
            db.withTransaction {
                saved = repository.save(record.copy(commitment = record.commitment.copy(state = HaloWorkState.READY,
                    execution = binding, nextHumanAction = null)), version)
                persist()
            }
        }
        check(receipt != null && saved != null) { "Plan changed during review; read it again" }
        return requireNotNull(saved)
    }
    private suspend fun bound(record: OrganizerRecord): com.halo.app.data.routine.ManagedRoutineSnapshot {
        val binding = requireNotNull(record.commitment.execution) { "Review the work plan first" }
        require(binding.planNonce.isNotBlank() && binding.planId == fingerprint(record, binding.planNonce)) { "The approved capability plan or context changed" }
        validateSources(record)
        val snapshot = requireNotNull(routines.managementGet(RoutineId(binding.routineId))) { "Automation was removed; review the plan again" }
        val routine = requireNotNull(snapshot.routine)
        require(routine.actions == routineActions(record, binding.planId) && routine.trigger == trigger(record) &&
            routine.grantDigest == RoutineArguments.grant(routine) && routine.grantDigest == binding.routineVersion) { "The automation definition changed; stop and review the plan again" }
        return snapshot
    }
    suspend fun arm(id: String, version: Long): OrganizerRecord {
        val record = organizer.requireCurrent(id, version)
        require(record.time != null && record.commitment.owner != CommitmentOwner.ME && !record.commitment.recoveryNeedsReview &&
            record.commitment.state in setOf(HaloWorkState.READY, HaloWorkState.WAITING))
        val snapshot = bound(record)
        require(record.commitment.state == HaloWorkState.READY || snapshot.routine?.enabled == false) { "This reviewed schedule is already enabled" }
        // Durable WAITING is published only after the scheduler's real receipt, never from a button click.
        val receipt = requireNotNull(management().setEnabled(snapshot.id, true, snapshot.sourceVersion))
        val current = organizer.requireCurrent(id, version)
        val scheduled = receipt.scheduling in setOf("SCHEDULED", "EVENT_DRIVEN")
        return repository.save(current.copy(commitment = current.commitment.copy(
            state = if (scheduled) HaloWorkState.WAITING else HaloWorkState.NEEDS_USER,
            execution = current.commitment.execution!!.copy(scheduling = receipt.scheduling),
            nextHumanAction = if (scheduled) null else receipt.schedulingReason?.take(1000) ?: "Review the blocked automation schedule.")), version)
    }
    suspend fun runNow(id: String, version: Long): OrganizerRecord {
        val record = organizer.requireCurrent(id, version)
        require(record.commitment.owner != CommitmentOwner.ME && !record.commitment.recoveryNeedsReview && record.commitment.state in setOf(HaloWorkState.READY, HaloWorkState.WAITING))
        val snapshot = bound(record)
        requireNotNull(management().runNow(snapshot.id, snapshot.sourceVersion))
        return requireNotNull(repository.get(id))
    }
    suspend fun stop(id: String, version: Long): OrganizerRecord {
        val record = organizer.requireCurrent(id, version); val binding = requireNotNull(record.commitment.execution)
        val recovery = recoveryEvidence(record)
        val saved = repository.save(record.copy(commitment = record.commitment.copy(execution = null,
            recovery = recovery, state = HaloWorkState.NEEDS_USER,
            nextHumanAction = "Work stopped. Review recorded results; an interrupted effect must not be repeated automatically.")), version)
        // Revocation above fences suspended capability calls before any subsequent publication/effect.
        val current = routines.managementGet(RoutineId(binding.routineId))
        if (current != null) management().setEnabled(current.id, false, current.sourceVersion)
        binding.runId?.let { cancelRun(it) }
        return saved
    }
    private suspend fun recoveryEvidence(record: OrganizerRecord): OrganizerWorkRecovery? {
        val binding = record.commitment.execution ?: return record.commitment.recovery
        val run = binding.runId?.let { runs.get(AgentRunId(it)) }
        return if (binding.runId != null) OrganizerWorkRecovery(binding.planId, binding.routineId, binding.runId, clock.instant(),
            run == null || run.state != AgentRunState.COMPLETED) else record.commitment.recovery
    }
    suspend fun transfer(id: String, version: Long, owner: CommitmentOwner): OrganizerRecord {
        val record = organizer.requireCurrent(id, version)
        val current = if (record.commitment.execution != null) stop(id, version) else record
        return organizer.transfer(id, current.version, owner)
    }
    suspend fun acknowledgeRecovery(id: String, version: Long): OrganizerRecord {
        val record = organizer.requireCurrent(id, version)
        require(record.commitment.execution == null && record.commitment.recoveryNeedsReview)
        val setup = record.commitment.owner != CommitmentOwner.ME && record.commitment.actions.isEmpty()
        return repository.save(record.copy(commitment = record.commitment.copy(recovery = record.commitment.recovery!!.copy(reviewed = true),
            state = if (setup) HaloWorkState.NEEDS_USER else if (record.commitment.owner == CommitmentOwner.ME) HaloWorkState.READY else HaloWorkState.DRAFT,
            nextHumanAction = if (setup) "Choose installed capabilities and review the concrete work plan, or take over." else null)), version)
    }
    suspend fun watch(id: String, version: Long): OrganizerRecord {
        val record = organizer.requireCurrent(id, version)
        require(record.kind == OrganizerKind.TASK && record.commitment.purpose != CommitmentPurpose.STOPWATCH)
        val stopped = if (record.commitment.execution != null) stop(id, version) else record
        val current = if (stopped.commitment.owner == CommitmentOwner.ME)
            organizer.transfer(id, stopped.version, CommitmentOwner.HALO) else stopped
        val actions = current.commitment.actions.filter { it.capability in setOf("monitor", "system.monitor") }.take(1)
        return saveDefinition(id, current.version, current.title, current.commitment.intent, current.commitment.owner,
            CommitmentPurpose.WATCH, actions, current.commitment.sources)
    }
    /** Called only inside a journal-claimed RoutineExecutor tool scope, once per exact step. */
    suspend fun perform(id: String, planId: String, index: Int, runId: String, toolCallId: String, authority: () -> Unit = {}): OrganizerRecord {
        require(runAllowed()) { "Organizer background authority changed" }
        val run = requireNotNull(runs.get(AgentRunId(runId)))
        require(!run.state.isTerminal && run.steps.any { it is AgentStep.ToolExecutionClaimed && it.toolId == PERFORM && it.toolCallId == toolCallId }) { "No durable automation claim" }
        if (runAuthorities.size >= 128) runAuthorities.keys.forEach { previous ->
            if (runs.get(AgentRunId(previous))?.state?.isTerminal != false) runAuthorities.remove(previous)
        }
        val verify = runAuthorities.putIfAbsent(runId, authority) ?: authority
        verify()
        var record = requireNotNull(repository.get(id)); val binding = requireNotNull(record.commitment.execution)
        val occurrence = requireNotNull(routines.runResult(runId)) { "No durable automation occurrence" }
        require(occurrence.routineId.value == binding.routineId && occurrence.state == RoutineRunState.RUNNING && toolCallId == "$runId:action:$index")
        require(occurrence.source == "MANUAL" || record.time?.let { it.instant(ZoneId.of(it.zoneId)) }?.let { clock.instant() >= it && occurrence.triggerAt >= it } != false) { "The reviewed work start has not been reached" }
        bound(record)
        require(record.deletedAt == null && record.status == OrganizerStatus.ACTIVE && record.commitment.owner != CommitmentOwner.ME && binding.planId == planId)
        require(index in record.commitment.actions.indices)
        // Automatic ticks may observe a pending shared handoff, but cannot replace it or repeat its capabilities.
        if (record.commitment.owner == CommitmentOwner.SHARED && record.commitment.state == HaloWorkState.NEEDS_USER &&
            record.commitment.result?.waiting == false && record.commitment.result?.newMatch != false &&
            binding.runId != runId && binding.runId?.let { routines.runResult(it)?.state } == RoutineRunState.COMPLETED) return record
        if (index == 0) require(OrganizerResponsibility.mayExecute(record, planId) && binding.runId != runId)
        else require(record.commitment.state == HaloWorkState.RUNNING && binding.runId == runId && binding.claimedStep == index - 1)
        val action = record.commitment.actions[index]; val arguments = capabilities.arguments(action)
        record = repository.save(record.copy(commitment = record.commitment.copy(state = HaloWorkState.RUNNING,
            result = if (index == 0) null else record.commitment.result, nextHumanAction = null,
            execution = binding.copy(runId = runId, claimedStep = index))), record.version)
        val claimedVersion = record.version
        suspend fun current() {
            com.halo.app.everyday.EverydayWriteAuthority.requireCurrent(); verify()
            require(runAllowed()) { "Organizer background authority changed" }
            val latest = organizer.requireCurrent(id, claimedVersion)
            require(latest.commitment.execution?.planId == planId && latest.commitment.execution?.runId == runId)
            require(!requireNotNull(runs.get(AgentRunId(runId))).state.isTerminal) { "Execution was cancelled" }
        }
        try {
            val outcome = com.halo.app.everyday.EverydayEffectAuthority.run(::current) { capabilities.require(action.capability).execute(arguments, ::current) }
            current()
            val previous = record.commitment.result
            val matched = when { previous?.conditionMatched == false || outcome.conditionMatched == false -> false
                previous?.conditionMatched == true || outcome.conditionMatched == true -> true; else -> null }
            val summary = listOfNotNull(previous?.summary, outcome.summary).joinToString("\n\n").take(8000)
            val last = index == record.commitment.actions.lastIndex
            val evidenceIds = (previous?.evidenceIds.orEmpty() + outcome.evidenceIds).distinct().take(20)
            val repeatedMatch = last && record.commitment.purpose == CommitmentPurpose.WATCH && matched == true &&
                evidenceIds.isNotEmpty() && evidenceIds.toSet() == binding.lastEvidenceIds.toSet()
            val newMatch = if (matched == null) null else matched && !repeatedMatch
            val waiting = previous?.waiting == true || outcome.waiting
            val dependencies = (previous?.sourceRefs.orEmpty() + outcome.sourceRefs).distinctBy { it.id }
            val result = OrganizerWorkResult(summary, clock.instant(), evidenceIds, matched, newMatch,
                (if (repeatedMatch) listOf("The previously observed condition is unchanged; no new match was announced.") else previous?.messages.orEmpty() + outcome.messages).distinct().take(12), waiting, dependencies)
            val next = when {
                !last -> HaloWorkState.RUNNING
                waiting && record.repeat != null -> HaloWorkState.WAITING
                waiting -> HaloWorkState.NEEDS_USER
                newMatch == false && record.repeat != null -> HaloWorkState.WAITING
                newMatch == false -> HaloWorkState.NEEDS_USER
                record.commitment.owner == CommitmentOwner.SHARED -> HaloWorkState.NEEDS_USER
                record.repeat != null -> HaloWorkState.WAITING
                else -> HaloWorkState.COMPLETED
            }
            return db.withTransaction {
                current()
                validateSources(record); requireSources(dependencies)
                val saved = repository.save(record.copy(commitment = record.commitment.copy(state = next, result = result,
                execution = record.commitment.execution!!.copy(lastEvidenceIds = if (last && !waiting) evidenceIds else binding.lastEvidenceIds),
                nextHumanAction = if (next == HaloWorkState.NEEDS_USER) if (waiting) "No current source observation is available. Review availability or choose another check."
                    else if (newMatch == false) "No new condition match was observed. Choose another check or a recurring schedule."
                    else "Review the prepared result and complete your part, or revise the plan."
                    else if (next == HaloWorkState.WAITING && record.commitment.execution!!.scheduling !in setOf("SCHEDULED", "EVENT_DRIVEN"))
                        "This recurring plan has no active schedule. Enable its reviewed schedule or run the next check explicitly." else null)), claimedVersion)
                com.halo.app.everyday.EverydayWriteAuthority.requireCurrent(); verify()
                saved
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            // Preserve a durable claimed step. Recovery inspects the journal; it never blindly retries.
            val latest = repository.get(id)
            if (latest?.version == claimedVersion) repository.save(latest.copy(commitment = latest.commitment.copy(state = HaloWorkState.NEEDS_USER,
                nextHumanAction = "Work stopped before a confirmed result. Read the execution record before retrying.")), claimedVersion)
            throw error
        }
    }
    suspend fun onRoutineFinished(routine: Routine, summary: RoutineRunSummary?): Boolean {
        if (!routine.id.value.startsWith("organizer:") || routine.actions.isEmpty() || routine.actions.any { it.toolId != PERFORM }) return false
        val authority = summary?.agentRunId?.let { runAuthorities.remove(it) }
        val mayPresent = authority != null && runCatching { authority() }.isSuccess
        val id = routine.actions.firstOrNull()?.argumentsJson?.let { JSONObject(it).optString("id") } ?: return true
        var record = repository.get(id) ?: return true
        if (record.deletedAt != null || record.commitment.execution?.routineId != routine.id.value || summary == null) return true
        val planId = JSONObject(routine.actions.first().argumentsJson).getString("planId")
        if (record.commitment.execution?.planId != planId || record.status != OrganizerStatus.ACTIVE) return true
        if (record.commitment.owner == CommitmentOwner.SHARED && record.commitment.state == HaloWorkState.NEEDS_USER &&
            record.commitment.result != null && record.commitment.execution?.runId != summary.agentRunId) return true
        if (summary.state !in setOf(RoutineRunState.RUNNING, RoutineRunState.SCHEDULED, RoutineRunState.COMPLETED)) {
            record = repository.save(record.copy(commitment = record.commitment.copy(state = if (summary.state == RoutineRunState.FAILED) HaloWorkState.FAILED else HaloWorkState.NEEDS_USER,
                execution = record.commitment.execution!!.copy(runId = summary.agentRunId, scheduling = "BLOCKED"),
                nextHumanAction = "Review the execution record before continuing.")), record.version)
            routines.setEnabled(routine.id, false)
        }
        if (mayPresent && runAllowed() && record.commitment.execution?.runId == summary.agentRunId && record.commitment.result?.waiting != true && record.commitment.result?.conditionMatched != false && record.commitment.result?.newMatch != false &&
            record.commitment.state in setOf(HaloWorkState.COMPLETED, HaloWorkState.NEEDS_USER, HaloWorkState.FAILED, HaloWorkState.WAITING)) notifyResult(record, summary.id)
        return true
    }
    /** Process loss while claimed work was running requires inspection, never automatic replay. */
    suspend fun reconcile() {
        // Restore/purge can remove every binding while old scheduler jobs still exist.
        val owners = repository.records(true).associateBy { it.id }
        routines.all().filter { it.id.value.startsWith("organizer:") && it.actions.isNotEmpty() && it.actions.all { action -> action.toolId == PERFORM } }.forEach { routine ->
            val args = JSONObject(routine.actions.first().argumentsJson)
            val record = owners[args.optString("id")]
            val binding = record?.commitment?.execution
            if (record == null || record.deletedAt != null || record.status != OrganizerStatus.ACTIVE ||
                binding == null || binding.routineId != routine.id.value || binding.planId != args.optString("planId")) {
                val snapshot = routines.managementGet(routine.id)
                if (snapshot != null) management().setEnabled(snapshot.id, false, snapshot.sourceVersion)
            }
        }
        repository.records(true).filter { (it.deletedAt != null || it.status != OrganizerStatus.ACTIVE) && it.commitment.execution != null }.forEach { record ->
            val id = RoutineId(record.commitment.execution!!.routineId)
            if (routines.get(id)?.enabled == true) {
                routines.setEnabled(id, false)
                val snapshot = routines.managementGet(id)
                if (snapshot != null) management().setEnabled(id, false, snapshot.sourceVersion)
            }
        }
        repository.records().filter { it.commitment.execution != null && it.commitment.state != HaloWorkState.RUNNING }.forEach { record ->
            val binding = record.commitment.execution!!
            val linked = runCatching { bound(record) }.getOrNull()
            if (linked == null || record.commitment.state == HaloWorkState.WAITING && binding.scheduling in setOf("SCHEDULED", "EVENT_DRIVEN") && linked.routine?.enabled != true)
                repository.save(record.copy(commitment = record.commitment.copy(execution = null, recovery = recoveryEvidence(record), state = HaloWorkState.NEEDS_USER,
                    nextHumanAction = "The saved execution owner changed. Review this work plan before enabling it again.")), record.version)
        }
        repository.records().filter { it.commitment.state == HaloWorkState.RUNNING }.forEach { record ->
            val run = record.commitment.execution?.runId?.let { runs.get(AgentRunId(it)) }
            if (run == null || run.state.isTerminal || run.state == AgentRunState.INTERRUPTED) repository.save(record.copy(commitment = record.commitment.copy(
                state = HaloWorkState.NEEDS_USER, nextHumanAction = "Execution was interrupted. Inspect its recorded effects before continuing.")), record.version)
        }
    }
    companion object { const val PERFORM = "halo_organizer_perform" }
}
