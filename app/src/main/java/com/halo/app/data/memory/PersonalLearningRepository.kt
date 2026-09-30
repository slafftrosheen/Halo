package com.halo.app.data.memory

import androidx.room.withTransaction
import com.halo.app.data.AppDatabase
import com.halo.app.data.notebook.WorkspaceObjectEntity
import com.halo.runtime.cognition.memory.*
import com.halo.runtime.cognition.procedures.*
import kotlinx.coroutines.flow.map
import java.time.Instant

/** One Inbox projection over the existing Room database. This never creates behavior automatically. */
class PersonalLearningRepository(private val db: AppDatabase, private val memories: RoomMemoryRepository,
    private val budget: SuggestionBudget = SuggestionBudget(),
    private val attention: (() -> com.halo.runtime.cognition.attention.AttentionController)? = null) {
    val dao = db.personalMemoryDao()
    val pending = dao.pending()
    val episodes = dao.episodes().map { rows -> rows.mapNotNull { runCatching { PersonalMemoryCodec.decodeEpisode(it.json) }.getOrNull() } }
    val reflections = dao.reflections()
    suspend fun isRootRun(id: String): Boolean { val run = db.agentRunDao().get(id)?.run ?: return false; return run.parentRunId == null }
    suspend fun exists(ref: MemoryEvidenceRef): Boolean = when(ref.kind) {
        MemoryEvidenceKind.AGENT_RUN, MemoryEvidenceKind.ROUTINE_RUN -> db.agentRunDao().get(ref.id) != null
        MemoryEvidenceKind.WORKSPACE_OBJECT -> db.notebookDao().getWorkspaceObject(ref.id)?.let { objectRow ->
            ref.revision == null || if(objectRow.type == "NOTE") db.notebookDao().getNote(ref.id)?.revision?.toString() == ref.revision
                else objectRow.updatedAt.toString() == ref.revision
        } == true
        MemoryEvidenceKind.EPISODE -> dao.episodeById(ref.id) != null
        MemoryEvidenceKind.CONVERSATION_TURN -> db.conversationDao().turnById(ref.id) != null
        MemoryEvidenceKind.USER_ACTION -> ref.id.startsWith("approved:")
        MemoryEvidenceKind.IMPORTED -> false
    }
    suspend fun propose(p: MemoryProposal): Boolean {
        if(p.kind == MemoryKind.PROJECT && p.scope != MemoryScope.PROJECT || !MemorySensitivityClassifier.mayPropose(p.content,p.origin,p.sensitivity) || p.evidence.any { !exists(it) }) return false
        val candidate = p.candidate(); val conflict = memories.conflict(candidate)
        // A repeated inferred conclusion does not silently reinforce or mutate a confirmed personal fact.
        if(conflict.kind == MemoryConflictKind.REINFORCES) return false
        val revised = p.copy(conflictMemoryIds = conflict.records.map { it.id })
        val fingerprint = "memory:" + MemoryProposalFingerprint.of(p.kind,p.scope,p.scopeId,p.content,p.subject)
        val id = "learning:$fingerprint"
        android.util.Log.i("HALO_MEMORY_PROPOSAL", "proposal=${id.takeLast(8)} kind=${p.kind} origin=${p.origin} confidenceBand=${if(p.confidence >= .8f) "likely" else "suggested"} evidenceCount=${p.evidence.size}")
        if(conflict.records.isNotEmpty()) android.util.Log.i("HALO_MEMORY_CONFLICT", "proposal=${id.takeLast(8)} existingCount=${conflict.records.size}")
        val saved=store(PersonalSuggestionEntity(id,fingerprint,"MEMORY",PersonalMemoryCodec.proposal(revised),"PENDING",p.createdAt.toEpochMilli(),p.createdAt.toEpochMilli(),p.evidence.size), p.content)
        if(saved) surface(id);return saved
    }
    suspend fun suggest(s: PersonalSuggestion): Boolean {
        val saved=store(PersonalSuggestionEntity("learning:${s.fingerprint}",s.fingerprint,s.kind.name,PersonalMemoryCodec.suggestion(s),"PENDING",System.currentTimeMillis(),System.currentTimeMillis(),s.evidenceCount),s.title)
        if(saved) surface("learning:${s.fingerprint}");return saved
    }
    private suspend fun surface(id: String) {
        try { attention?.invoke()?.submit(com.halo.runtime.cognition.attention.AttentionRequest(
            com.halo.runtime.cognition.attention.AttentionRequestId("suggestion:$id"),com.halo.runtime.cognition.attention.AttentionSource.PersonalSuggestion,
            importance=com.halo.runtime.cognition.attention.AttentionImportance.BACKGROUND,
            deliveryIntent=com.halo.runtime.cognition.attention.DeliveryIntent.Inform(com.halo.runtime.cognition.attention.UserFacingContent("Learning suggestion","Review in Notebook Inbox")),
            deduplicationKey="suggestion:$id",expiresAt=Instant.now().plusSeconds(86400))) }
        catch(cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch(_: Exception) { /* Durable Inbox entry remains the truth even if passive surfacing fails. */ }
    }
    private suspend fun store(row: PersonalSuggestionEntity,title: String): Boolean = db.withTransaction {
        expire()
        val old = dao.fingerprint(row.fingerprint); val now = System.currentTimeMillis()
        if(old != null && (old.state == "PENDING" || old.state == "ACCEPTED" || old.neverAgain || row.evidenceCount <= old.evidenceCount || now - old.updatedAt < budget.cooldownDays * 86400000)) return@withTransaction false
        if(dao.pendingCount() >= budget.maxPending || dao.newCount(now - 86400000) >= budget.maxNewPerDay) return@withTransaction false
        val item = row.copy(id = old?.id ?: row.id, createdAt = now, updatedAt = now)
        dao.put(item)
        db.notebookDao().insertWorkspaceObject(WorkspaceObjectEntity(item.id,"MEMORY",title.take(80),now,now,"HALO_GENERATED","ACTIVE",true,"READY","learning:${item.id}"))
        android.util.Log.i("HALO_SUGGESTION_CREATED", "kind=${item.kind} fingerprint=${item.fingerprint.takeLast(8)} evidenceCount=${item.evidenceCount}")
        true
    }
    suspend fun expire() { dao.expiring(System.currentTimeMillis() - 30L*86400000).forEach { row -> decide(row,"EXPIRED",false) } }
    suspend fun dismiss(id: String,neverAgain: Boolean = false) = db.withTransaction {
        val row = dao.suggestionById(id)?.takeIf { it.state == "PENDING" } ?: return@withTransaction
        decide(row,"DISMISSED",neverAgain)
        android.util.Log.i("HALO_SUGGESTION_DISMISSED", "kind=${row.kind} fingerprint=${row.fingerprint.takeLast(8)}")
    }
    private suspend fun decide(row: PersonalSuggestionEntity,state: String,neverAgain: Boolean) {
        val now=System.currentTimeMillis();dao.decide(row.id,state,now,if(state == "DISMISSED") now else row.dismissedAt,neverAgain)
        db.notebookDao().setLifecycle(row.id,"ARCHIVED",now)
    }
    suspend fun conflicts(id: String): MemoryConflict {
        val row=requireNotNull(dao.suggestionById(id));return memories.conflict(PersonalMemoryCodec.decodeProposal(row.json).candidate())
    }
    suspend fun acceptMemory(id: String,editedContent: String? = null,replaceMemoryId: String? = null): MemoryRecord = db.withTransaction {
        val row=requireNotNull(dao.suggestionById(id));require(row.state == "PENDING" && row.kind == "MEMORY")
        val proposal=PersonalMemoryCodec.decodeProposal(row.json)
        require(proposal.evidence.all { exists(it) }) { "Evidence unavailable; edit and save manually if still useful" }
        val candidate=proposal.candidate().copy(content = editedContent?.trim() ?: proposal.content,acceptedByUser = true)
        val conflict=memories.conflict(candidate)
        val result=if(conflict.kind == MemoryConflictKind.CONFLICTS) {
            require(conflict.records.size == 1 && conflict.records.single().id == replaceMemoryId) { "Review the current conflicting memory before accepting" }
            check(memories.acceptUpdate(requireNotNull(replaceMemoryId),candidate));requireNotNull(memories.getMemory(replaceMemoryId))
        } else memories.promoteCandidate(candidate)
        decide(row,"ACCEPTED",false);result
    }
    suspend fun saveProcedure(id: String,name: String,description: String,store: LearnedProcedureStore,known: (String)->Boolean) = db.withTransaction {
        val row=requireNotNull(dao.suggestionById(id));require(row.state == "PENDING" && row.kind == "PROCEDURE")
        val suggestion=PersonalMemoryCodec.decodeSuggestion(row.json)
        require(name.trim().length in 1..80 && description.length <= 400 && suggestion.tools.size in 2..12 && suggestion.tools.all(known))
        val now=System.currentTimeMillis()
        // Fixed identity makes crash recovery idempotent across the existing preference-backed store.
        store.upsert(LearnedProcedure("accepted:${row.id}",name.trim(),description,listOf(name.trim()),suggestion.tools.map { ProcedureStep(it,"Review and perform the configured operation") },ProcedureOrigin.USER_CREATED,now,now))
        decide(row,"ACCEPTED",false)
    }
    suspend fun reviewed(id: String) { val row=dao.suggestionById(id) ?: return;require(row.kind != "MEMORY" && row.kind != "PROCEDURE");decide(row,"ACCEPTED",false) }
    private fun MemoryProposal.candidate() = MemoryCandidate(content,kind=kind,scope=scope,scopeId=scopeId,confidence=confidence,origin=origin,evidence=evidence,
        sourceEpisodeId=evidence.firstOrNull { it.kind == MemoryEvidenceKind.EPISODE }?.id)
}
