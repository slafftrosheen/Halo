package com.halo.app.learning

import com.halo.app.data.AppDatabase
import com.halo.app.data.memory.*
import com.halo.runtime.cognition.agent.*
import com.halo.runtime.cognition.memory.*
import com.halo.runtime.cognition.procedures.LearnedProcedureStore
import com.halo.runtime.cognition.routine.AutomationEnvironmentSnapshot
import com.halo.runtime.cognition.tools.ToolDescriptor
import com.halo.runtime.cognition.tools.ToolRiskClass
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.Instant

/** Bounded terminal-run observer. All work follows durable response commit; root action truth never changes. */
class ReflectionCoordinator(private val db: AppDatabase,private val store: AgentRunStore,private val learning: PersonalLearningRepository,
    private val preferences: ()->LearningPreferences,private val environment: suspend ()->AutomationEnvironmentSnapshot,
    private val busy: ()->Boolean,private val activeRuns: Flow<List<AgentRun>>,private val model: ReflectionModel,
    private val procedures: LearnedProcedureStore,private val descriptor: (String)->ToolDescriptor?,
    private val publish: suspend (com.halo.sdk.runtime.PersonalReflectionCompleted)->Unit = {},
    private val capabilityPatterns: suspend (List<Episode>)->List<CapabilityPattern> = { emptyList() }) {
    private val builder=EpisodeBuilder()
    suspend fun observe(): Unit = coroutineScope {
        launch { learning.pending.collect { PersonalMemoryDiagnostics.pending(it.map { row -> row.kind }) } }
        launch { learning.episodes.collect { PersonalMemoryDiagnostics.episodes(it.size) } }
        learning.expire()
        learning.dao.interruptReflections()
        // Old PENDING reflects an interrupted auxiliary job, never a reason to replay the user's work.
        db.agentRunDao().reflectionCandidateIds().collect { ids ->
            ids.forEach { id ->
                if(learning.dao.reflection("run:$id") != null) return@forEach
                val run=store.get(AgentRunId(id)) ?: return@forEach
                try { process(run) } catch(cancelled: CancellationException) { throw cancelled }
                catch(_: Exception) { learning.dao.status("run:$id","FAILED","DETERMINISTIC",System.currentTimeMillis()) }
            }
        }
    }
    private suspend fun process(run: AgentRun) {
        if(run.parentRunId != null || !run.state.isTerminal) return
        val prefs=preferences();val key="run:${run.id.value}"
        // Normal successful turns must have committed conversation evidence before learning starts.
        val commit=run.steps.filterIsInstance<AgentStep.ResponseCommitted>().lastOrNull()
        if(run.state == AgentRunState.COMPLETED && commit == null && !run.interactionId.value.startsWith("routine:")) return
        if(learning.dao.claim(ReflectionStateEntity(key,ReflectionPolicy.VERSION,"PENDING","DETERMINISTIC",System.currentTimeMillis())) == -1L) return
        val turn=commit?.let { c -> db.conversationDao().turnsForCommit("%${c.commitId}%").firstOrNull {
            runCatching { org.json.JSONObject(it.metadata).optString("commitId") == c.commitId }.getOrDefault(false) } }
        val contextReceipts=run.steps.filterIsInstance<AgentStep.ContextUsed>()
        val objectIds=contextReceipts.flatMap { it.objectIds }.distinct().take(20).filter { db.notebookDao().getWorkspaceObject(it) != null }.toSet()
        val memoryIds=contextReceipts.flatMap { it.memoryIds }.distinct().take(12).filter { db.memoryRecordDao().getRecord(it) != null }.toSet()
        val projectScopes=memoryIds.mapNotNull { db.memoryRecordDao().getRecord(it)?.takeIf { row -> row.scope == "PROJECT" }?.scopeId }.distinct()
        val episode=if(prefs.mode == LearningPreference.OFF) null else builder.build(run,objectIds,memoryIds,turn?.id,projectScopes.singleOrNull())
        if(episode == null) { learning.dao.status(key,"NOT_REQUIRED","NONE",System.currentTimeMillis());return }
        learning.dao.episode(EpisodeEntity(episode.id.value,run.id.value,PersonalMemoryCodec.episode(episode),episode.endedAt.toEpochMilli()))
        android.util.Log.i("HALO_EPISODE_CREATED", "episode=${episode.id.value.takeLast(8)} run=${run.id.value.takeLast(8)} outcome=${episode.outcome} toolCount=${episode.toolIds.size} objectRefCount=${episode.referencedObjectIds.size}")
        val snapshot=environment()
        val requestedMode=ReflectionPolicy.mode(episode,prefs,busy(),snapshot.batteryPercent,snapshot.thermalLimited)
        val eligibleStatement=turn != null && turn.userText.length <= 1000 && MemorySensitivityClassifier.classify(turn.userText) !in setOf(MemorySensitivity.SECRET,MemorySensitivity.SENSITIVE)
        val mode=if(requestedMode == ReflectionMode.MODEL && !eligibleStatement) ReflectionMode.DETERMINISTIC else requestedMode
        android.util.Log.i("HALO_REFLECTION_DECISION", "episode=${episode.id.value.takeLast(8)} mode=$mode")
        val evidence=buildList { add(MemoryEvidenceRef(MemoryEvidenceKind.EPISODE,episode.id.value));add(MemoryEvidenceRef(MemoryEvidenceKind.AGENT_RUN,run.id.value));turn?.let { add(MemoryEvidenceRef(MemoryEvidenceKind.CONVERSATION_TURN,it.id)) } }
        var status=ReflectionStatus.COMPLETED
        val acceptedProposals=mutableListOf<MemoryProposal>()
        var procedureSuggestion: ProcedureSuggestion?=null
        var automationSuggestion: AutomationSuggestion?=null
        if(mode == ReflectionMode.MODEL && turn != null && turn.userText.length <= 1000 && MemorySensitivityClassifier.classify(turn.userText) !in setOf(MemorySensitivity.SECRET,MemorySensitivity.SENSITIVE)) {
            try {
                val draft=coroutineScope {
                    val task=async { model.reflect(ReflectionRequest(episode,listOf(turn.userText),evidence)) }
                    val preempt=launch { activeRuns.collect { if(it.any { r -> !r.state.isTerminal && r.parentRunId == null }) task.cancel() } }
                    try { task.await() } finally { preempt.cancel() }
                }
                val currentPreferences=preferences()
                if(draft == null || currentPreferences.mode != LearningPreference.SUGGEST || !currentPreferences.suggestMemories || !currentPreferences.modelReflection) status=ReflectionStatus.SKIPPED
                else draft.memoryCandidates.take(3).forEach { if(learning.propose(it)) acceptedProposals += it }
            } catch(cancelled: CancellationException) {
                currentCoroutineContext().ensureActive() // Foreground cancellation skips only this auxiliary child.
                status=ReflectionStatus.SKIPPED
            } catch(_: Exception) { status=ReflectionStatus.FAILED }
        }
        val currentPreferences=preferences()
        if(currentPreferences.mode == LearningPreference.SUGGEST) {
            val recent=learning.dao.recent(System.currentTimeMillis()-30L*86400000).mapNotNull { runCatching { PersonalMemoryCodec.decodeEpisode(it.json) }.getOrNull() }
            val saved=procedures.all().filter { it.enabled }.map { it.steps.map { step -> step.capabilityId } }.toSet()
            val routineRuns=db.routineDao().recentManualPatterns(System.currentTimeMillis()-30L*86400000)
            val patterns=routineRuns.mapNotNull { count ->
                val r=db.routineDao().get(count.routineId) ?: return@mapNotNull null
                val actions=runCatching { RoutineCodecBridge.actions(r.actionsJson) }.getOrDefault(emptyList())
                ManualRoutinePattern(count.routineId,count.count,actions.isNotEmpty() && actions.all { descriptor(it)?.automation?.backgroundCompatible == true })
            }
            val capabilityEvidence=try { capabilityPatterns(recent).take(4) }
                catch(cancelled: CancellationException) { throw cancelled }
                catch(_: Exception) { emptyList() }
            PersonalSuggestionEngine().suggest(recent,currentPreferences,saved,patterns,capabilityEvidence).filter { suggestion ->
                suggestion.tools.all { descriptor(it)?.security?.risk in setOf(ToolRiskClass.READ_ONLY,ToolRiskClass.REVERSIBLE) }
            }.forEach { suggestion -> if(learning.suggest(suggestion)) {
                when(suggestion.kind) {
                    PersonalSuggestionKind.PROCEDURE -> WorkflowSignature.of(suggestion.tools)?.let { procedureSuggestion=ProcedureSuggestion(it,suggestion.evidenceCount) }
                    PersonalSuggestionKind.AUTOMATION -> suggestion.targetId?.let { automationSuggestion=AutomationSuggestion(it,suggestion.evidenceCount) }
                    else -> Unit
                }
            } }
        }
        learning.dao.status(key,status.name,mode.name,System.currentTimeMillis())
        PersonalMemoryDiagnostics.reflection(status,mode)
        val reflection=Reflection(episode.id,if(acceptedProposals.isNotEmpty()) ReflectionUsefulness.PERSONAL else ReflectionUsefulness.OPERATIONAL,
            acceptedProposals,procedureSuggestion,automationSuggestion,Instant.now())
        try { publish(com.halo.sdk.runtime.PersonalReflectionCompleted("reflection:${episode.id.value}",run.id.value,episode.id.value,
            status.name,mode.name,reflection.usefulness.name,reflection.memoryProposals.size,reflection.procedureSuggestion != null,
            reflection.automationSuggestion != null,reflection.stage,reflection.createdAt.toEpochMilli())) }
        catch(cancelled: CancellationException) { throw cancelled }
        catch(_: Exception) { /* The durable status remains available to Activity. */ }
        learning.dao.pruneEpisodes(System.currentTimeMillis()-90L*86400000)
    }
}
private object RoutineCodecBridge { fun actions(json: String) = com.halo.app.data.routine.RoutineCodec().decodeActions(json,1).map { it.toolId } }
