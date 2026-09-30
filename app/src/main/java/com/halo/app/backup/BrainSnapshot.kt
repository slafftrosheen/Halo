package com.halo.app.backup

import android.content.Context
import androidx.room.withTransaction
import com.halo.app.data.AppDatabase
import com.halo.app.data.ObjectProvenance
import com.halo.app.data.Memory
import com.halo.app.data.PersistedConversation
import com.halo.app.data.PersistedTurn
import com.halo.app.data.WorkingSetEntry
import com.halo.app.data.agent.AgentRunEntity
import com.halo.app.data.agent.AgentStepEntity
import com.halo.app.data.memory.MemoryRecordEntity
import com.halo.app.data.memory.MemoryRevisionEntity
import com.halo.app.data.notebook.NoteEntity
import com.halo.app.data.notebook.WorkspaceObjectEntity
import com.halo.app.data.workspace.WorkspaceFileEntity
import com.halo.app.data.workspace.WorkspaceRelationEntity
import com.halo.sdk.runtime.Storage
import com.halo.sdk.runtime.embodiment.PersistentDeviceRegistry
import com.halo.host.android.reminder.ReminderRecordStore
import com.halo.sdk.provider.AiModelRole
import com.squareup.moshi.JsonClass
import java.io.File
import java.util.UUID
import org.json.JSONObject

@JsonClass(generateAdapter = true)
data class WorkspaceObjectSnapshot(val id: String, val type: String, val title: String, val createdAt: Long, val updatedAt: Long, val source: String, val lifecycle: String, val inInbox: Boolean, val indexingState: String, val contentRef: String)
@JsonClass(generateAdapter = true)
data class NoteSnapshot(val objectId: String, val body: String, val revision: Int)
@JsonClass(generateAdapter = true)
data class ConversationSnapshot(val id: String, val createdAt: Long, val active: Boolean, val title: String, val updatedAt: Long, val archived: Boolean)
@JsonClass(generateAdapter = true)
data class TurnSnapshot(val id: String, val conversationId: String, val userText: String, val assistantText: String, val timestamp: Long, val metadata: String)
@JsonClass(generateAdapter = true)
data class MemorySnapshot(val id: String, val content: String, val kind: String, val scope: String, val scopeId: String?, val createdAt: Long, val updatedAt: Long, val sourceObjectId: String?, val sourceRevision: Int?, val confidence: Float?, val status: String,
    val origin: String = "IMPORT", val evidenceJson: String = "[]", val sourceEpisodeId: String? = null,
    val evidenceCount: Int = 0, val lastConfirmedAt: Long? = null, val usageCount: Long = 0, val lastUsedAt: Long? = null,
    val validUntil: Long? = null, val fingerprint: String = "", val userConfirmed: Boolean = false)
@JsonClass(generateAdapter = true)
data class MemoryRevisionSnapshot(val id: String, val memoryId: String, val previousContent: String, val newContent: String, val changedAt: Long, val reason: String)
@JsonClass(generateAdapter = true)
data class LegacyMemorySnapshot(val legacyId: Int, val content: String, val timestamp: Long, val sourceObjectId: String?, val sourceRevision: Int?)
@JsonClass(generateAdapter = true)
data class WorkingSetSnapshot(val objectId: String, val addedAt: Long, val source: String)
@JsonClass(generateAdapter = true)
data class ProvenanceSnapshot(val objectId: String, val sourceId: String, val revision: String, val metadata: String)
@JsonClass(generateAdapter = true)
data class RelationSnapshot(val id: String, val fromId: String, val type: String, val toId: String, val createdAt: Long)
@JsonClass(generateAdapter = true)
data class FileSnapshot(val objectId: String, val uri: String, val displayName: String, val mimeType: String, val sizeBytes: Long?, val modifiedAt: Long?, val ownedCopyPath: String?, val permissionPersisted: Boolean, val ownership: String, val availability: String, val sha256: String?)
@JsonClass(generateAdapter = true)
data class AgentRunSnapshot(val id: String, val interactionId: String = id, val request: String, val startedAt: Long, val updatedAt: Long = startedAt, val completedAt: Long?, val state: String,
    val parentRunId: String? = null, val rootRunId: String? = null, val delegatedRole: String? = null,
    val delegationDepth: Int = 0, val pinnedProviderId: String? = null, val pinnedModelId: String? = null,
    val pinnedTargetLocal: Boolean? = null, val delegatedInputRefs: String? = null)
@JsonClass(generateAdapter = true)
data class AgentStepSnapshot(val id: String, val runId: String, val interactionId: String = runId, val ordinal: Int = 0, val stepType: String, val schemaVersion: Int = 2, val payload: String, val idempotencyKey: String? = null, val state: String = "COMPLETED", val timestamp: Long)
@JsonClass(generateAdapter = true)
data class ReminderSnapshot(val id: String, val message: String, val triggerAtEpochMs: Long, val precision: String)
@JsonClass(generateAdapter = true)
data class DeviceConfigurationSnapshot(val id: String, val displayName: String, val deviceType: String, val firmwareVersion: String?, val preferredAutoConnect: Boolean)
@JsonClass(generateAdapter = true)
data class UserSettingsSnapshot(
    val values: Map<String, String>,
    val providerConfiguration: Map<String, Boolean>,
    val skillEnabled: Map<String, Boolean> = emptyMap(),
    val booleanValues: Map<String, Boolean> = emptyMap(),
    val integerValues: Map<String, Int> = emptyMap(),
    val appearanceMode: String? = null,
)

data class BrainSnapshot(
    val snapshotId: String,
    val createdAt: Long,
    val workspace: List<WorkspaceObjectSnapshot>,
    val notes: List<NoteSnapshot>,
    val conversations: List<ConversationSnapshot>,
    val turns: List<TurnSnapshot>,
    val memories: List<MemorySnapshot>,
    val legacyMemories: List<LegacyMemorySnapshot>,
    val memoryRevisions: List<MemoryRevisionSnapshot>,
    val workingSet: List<WorkingSetSnapshot>,
    val provenance: List<ProvenanceSnapshot>,
    val relations: List<RelationSnapshot>,
    val files: List<FileSnapshot>,
    val agentRuns: List<AgentRunSnapshot>,
    val agentSteps: List<AgentStepSnapshot>,
    val reminders: List<ReminderSnapshot>,
    val devices: List<DeviceConfigurationSnapshot>,
    val settings: UserSettingsSnapshot,
    val episodes: List<EpisodeSnapshot> = emptyList(),
    val suggestionDecisions: List<SuggestionDecisionSnapshot> = emptyList(),
    val reflectionStatuses: List<ReflectionStatusSnapshot> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class EpisodeSnapshot(val id: String,val runId: String,val json: String,val endedAt: Long,val importance: Int,val usageCount: Long,val lastReferencedAt: Long?)
@JsonClass(generateAdapter = true)
data class SuggestionDecisionSnapshot(val id: String,val fingerprint: String,val kind: String,val state: String,val createdAt: Long,val updatedAt: Long,val evidenceCount: Int,val dismissedAt: Long?,val neverAgain: Boolean)

@JsonClass(generateAdapter = true)
data class ReflectionStatusSnapshot(val id: String,val policyVersion: Int,val status: String,val mode: String,val updatedAt: Long)

interface BrainSnapshotSource { suspend fun snapshot(): BrainSnapshot }

class RoomBrainSnapshotSource(
    private val context: Context,
    private val database: AppDatabase,
    private val storage: Storage,
) : BrainSnapshotSource {
    override suspend fun snapshot(): BrainSnapshot = database.withTransaction {
        val dao = database.brainSnapshotDao()
        val notes = dao.notes()
        val provenance = dao.provenance()
        val storedFiles = dao.files()
        val captureFiles = provenance.mapNotNull { record ->
            if (storedFiles.any { it.objectId == record.objectId }) return@mapNotNull null
            val metadata = runCatching { JSONObject(record.metadata) }.getOrNull() ?: return@mapNotNull null
            val path = metadata.optString("localPath")
            val source = path.takeIf(String::isNotBlank)?.let(::File)?.takeIf(File::isFile) ?: return@mapNotNull null
            val note = notes.firstOrNull { it.objectId == record.objectId } ?: return@mapNotNull null
            val uri = Regex("\\[Open image\\]\\(([^)]+)\\)").find(note.body)?.groupValues?.get(1).orEmpty()
            FileSnapshot(record.objectId, uri, source.name, metadata.optString("mimeType", "image/jpeg"), source.length(), source.lastModified(), source.absolutePath, false, "HALO_OWNED", "OWNED_LOCAL", null)
        }
        BrainSnapshot(
            snapshotId = UUID.randomUUID().toString(),
            createdAt = System.currentTimeMillis(),
            workspace = dao.workspaceObjects().filterNot { it.id.startsWith("learning:") }.map { WorkspaceObjectSnapshot(it.id, it.type, it.title, it.createdAt, it.updatedAt, it.source, it.lifecycle, it.inInbox, it.indexingState, it.contentRef) },
            notes = notes.map { NoteSnapshot(it.objectId, it.body, it.revision) },
            conversations = dao.conversations().map { ConversationSnapshot(it.id, it.createdAt, it.active, it.title, it.updatedAt, it.archived) },
            turns = dao.turns().map { TurnSnapshot(it.id, it.conversationId, it.userText, it.assistantText, it.timestamp, it.metadata) },
            memories = dao.memories().map { MemorySnapshot(it.id, it.content, it.kind, it.scope, it.scopeId, it.createdAt, it.updatedAt, it.sourceObjectId, it.sourceRevision, it.confidence, it.status,it.origin,it.evidenceJson,it.sourceEpisodeId,it.evidenceCount,it.lastConfirmedAt,it.usageCount,it.lastUsedAt,it.validUntil,it.fingerprint,it.userConfirmed) },
            legacyMemories = emptyList(), // Legacy storage is an import archive, never a second export of personal facts.
            memoryRevisions = dao.memoryRevisions().map { MemoryRevisionSnapshot(it.id, it.memoryId, it.previousContent, it.newContent, it.changedAt, it.reason) },
            workingSet = dao.workingSet().map { WorkingSetSnapshot(it.objectId, it.addedAt, it.source) },
            provenance = provenance.map { ProvenanceSnapshot(it.objectId, it.sourceId, it.revision, it.metadata) },
            relations = dao.relations().map { RelationSnapshot(it.id, it.fromId, it.type, it.toId, it.createdAt) },
            files = storedFiles.map { FileSnapshot(it.objectId, it.uri, it.displayName, it.mimeType, it.sizeBytes, it.modifiedAt, it.ownedCopyPath, it.permissionPersisted, it.ownership, it.availability, it.sha256) } + captureFiles,
            agentRuns = dao.agentRuns().map { AgentRunSnapshot(it.id, it.interactionId, it.request, it.startedAt, it.updatedAt, it.completedAt, it.state, it.parentRunId, it.rootRunId, it.delegatedRole, it.delegationDepth, it.pinnedProviderId, it.pinnedModelId, it.pinnedTargetLocal, it.delegatedInputRefs) },
            agentSteps = dao.agentSteps().map { AgentStepSnapshot(it.id, it.runId, it.interactionId, it.ordinal, it.type, it.schemaVersion, it.payloadJson, it.idempotencyKey, it.state, it.createdAt) },
            reminders = ReminderRecordStore(context).all().map { ReminderSnapshot(it.id, it.message, it.triggerAtEpochMs, it.precision) },
            devices = PersistentDeviceRegistry(storage).getSavedDevices().map {
                DeviceConfigurationSnapshot(it.identity.deviceId, it.identity.displayName, it.identity.deviceType, it.firmwareVersion, it.autoConnect)
            },
            episodes = database.personalMemoryDao().savedEpisodes().map { EpisodeSnapshot(it.id,it.runId,it.json,it.endedAt,it.importance,it.usageCount,it.lastReferencedAt) },
            reflectionStatuses = database.personalMemoryDao().reflectionHistory().map { ReflectionStatusSnapshot(it.id,it.policyVersion,it.status,it.mode,it.updatedAt) },
            suggestionDecisions = database.personalMemoryDao().durableDecisions().map { SuggestionDecisionSnapshot(it.id,it.fingerprint,it.kind,it.state,it.createdAt,it.updatedAt,it.evidenceCount,it.dismissedAt,it.neverAgain) },
            settings = UserSettingsSnapshot(
                values = (listOf("active_ai_provider", "active_ai_model", "ai_routing_policy", "ollama_endpoint", "speech_locale", "speech_stt_provider", "speech_tts_provider", "learning_mode") +
                    AiModelRole.entries.flatMap { listOf("role_${it.name}_selection", "role_${it.name}_provider", "role_${it.name}_model") })
                    .mapNotNull { key -> storage.getString(key)?.let { key to it } }.toMap(),
                providerConfiguration = mapOf("openrouter" to !storage.getString("openrouter_api_key").isNullOrBlank()),
                skillEnabled = com.halo.runtime.cognition.skills.DefaultSkillRegistry.defaultBuiltInSkills()
                    .associate { it.id.value to storage.getBoolean("skill_enabled_${it.id.value}", it.enabled) },
                booleanValues = listOf(
                    "policy_conversation", "policy_device_state", "policy_recent_events", "policy_memories",
                    "policy_tools", "policy_location", "policy_images", "policy_audio", "policy_video",
                    "policy_cloud_workspace", "policy_cloud_memories", "learning_memories", "learning_procedures", "learning_automations", "learning_model_reflection",
                ).associateWith { storage.getBoolean(it, it in setOf("learning_memories","learning_procedures","learning_automations")) },
                integerValues = listOf("litert_context", "ai_temperature")
                    .associateWith { storage.getInt(it, 0) },
                appearanceMode = context.getSharedPreferences("halo_appearance", Context.MODE_PRIVATE)
                    .getString("mode", null),
            ),
        )
    }
}

internal fun BrainSnapshot.toRoomContent() = RoomBrainContent(
    workspace.map { WorkspaceObjectEntity(it.id, it.type, it.title, it.createdAt, it.updatedAt, it.source, it.lifecycle, it.inInbox, it.indexingState, it.contentRef) },
    notes.map { NoteEntity(it.objectId, it.body, it.revision) },
    conversations.map { PersistedConversation(it.id, it.createdAt, it.active, it.title, it.updatedAt, it.archived) },
    turns.map { PersistedTurn(it.id, it.conversationId, it.userText, it.assistantText, it.timestamp, it.metadata) },
    memories.map { MemoryRecordEntity(it.id, it.content, it.kind, it.scope, it.scopeId, it.createdAt, it.updatedAt, it.sourceObjectId, it.sourceRevision, it.confidence, it.status,it.origin,it.evidenceJson,it.sourceEpisodeId,it.evidenceCount,it.lastConfirmedAt,it.usageCount,it.lastUsedAt,it.validUntil,it.fingerprint,it.userConfirmed) },
    memoryRevisions.map { MemoryRevisionEntity(it.id, it.memoryId, it.previousContent, it.newContent, it.changedAt, it.reason) },
    workingSet.map { WorkingSetEntry(it.objectId, it.addedAt, it.source) },
    provenance.map { ObjectProvenance(it.objectId, it.sourceId, it.revision, it.metadata) },
    relations.map { WorkspaceRelationEntity(it.id, it.fromId, it.type, it.toId, it.createdAt) },
    files.map { WorkspaceFileEntity(it.objectId, it.uri, it.displayName, it.mimeType, it.sizeBytes, it.modifiedAt, it.ownedCopyPath, it.permissionPersisted, it.ownership, it.availability, it.sha256) },
    agentRuns.map { AgentRunEntity(it.id, it.interactionId, it.request, it.startedAt, it.updatedAt, it.completedAt, it.state,
        parentRunId = it.parentRunId, rootRunId = it.rootRunId, delegatedRole = it.delegatedRole,
        delegationDepth = it.delegationDepth, pinnedProviderId = it.pinnedProviderId, pinnedModelId = it.pinnedModelId,
        pinnedTargetLocal = it.pinnedTargetLocal, delegatedInputRefs = it.delegatedInputRefs) },
    agentSteps.map { AgentStepEntity(it.id, it.runId, it.interactionId, it.ordinal, it.stepType, it.schemaVersion, it.payload, it.idempotencyKey, it.state, it.timestamp, it.timestamp) },
)

internal data class RoomBrainContent(
    val workspace: List<WorkspaceObjectEntity>, val notes: List<NoteEntity>, val conversations: List<PersistedConversation>,
    val turns: List<PersistedTurn>, val memories: List<MemoryRecordEntity>, val memoryRevisions: List<MemoryRevisionEntity>,
    val workingSet: List<WorkingSetEntry>, val provenance: List<ObjectProvenance>, val relations: List<WorkspaceRelationEntity>,
    val files: List<WorkspaceFileEntity>, val agentRuns: List<AgentRunEntity>, val agentSteps: List<AgentStepEntity>,
)
