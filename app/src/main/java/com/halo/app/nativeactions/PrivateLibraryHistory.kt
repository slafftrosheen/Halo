package com.halo.app.nativeactions

import com.halo.app.data.AppDatabase
import com.halo.app.data.PersistedTurn
import com.halo.app.data.RoomConversationStore
import com.halo.app.data.agent.AgentStepEntity
import com.halo.app.data.agent.AgentRunWithSteps
import com.halo.app.data.notebook.WorkspaceObjectEntity
import com.halo.app.data.workspace.privateLibraryClaim
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Seals saved conversations and settled or parked execution history owned in Room. */
internal class PrivateLibraryHistory(
    private val database: AppDatabase,
    private val conversationPolicy: (String) -> String? = { null },
    private val now: () -> Long = System::currentTimeMillis,
    private val conversationStore: RoomConversationStore? = null,
    private val writeConversationPolicy: (String, String?) -> Unit = { _, raw -> check(raw == null) },
) {
    private val conversations get() = database.conversationDao()
    private val runs get() = database.agentRunDao()
    private val privacy get() = database.privateLibraryDao()

    suspend fun <T> withConversationWithdrawal(ids: Set<String>, action: suspend () -> T): T {
        val store = conversationStore ?: return action()
        return store.withPrivateConversationWithdrawal(ids) {
            try { action() }
            finally {
                withContext(NonCancellable) {
                    for (id in ids) if (conversationPolicy(id) != null && conversations.getConversation(id) == null &&
                        privacy.claims(listOf(privateLibraryClaim(id))).isNotEmpty()) writeConversationPolicy(id, null)
                }
            }
        }
    }

    suspend fun linked(sourceId: String): List<PrivateHistoryRef> = buildList {
        val threads = conversations.privateHistoryCandidates(sourceId) + listOfNotNull(conversations.getConversation(sourceId)?.id)
        for (id in threads.distinct().sorted()) {
            if (id == sourceId || conversations.turns(id).any { turn ->
                    turn.id == sourceId || sourceId in turn.userText || sourceId in turn.assistantText ||
                        JSONObject(turn.metadata).containsReference(sourceId)
                }) add(PrivateHistoryRef(PrivateHistoryKind.CONVERSATION, id))
        }
        val episodeRuns = database.personalMemoryDao().privateEpisodeRunIds(sourceId).toSet()
        for (id in (runs.privateHistoryCandidates(sourceId) + episodeRuns).distinct().sorted()) {
            val row = requireNotNull(runs.get(id))
            if (id in episodeRuns || row.matches(sourceId))
                add(PrivateHistoryRef(PrivateHistoryKind.AGENT_RUN, id))
        }
    }

    suspend fun capture(ref: PrivateHistoryRef, coveredOwners: Set<String> = emptySet(),
        toolResults: List<PrivateLibraryPayloadBody> = emptyList(),
    ): PrivateLibraryItem = when (ref.kind) {
        PrivateHistoryKind.CONVERSATION -> captureConversation(ref.id, coveredOwners, toolResults)
        PrivateHistoryKind.AGENT_RUN -> captureRun(ref.id, coveredOwners, toolResults)
    }

    fun ref(item: PrivateLibraryItem): PrivateHistoryRef = PrivateHistoryRef(
        PrivateHistoryKind.valueOf(requireNotNull(item.history).getString("kind")), item.header.id,
    )

    fun ownedIds(item: PrivateLibraryItem): Set<String> = when (ref(item).kind) {
        PrivateHistoryKind.CONVERSATION -> conversation(item).turns.flatMapTo(linkedSetOf()) { turn ->
            listOfNotNull(turn.id, JSONObject(turn.metadata).historyString("commitId")?.let { "conversation-commit:$it" })
        }
        PrivateHistoryKind.AGENT_RUN -> run(item).let { history ->
            history.steps.mapTo(linkedSetOf()) { it.id } + history.episodes.map { it.id }
        }
    }

    fun ownedEpisodeIds(item: PrivateLibraryItem): Set<String> = if (ref(item).kind == PrivateHistoryKind.AGENT_RUN)
        run(item).episodes.mapTo(linkedSetOf()) { it.id } else emptySet()

    fun sourceIds(item: PrivateLibraryItem): Set<String> = when (ref(item).kind) {
        PrivateHistoryKind.CONVERSATION -> conversationSources(conversation(item).turns)
        PrivateHistoryKind.AGENT_RUN -> run(item).let { runSources(AgentRunWithSteps(it.run, it.steps)) }
    }

    /** Direct links to surviving canonical owners, for dependency discovery before opening an envelope. */
    suspend fun related(ref: PrivateHistoryRef): List<PrivateHistoryRef> {
        val (sources, interactions) = when (ref.kind) {
            PrivateHistoryKind.CONVERSATION -> {
                val turns = conversations.turns(ref.id)
                conversationSources(turns) to turns.mapNotNull { JSONObject(it.metadata).historyString("interactionId") }.distinct()
            }
            PrivateHistoryKind.AGENT_RUN -> {
                val parent = requireNotNull(runs.get(ref.id))
                runSources(parent) to listOf(parent.run.interactionId)
            }
        }
        val direct = sources.flatMap { id ->
            listOfNotNull(
                conversations.getConversation(id)?.let { PrivateHistoryRef(PrivateHistoryKind.CONVERSATION, it.id) },
                runs.get(id)?.let { PrivateHistoryRef(PrivateHistoryKind.AGENT_RUN, it.run.id) },
            )
        }
        return (direct + interactions.filter(String::isNotBlank).flatMap { linked(it) }).filter { it != ref }.distinct()
    }

    suspend fun relatedOwnedIds(ref: PrivateHistoryRef): Set<String> = when (ref.kind) {
        PrivateHistoryKind.CONVERSATION -> conversations.turns(ref.id).mapTo(linkedSetOf(), PersistedTurn::id)
        PrivateHistoryKind.AGENT_RUN -> requireNotNull(runs.get(ref.id)).steps.mapTo(linkedSetOf()) { it.id } +
            database.personalMemoryDao().privateRunEpisodes(ref.id).map { it.id }
    }

    fun preview(item: PrivateLibraryItem): String = when (ref(item).kind) {
        PrivateHistoryKind.CONVERSATION -> conversation(item).turns.take(20).joinToString("\n\n") { turn ->
            listOf(turn.userText.take(1200), turn.assistantText.take(2400)).filter(String::isNotBlank).joinToString("\n")
        }
        PrivateHistoryKind.AGENT_RUN -> run(item).run.request.take(4000)
    }

    suspend fun withdraw(item: PrivateLibraryItem) {
        val ref = ref(item)
        when (ref.kind) {
            PrivateHistoryKind.CONVERSATION -> {
                val snapshot = conversation(item)
                check(conversations.getConversation(ref.id) == snapshot.conversation && conversationTurns(ref.id) == snapshot.turns &&
                    database.notebookDao().getWorkspaceObject(ref.id) == item.header && conversationPolicy(ref.id) == snapshot.contextPolicy) {
                    "Conversation history changed during review"
                }
                if (snapshot.conversation.active) {
                    requireNotNull(conversationStore).stagePrivateConversationWithdrawal(ref.id, wasActive = true)
                } else conversationStore?.stagePrivateConversationWithdrawal(ref.id, wasActive = false)
                conversations.clear(ref.id)
                check(conversations.withdrawPrivateConversation(ref.id) == 1)
                check(conversations.withdrawPrivateHeader(ref.id) == 1)
                privacy.withdrawProvenance(ref.id)
                database.workspaceContextDao().remove(ref.id)
                database.notebookDao().deleteDraft(ref.id)
            }
            PrivateHistoryKind.AGENT_RUN -> {
                val snapshot = run(item)
                val memory = database.personalMemoryDao()
                val current = requireNotNull(runs.get(ref.id))
                check(current.run == snapshot.run && orderedSteps(current) == snapshot.steps &&
                    memory.privateRunEpisodes(ref.id) == snapshot.episodes && memory.reflection("run:${ref.id}") == snapshot.reflection) {
                    "Execution history changed during review"
                }
                check(memory.withdrawPrivateRunEpisodes(ref.id) == snapshot.episodes.size)
                snapshot.reflection?.let { check(memory.withdrawPrivateReflection(it.id) == 1) }
                check(runs.withdrawPrivateSteps(ref.id) == snapshot.steps.size)
                check(runs.withdrawPrivateRun(ref.id, snapshot.run.state) == 1)
            }
        }
    }

    suspend fun restore(item: PrivateLibraryItem) {
        val ref = ref(item)
        when (ref.kind) {
            PrivateHistoryKind.CONVERSATION -> {
                val snapshot = conversation(item)
                val notes = database.notebookDao()
                val links = database.notebookOrganizationDao()
                check(item.header.type == "CONVERSATION" && conversations.getConversation(ref.id) == null)
                val previousPolicy = conversationPolicy(ref.id)
                check(previousPolicy == null || previousPolicy == snapshot.contextPolicy) { "Existing conversation policy must be preserved" }
                if (previousPolicy != snapshot.contextPolicy) writeConversationPolicy(ref.id, snapshot.contextPolicy)
                notes.insertObject(item.header)
                conversations.restorePrivateConversation(snapshot.conversation.copy(active = false))
                snapshot.turns.forEach { conversations.restorePrivateTurn(it) }
                item.memberships.forEach { link -> if (privacy.collection(link.collectionId) != null) links.addMember(link) }
                item.favorite?.let { links.addFavorite(it) }
                item.alias?.takeIf { it.expiresAt > now() }?.let { database.workspaceLibraryNameDao().save(it) }
                item.provenance?.let { database.workspaceContextDao().provenance(it) }
                item.working?.let { database.workspaceContextDao().add(it) }
                item.draft?.let { notes.upsertDraft(it) }
            }
            PrivateHistoryKind.AGENT_RUN -> {
                val snapshot = run(item)
                val original = snapshot.run
                val restored = if (original.state == PrivateAgentHistory.waitingForEvent) {
                    val closedAt = maxOf(now(), original.updatedAt)
                    original.copy(state = "CANCELLED", updatedAt = closedAt, completedAt = closedAt)
                } else original
                runs.insertRun(restored)
                snapshot.steps.forEach { runs.insertStep(it) }
                val memory = database.personalMemoryDao()
                snapshot.episodes.forEach { check(memory.episode(it) != -1L) }
                snapshot.reflection?.let { check(memory.claim(it) != -1L) }
            }
        }
    }

    private suspend fun captureConversation(id: String, coveredOwners: Set<String>,
        toolResults: List<PrivateLibraryPayloadBody>,
    ): PrivateLibraryItem {
        val row = requireNotNull(conversations.getConversation(id))
        check(!row.active || conversationStore != null) { "The selected conversation's secure transition is unavailable" }
        val notes = database.notebookDao()
        val header = requireNotNull(notes.getWorkspaceObject(id))
        check(header.type == "CONVERSATION" && header.contentRef == "conversation:$id")
        val turns = conversationTurns(id)
        val results = coveredResults(setOf(id) + conversationSources(turns), toolResults)
        requireCoveredPayloadOwners(listOf(id) + turns.map(PersistedTurn::id), toolResults)
        requireCoveredOwners(listOf(id) + turns.map(PersistedTurn::id), coveredOwners)
        check(turns.none { JSONObject(it.metadata).hasUncoveredPayload(results) }) {
            "This conversation has a stored result outside the reviewed group"
        }
        return PrivateLibraryItem(header, null, emptyList(), privacy.memberships(id), privacy.favorite(id), privacy.alias(id), null,
            provenance = database.workspaceContextDao().provenanceFor(id), working = database.workspaceContextDao().selectedEntry(id),
            draft = notes.getDraft(id), history = PrivateConversationHistory(row, turns, conversationPolicy(id)).encode())
    }

    private suspend fun captureRun(id: String, coveredOwners: Set<String>,
        toolResults: List<PrivateLibraryPayloadBody>,
    ): PrivateLibraryItem {
        val row = requireNotNull(runs.get(id))
        val run = row.run
        val parked = run.state == PrivateAgentHistory.waitingForEvent
        check(parked || run.state in PrivateAgentHistory.terminalStates) { "Finish the linked execution before locking it" }
        val steps = orderedSteps(row)
        val coveredWatches = requireCoveredWatches(run.id, run.interactionId, steps, coveredOwners, parked)
        val routineRows = runs.privateRoutineRunsForAgent(id)
        val routineRun = routineRows.isNotEmpty() || steps.any { it.type == "RunPrepared" && JSONObject(it.payloadJson).optString("kind") == "ROUTINE" }
        if (routineRun || run.interactionId.startsWith("routine:")) {
            check(routineRows.isNotEmpty() && routineRows.all { it.routineId in coveredOwners && it.finishedAt != null && it.state in terminalRoutineStates }) {
                "This execution has a routine occurrence that is unfinished or outside the approved Private group"
            }
        }
        requireClosedJournal(steps, coveredWatches, coveredResults(listOf(id), toolResults))
        requireCoveredPayloadOwners(listOf(id, run.interactionId) + steps.map { it.id }, toolResults)
        val memory = database.personalMemoryDao()
        val reflection = memory.reflection("run:$id")
        check(reflection == null || reflection.status in PrivateAgentHistory.terminalReflections) {
            "Finish the linked execution's reflection before locking it"
        }
        val episodes = memory.privateRunEpisodes(id)
        requireCoveredOwners(listOf(id, run.interactionId) + episodes.map { it.id }, coveredOwners + id)
        val snapshot = PrivateAgentHistory(run, steps, episodes, reflection)
        val header = WorkspaceObjectEntity(id, "PRIVATE_AGENT_RUN", run.request.take(160).ifBlank { "Execution history" },
            run.startedAt, run.updatedAt, "AGENT", "ACTIVE", false, "NONE", "agent-run:$id")
        return PrivateLibraryItem(header, null, emptyList(), emptyList(), null, null, null, history = snapshot.encode())
    }

    private suspend fun conversationTurns(id: String) = conversations.turns(id)
        .sortedWith(compareBy(PersistedTurn::timestamp, PersistedTurn::id))

    private fun orderedSteps(row: AgentRunWithSteps) = row.steps.sortedWith(compareBy(AgentStepEntity::ordinal, AgentStepEntity::id))

    private suspend fun requireCoveredWatches(runId: String, interactionId: String, steps: List<AgentStepEntity>,
        covered: Set<String>, parked: Boolean): Set<String> {
        val watches = database.continuationWatchDao()
        val stepIds = steps.filter { it.type in watchSteps }.map { JSONObject(it.payloadJson).getString("watchId") }.toSet()
        val direct = (watches.privateCandidates(runId) + interactionId.takeIf(String::isNotBlank)?.let { watches.privateCandidates(it) }.orEmpty())
            .distinct().mapNotNull { watches.getWatchById(it) }.filter { row ->
                row.runId == runId || row.interactionId == interactionId ||
                    row.behaviorType in runWatchBehaviors && JSONObject(row.behaviorJson).optString("runId") == runId
            }
        val all = (direct.map { it.id } + stepIds).toSet()
        val coveredRows = all.map { id ->
            check(id in covered) { "This execution has a continuation watch outside the approved Private group" }
            requireNotNull(watches.getWatchById(id))
        }
        check(coveredRows.all { it.status in sealableWatchStates }) {
            "This execution has an unsupported continuation watch state"
        }
        if (parked) {
            val registered = steps.filter { it.type == "ExternalConditionRegistered" }
                .mapTo(hashSetOf()) { JSONObject(it.payloadJson).getString("watchId") }
            check(coveredRows.any { it.id in registered && it.runId == runId && it.behaviorType in runWatchBehaviors }) {
                "This waiting execution has no verified continuation watch"
            }
        }
        return all
    }

    private fun requireClosedJournal(steps: List<AgentStepEntity>, coveredWatches: Set<String>, results: Map<String, String>) {
        val records = steps.map { it to JSONObject(it.payloadJson) }
        check(records.all { (step, payload) ->
            !payload.hasUncoveredPayload(results) && when {
                step.type in watchSteps -> payload.historyString("watchId") in coveredWatches && step.idempotencyKey.isNullOrBlank()
                step.type in inlineSteps -> step.idempotencyKey.isNullOrBlank()
                else -> step.type in toolSteps
            }
        }) { "This execution has unsupported evidence or separately stored results" }
        val receipts = records.filter { (step, data) ->
            step.type in failedToolSteps || step.type in outcomeSteps && data.optString("actionTruth") in finalToolTruths
        }
        val claims = records.filter { it.first.type in claimSteps }
        check(claims.all { (claim, input) ->
            val call = input.historyString("toolCallId")
            val tool = input.historyString("toolId")
            val invocation = input.historyString("invocationId")
            val nextClaim = claims.firstOrNull { (next, nextInput) ->
                next.ordinal > claim.ordinal && nextInput.historyString("toolCallId") == call &&
                    nextInput.historyString("toolId") == tool
            }?.first?.ordinal
            call != null && tool != null && receipts.any { (receipt, output) ->
                val matchesInvocation = output.historyString("invocationId") == invocation ||
                    receipt.type in failedToolSteps && !output.has("invocationId") &&
                    (nextClaim == null || receipt.ordinal < nextClaim)
                receipt.ordinal > claim.ordinal && output.historyString("toolCallId") == call &&
                    output.historyString("toolId") == tool && matchesInvocation &&
                    (invocation != null || nextClaim == null || receipt.ordinal < nextClaim)
            }
        }) { "An action has no final receipt; its effects must be reconciled before locking" }
    }

    private suspend fun requireCoveredOwners(ids: List<String>, covered: Set<String>) {
        val memory = database.personalMemoryDao()
        check(ids.distinct().none { id -> memory.privateHistoryPublicOwners(id).any { it.id !in covered } }) {
            "A separate saved memory, episode or suggestion still uses this history"
        }
    }

    private fun conversationSources(turns: List<PersistedTurn>): Set<String> = turns.flatMapTo(linkedSetOf()) { turn ->
        val metadata = JSONObject(turn.metadata)
        listOfNotNull(metadata.historyString("runId"), metadata.historyString("sourceCaptureId"), metadata.historyString("interactionId"))
    }

    private fun runSources(row: AgentRunWithSteps): Set<String> = buildSet {
        listOfNotNull(row.run.parentRunId, row.run.rootRunId).filter { it != row.run.id }.forEach(::add)
        for (step in row.steps) {
            val data = JSONObject(step.payloadJson)
            data.historyString("conversationId")?.let(::add)
            data.historyString("sourceCaptureId")?.let(::add)
            for (key in listOf("objectIds", "memoryIds")) {
                val ids = data.optJSONArray(key) ?: continue
                for (i in 0 until ids.length()) add(ids.getString(i))
            }
            val refs = data.optJSONArray("workspaceReferences") ?: continue
            for (i in 0 until refs.length()) refs.getJSONObject(i).historyString("objectId")?.let(::add)
        }
    }

    private fun coveredResults(ids: Collection<String>, bodies: List<PrivateLibraryPayloadBody>): Map<String, String> =
        bodies.flatMap { it.references }.filter { it.ownerId in ids }.associate { it.id to it.sha256 }

    private suspend fun requireCoveredPayloadOwners(ids: List<String>, bodies: List<PrivateLibraryPayloadBody>) {
        val owners = bodies.flatMap { it.references }.mapTo(hashSetOf()) { it.ownerId }
        check(ids.distinct().filterNot { it in owners }.chunked(400).none { database.payloadDao().hasPrivateOwnerReferences(it) }) {
            "This history has stored results without a verified owner"
        }
    }

    private fun AgentRunWithSteps.matches(id: String): Boolean = id in listOf(run.id, run.parentRunId, run.rootRunId, run.interactionId) ||
        run.delegatedInputRefs?.takeIf(String::isNotBlank)?.let { id in it } == true ||
        steps.any { id == it.id || id == it.interactionId || JSONObject(it.payloadJson).containsReference(id) }

    private fun conversation(item: PrivateLibraryItem) = PrivateConversationHistory.decode(item.header.id, requireNotNull(item.history))
    private fun run(item: PrivateLibraryItem) = PrivateAgentHistory.decode(item.header.id, requireNotNull(item.history))

    private fun JSONObject.containsReference(id: String): Boolean = keys().asSequence().any { it == id || get(it).containsReference(id) }
    private fun Any?.containsReference(id: String): Boolean = when (this) {
        is String -> id in this
        is JSONObject -> containsReference(id)
        is JSONArray -> (0 until length()).any { get(it).containsReference(id) }
        else -> false
    }

    private fun JSONObject.hasUncoveredPayload(results: Map<String, String>): Boolean {
        val ref = historyString("payloadRefId")?.takeUnless { it == "null" }
        val sha = historyString("payloadSha256")?.takeUnless { it == "null" }
        if (ref != null && (ref !in results || sha != null && results[ref] != sha)) return true
        if (sha != null && sha !in results.values) return true
        if (externalMediaKeys.any { historyString(it)?.let { id -> id != "null" } == true }) return true
        return keys().asSequence().any { get(it).hasUncoveredPayload(results) }
    }

    private fun Any?.hasUncoveredPayload(results: Map<String, String>): Boolean = when (this) {
        is JSONObject -> hasUncoveredPayload(results)
        is JSONArray -> (0 until length()).any { get(it).hasUncoveredPayload(results) }
        else -> false
    }

    private companion object {
        val externalMediaKeys = setOf("mediaRefId", "mediaAssetId")
        val sealableWatchStates = setOf("ACTIVE", "MATCH_CLAIMED", "SATISFIED", "EXPIRED", "CANCELLED", "FAILED")
        val runWatchBehaviors = setOf("RECONCILE_RUN", "MAKE_RUN_RESUMABLE")
        val terminalRoutineStates = setOf("COMPLETED", "FAILED", "PARTIAL", "SKIPPED", "CANCELLED", "BLOCKED", "INTERRUPTED")
        val watchSteps = setOf("ExternalConditionRegistered", "ExternalConditionSatisfied", "ExternalConditionExpired", "ExternalConditionCancelled")
        val claimSteps = setOf("ToolExecutionClaimed", "ToolDispatched")
        val outcomeSteps = setOf("ToolCompleted", "ToolOutcomeRecorded")
        val failedToolSteps = setOf("ToolFailed", "ToolRejected")
        val finalToolTruths = setOf("CONFIRMED", "FAILED")
        val toolSteps = claimSteps + outcomeSteps + failedToolSteps + setOf("ToolRequested", "ToolInvocationPrepared")
        val inlineSteps = setOf("RunMetadata", "RunPrepared", "ContinuityBound", "InteractionHandoffRecorded", "ContextUsed",
            "PlanCreated", "ClarificationRequested", "ApprovalRequested", "InputRequested", "InputResolved", "ProviderTurnStarted",
            "ProviderTurnCompleted", "ContinuationPrepared", "ResponseProduced", "ResponseCommitted", "RunGuardTriggered",
            "DirectCommandAuthorityRecorded", "ResourceBlocked", "RecoveryClaimed", "DelegationResultRecorded")
    }
}
