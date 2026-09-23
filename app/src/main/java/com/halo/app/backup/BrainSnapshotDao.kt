package com.halo.app.backup

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.halo.app.data.ObjectProvenance
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

@Dao
interface BrainSnapshotDao {
    @Query("SELECT * FROM workspace_objects ORDER BY createdAt, id") suspend fun workspaceObjects(): List<WorkspaceObjectEntity>
    @Query("SELECT * FROM notes ORDER BY objectId") suspend fun notes(): List<NoteEntity>
    @Query("SELECT * FROM conversations ORDER BY createdAt, id") suspend fun conversations(): List<PersistedConversation>
    @Query("SELECT * FROM conversation_turns ORDER BY timestamp, id") suspend fun turns(): List<PersistedTurn>
    @Query("SELECT * FROM memory_records ORDER BY createdAt, id") suspend fun memories(): List<MemoryRecordEntity>
    @Query("SELECT * FROM memory_revisions ORDER BY changedAt, id") suspend fun memoryRevisions(): List<MemoryRevisionEntity>
    @Query("SELECT * FROM working_set ORDER BY addedAt, objectId") suspend fun workingSet(): List<WorkingSetEntry>
    @Query("SELECT * FROM object_provenance ORDER BY objectId") suspend fun provenance(): List<ObjectProvenance>
    @Query("SELECT * FROM workspace_relations ORDER BY createdAt, id") suspend fun relations(): List<WorkspaceRelationEntity>
    @Query("SELECT * FROM workspace_files ORDER BY objectId") suspend fun files(): List<WorkspaceFileEntity>
    @Query("SELECT * FROM agent_runs ORDER BY startedAt, id") suspend fun agentRuns(): List<AgentRunEntity>
    @Query("SELECT * FROM agent_steps ORDER BY createdAt, id") suspend fun agentSteps(): List<AgentStepEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putWorkspaceObjects(values: List<WorkspaceObjectEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putNotes(values: List<NoteEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putConversations(values: List<PersistedConversation>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putTurns(values: List<PersistedTurn>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putMemories(values: List<MemoryRecordEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putMemoryRevisions(values: List<MemoryRevisionEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putWorkingSet(values: List<WorkingSetEntry>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putProvenance(values: List<ObjectProvenance>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putRelations(values: List<WorkspaceRelationEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putFiles(values: List<WorkspaceFileEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putAgentRuns(values: List<AgentRunEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putAgentSteps(values: List<AgentStepEntity>)

    @Query("DELETE FROM agent_steps") suspend fun clearAgentSteps()
    @Query("DELETE FROM agent_runs") suspend fun clearAgentRuns()
    @Query("DELETE FROM workspace_files") suspend fun clearFiles()
    @Query("DELETE FROM workspace_relations") suspend fun clearRelations()
    @Query("DELETE FROM object_provenance") suspend fun clearProvenance()
    @Query("DELETE FROM working_set") suspend fun clearWorkingSet()
    @Query("DELETE FROM memory_revisions") suspend fun clearMemoryRevisions()
    @Query("DELETE FROM memory_records") suspend fun clearMemories()
    @Query("DELETE FROM memories") suspend fun clearLegacyMemories()
    @Query("DELETE FROM conversation_turns") suspend fun clearTurns()
    @Query("DELETE FROM conversations") suspend fun clearConversations()
    @Query("DELETE FROM notes") suspend fun clearNotes()
    @Query("DELETE FROM note_drafts") suspend fun clearDrafts()
    @Query("DELETE FROM workspace_objects") suspend fun clearWorkspaceObjects()
}
