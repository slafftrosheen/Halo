package com.halo.app.data.workspace

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.PrimaryKey
import com.halo.app.data.notebook.NoteBlockEntity
import com.halo.app.data.notebook.NoteEntity
import com.halo.app.data.notebook.NotebookCollectionEntity
import com.halo.app.data.notebook.NotebookCollectionMemberEntity
import com.halo.app.data.notebook.NotebookFavoriteEntity
import com.halo.app.data.studio.StudioProjectVersion
import com.halo.app.data.Memory
import com.halo.app.data.memory.MemoryRevisionEntity

/** Opaque, authenticated bytes only. The random envelope ID is unrelated to public item IDs. */
@Entity(tableName = "private_library_envelopes", primaryKeys = ["envelopeId", "part"])
data class PrivateLibraryChunk(val envelopeId: String, val part: Int, val bytes: ByteArray)

@Entity(tableName = "private_library_claims", indices = [androidx.room.Index("envelopeId")])
data class PrivateLibraryClaim(@PrimaryKey val fingerprint: String, val envelopeId: String)

/** A public restore can check an ID it already knows without reading private content. */
fun privateLibraryClaim(id: String): String = java.security.MessageDigest.getInstance("SHA-256")
    .digest("halo-private-library-claim-v1:$id".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

/** Separate domain prevents a replayed camera receipt from colliding with an item ID claim. */
internal fun privateLibraryCaptureClaim(sourceId: String, revision: String): String = java.security.MessageDigest.getInstance("SHA-256")
    .digest("halo-private-capture-claim-v1:${sourceId.length}:$sourceId:$revision".toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it) }

@Dao
interface PrivateLibraryDao {
    @Query("SELECT DISTINCT envelopeId FROM private_library_envelopes ORDER BY envelopeId")
    suspend fun envelopes(): List<String>

    @Query("SELECT DISTINCT envelopeId FROM private_library_envelopes ORDER BY envelopeId")
    fun observeEnvelopes(): kotlinx.coroutines.flow.Flow<List<String>>

    @Query("SELECT * FROM private_library_envelopes WHERE envelopeId = :id AND part >= 0 ORDER BY part")
    suspend fun chunks(id: String): List<PrivateLibraryChunk>

    @Query("SELECT bytes FROM private_library_envelopes WHERE envelopeId = :id AND part = -1")
    suspend fun summary(id: String): ByteArray?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun putChunks(chunks: List<PrivateLibraryChunk>)

    @Query("DELETE FROM private_library_envelopes WHERE envelopeId = :id")
    suspend fun removeChunks(id: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun putClaims(claims: List<PrivateLibraryClaim>)

    @Query("SELECT fingerprint FROM private_library_claims WHERE fingerprint IN (:hashes)")
    suspend fun claims(hashes: List<String>): List<String>

    @Query("DELETE FROM private_library_claims WHERE envelopeId = :id")
    suspend fun removeClaims(id: String): Int

    @Query("SELECT * FROM notes WHERE objectId = :id")
    suspend fun note(id: String): NoteEntity?

    @Query("SELECT * FROM notebook_collections WHERE id = :id")
    suspend fun collection(id: String): NotebookCollectionEntity?

    @Query("SELECT * FROM notebook_collection_members WHERE objectId = :id ORDER BY collectionId")
    suspend fun memberships(id: String): List<NotebookCollectionMemberEntity>

    @Query("SELECT * FROM notebook_favorites WHERE objectId = :id")
    suspend fun favorite(id: String): NotebookFavoriteEntity?

    @Query("SELECT * FROM workspace_library_title_aliases WHERE objectId = :id")
    suspend fun alias(id: String): WorkspaceLibraryTitleAlias?

    @Query("""SELECT
        COALESCE((SELECT length(CAST(committed AS BLOB)) + length(CAST(session AS BLOB)) FROM studio_projects WHERE objectId = :id), 0) +
        COALESCE((SELECT SUM(length(CAST(document AS BLOB))) FROM studio_project_versions WHERE objectId = :id), 0)
        """)
    suspend fun studioEncodedSize(id: String): Long

    @Query("SELECT * FROM studio_project_versions WHERE objectId = :id ORDER BY revision")
    suspend fun studioVersions(id: String): List<StudioProjectVersion>

    /** Workspace records that can display or contain a copy of the selected source. */
    @Query("""SELECT noteId FROM note_blocks WHERE noteId <> :id AND (refObjectId = :id OR instr(text, :id) > 0 OR instr(presentation, :id) > 0)
        UNION SELECT objectId FROM notes WHERE objectId <> :id AND (instr(body, :id) > 0 OR instr(pageSettings, :id) > 0)
        UNION SELECT objectId FROM note_drafts WHERE objectId <> :id AND (instr(body, :id) > 0 OR instr(structureJson, :id) > 0)
        UNION SELECT canvasId FROM canvas_nodes WHERE canvasId <> :id AND (refObjectId = :id OR instr(text, :id) > 0)
        UNION SELECT objectId FROM studio_projects WHERE objectId <> :id AND (instr(committed, :id) > 0 OR instr(session, :id) > 0)
        UNION SELECT objectId FROM studio_project_versions WHERE objectId <> :id AND instr(document, :id) > 0
        UNION SELECT objectId FROM object_provenance WHERE objectId <> :id
            AND (sourceId = :id OR sourceId = 'study-summary:' || :id OR instr(metadata, :id) > 0)
        UNION SELECT fromId FROM workspace_relations WHERE toId = :id AND type IN ('DERIVED_FROM', 'GENERATED_FROM')
        UNION SELECT toId FROM workspace_relations WHERE fromId = :id AND type = 'SOURCE_OF'
        UNION SELECT id FROM memory_records WHERE id <> :id AND (sourceObjectId = :id OR scopeId = :id OR instr(evidenceJson, :id) > 0)
        UNION SELECT id FROM personal_suggestions WHERE id <> :id AND instr(json, :id) > 0
        UNION SELECT 'mem_' || id FROM memories WHERE 'mem_' || id <> :id AND sourceObjectId = :id
        ORDER BY 1""")
    suspend fun dependentWorkspaceIds(id: String): List<String>

    @Query("""SELECT id FROM organizer_records WHERE id <> :id AND (parentId = :id OR instr(body, :id) > 0)
        UNION SELECT recordId FROM organizer_occurrences WHERE recordId <> :id AND instr(body, :id) > 0
        ORDER BY 1""")
    suspend fun dependentOrganizerIds(id: String): List<String>

    @Query("SELECT id FROM album_people WHERE referenceId = :id ORDER BY id")
    suspend fun dependentAlbumPeopleIds(id: String): List<String>

    @Query("""SELECT sessionId FROM study_practice_items WHERE sourceObjectId = :id
        UNION SELECT id FROM study_sessions WHERE instr(sourceObjectIdsJson, :id) > 0 OR instr(sourcesJson, :id) > 0""")
    suspend fun directStudyDependencies(id: String): List<String>

    @Query("SELECT * FROM workspace_relations WHERE fromId = :id OR toId = :id ORDER BY id")
    suspend fun relations(id: String): List<WorkspaceRelationEntity>

    @Query("DELETE FROM workspace_relations WHERE fromId = :id OR toId = :id")
    suspend fun withdrawRelations(id: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun restoreRelations(relations: List<WorkspaceRelationEntity>)

    /** Owners with live execution state need their own withdrawal path. */
    @Query("""SELECT
        (:allowNoteAssets = 0 AND EXISTS(SELECT 1 FROM workspace_assets WHERE objectId = :id)) OR
        (:allowNoteAssets = 0 AND EXISTS(SELECT 1 FROM note_attachments WHERE noteId = :id)) OR
        EXISTS(SELECT 1 FROM personal_episodes WHERE instr(json, :id) > 0 AND instr(:episodes, '|' || id || '|') = 0) OR
        EXISTS(SELECT 1 FROM agent_runs WHERE instr(delegatedInputRefs, :id) > 0 AND instr(:runs, '|' || id || '|') = 0) OR
        EXISTS(SELECT 1 FROM agent_steps WHERE instr(payloadJson, :id) > 0 AND instr(:runs, '|' || runId || '|') = 0) OR
        (:allowToolResults = 0 AND EXISTS(SELECT 1 FROM payload_refs WHERE ownerId = :id)) OR
        EXISTS(SELECT 1 FROM routines WHERE instr(:automation, '|' || id || '|') = 0 AND
            (instr(actionsJson, :id) > 0 OR instr(conditionsJson, :id) > 0 OR instr(triggerJson, :id) > 0 OR instr(deliveryJson, :id) > 0)) OR
        EXISTS(SELECT 1 FROM continuation_watches WHERE instr(:automation, '|' || id || '|') = 0 AND
            (runId = :id OR interactionId = :id OR instr(conditionJson, :id) > 0 OR instr(behaviorJson, :id) > 0 OR
                instr(presentationText, :id) > 0))
        """)
    suspend fun hasUnsealedOwners(id: String, allowNoteAssets: Boolean, allowToolResults: Boolean,
        runs: String, episodes: String, automation: String): Boolean

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun restoreBlocks(blocks: List<NoteBlockEntity>)

    @Query("DELETE FROM workspace_objects WHERE id = :id AND type IN ('NOTE', 'FILE', 'MEDIA', 'CANVAS', 'ARTIFACT', 'MEMORY')")
    suspend fun withdrawPublicItem(id: String): Int

    @Query("SELECT * FROM memories WHERE id = :id")
    suspend fun legacyMemory(id: Int): Memory?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun restoreLegacyMemory(row: Memory)

    @Query("SELECT * FROM memory_revisions WHERE memoryId = :id ORDER BY id")
    suspend fun memoryRevisions(id: String): List<MemoryRevisionEntity>

    @Query("DELETE FROM memory_revisions WHERE memoryId = :id")
    suspend fun withdrawMemoryRevisions(id: String)

    @Query("DELETE FROM memory_records WHERE id = :id")
    suspend fun withdrawMemory(id: String): Int

    @Query("DELETE FROM object_provenance WHERE objectId = :id")
    suspend fun withdrawProvenance(id: String): Int
}
