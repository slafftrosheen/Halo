package com.halo.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.halo.app.data.agent.AgentRunDao
import com.halo.app.data.agent.AgentRunEntity
import com.halo.app.data.agent.AgentStepEntity
import com.halo.app.data.memory.MemoryRecordDao
import com.halo.app.data.memory.MemoryRecordEntity
import com.halo.app.data.memory.MemoryRevisionEntity
import com.halo.app.data.models.ModelArtifactEntity
import com.halo.app.data.models.ModelRuntimeProfileDao
import com.halo.app.data.models.ModelRuntimeProfileEntity
import com.halo.app.data.notification.NotificationEntity
import com.halo.app.data.notebook.NoteEntity
import com.halo.app.data.notebook.NoteDraftEntity
import com.halo.app.data.notebook.NoteBlockEntity
import com.halo.app.data.notebook.NotebookDocumentDao
import com.halo.app.data.notebook.NotebookCollectionEntity
import com.halo.app.data.notebook.NotebookCollectionMemberEntity
import com.halo.app.data.notebook.NotebookFavoriteEntity
import com.halo.app.data.notebook.NotebookOrganizationDao
import com.halo.app.data.notebook.CanvasDocumentEntity
import com.halo.app.data.notebook.CanvasNodeEntity
import com.halo.app.data.notebook.CanvasEdgeEntity
import com.halo.app.data.notebook.NotebookCanvasDao
import com.halo.app.data.notebook.WorkspaceObjectEntity
import com.halo.app.data.routine.RoutineEntity
import com.halo.app.data.routine.RoutineRunEntity
import com.halo.app.data.study.StudyAttemptEntity
import com.halo.app.data.study.StudyDao
import com.halo.app.data.study.StudyFeedbackEntity
import com.halo.app.data.study.StudyPracticeItemEntity
import com.halo.app.data.study.StudyReviewEventEntity
import com.halo.app.data.study.StudySessionEntity
import com.halo.app.data.workspace.WorkspaceAssetEntity
import com.halo.app.data.workspace.NoteAttachmentEntity
import com.halo.app.data.workspace.WorkspaceAssetDao
import com.halo.app.data.workspace.WorkspaceFileDao
import com.halo.app.data.workspace.WorkspaceFileEntity
import com.halo.app.data.workspace.WorkspaceRelationEntity
import com.halo.app.backup.BackupRecordDao
import com.halo.app.backup.BackupRecordEntity
import com.halo.app.backup.BrainSnapshotDao
import com.halo.app.data.attention.AttentionReceiptEntity
import com.halo.app.data.notebook.NotebookDao
import com.halo.app.data.watch.ContinuationWatchEntity

@Database(
    entities = [
        Memory::class,
        WorkspaceObjectEntity::class,
        NoteEntity::class,
        NoteDraftEntity::class,
        NoteBlockEntity::class,
        NotebookCollectionEntity::class,
        NotebookCollectionMemberEntity::class,
        NotebookFavoriteEntity::class,
        CanvasDocumentEntity::class,
        CanvasNodeEntity::class,
        CanvasEdgeEntity::class,
        PersistedTurn::class,
        PersistedConversation::class,
        WorkingSetEntry::class,
        ObjectProvenance::class,
        WorkspaceFileEntity::class,
        MemoryRecordEntity::class,
        MemoryRevisionEntity::class,
        com.halo.app.data.memory.EpisodeEntity::class,
        com.halo.app.data.memory.ReflectionStateEntity::class,
        com.halo.app.data.memory.PersonalSuggestionEntity::class,
        WorkspaceRelationEntity::class,
        AgentRunEntity::class,
        AgentStepEntity::class,
        BackupRecordEntity::class,
        ModelArtifactEntity::class,
        NotificationEntity::class,
        RoutineEntity::class,
        RoutineRunEntity::class,
        WorkspaceAssetEntity::class,
        NoteAttachmentEntity::class,
        ModelRuntimeProfileEntity::class,
        StudySessionEntity::class,
        StudyAttemptEntity::class,
        StudyFeedbackEntity::class,
        StudyPracticeItemEntity::class,
        StudyReviewEventEntity::class,
        com.halo.app.data.mcp.McpServerEntity::class,
        com.halo.app.data.mcp.McpToolEntity::class,
        com.halo.app.data.payload.PayloadBodyEntity::class,
        com.halo.app.data.payload.PayloadReferenceEntity::class,
        com.halo.app.data.attention.AttentionPendingEntity::class,
        AttentionReceiptEntity::class,
        ContinuationWatchEntity::class
    ],
    version = AppDatabase.VERSION,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun continuationWatchDao(): com.halo.app.data.watch.ContinuationWatchDao
    abstract fun attentionDao(): com.halo.app.data.attention.AttentionDao
    abstract fun payloadDao(): com.halo.app.data.payload.PayloadDao
    abstract fun notificationDao(): com.halo.app.data.notification.NotificationDao
    abstract fun routineDao(): com.halo.app.data.routine.RoutineDao
    abstract fun agentRunDao(): AgentRunDao
    abstract fun conversationDao(): ConversationDao
    abstract fun workspaceContextDao(): WorkspaceContextDao
    abstract fun memoryDao(): MemoryDao
    abstract fun personalMemoryDao(): com.halo.app.data.memory.PersonalMemoryDao
    abstract fun memoryRecordDao(): MemoryRecordDao
    abstract fun notebookDao(): NotebookDao
    abstract fun notebookDocumentDao(): NotebookDocumentDao
    abstract fun notebookOrganizationDao(): NotebookOrganizationDao
    abstract fun notebookCanvasDao(): NotebookCanvasDao
    abstract fun studyDao(): StudyDao
    abstract fun workspaceFileDao(): WorkspaceFileDao
    abstract fun workspaceAssetDao(): WorkspaceAssetDao
    abstract fun brainSnapshotDao(): BrainSnapshotDao
    abstract fun backupRecordDao(): BackupRecordDao
    abstract fun modelRuntimeProfileDao(): ModelRuntimeProfileDao
    abstract fun mcpDao(): com.halo.app.data.mcp.McpDao

    companion object {
        const val VERSION = 24

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS workspace_objects (
                        id TEXT NOT NULL PRIMARY KEY,
                        type TEXT NOT NULL,
                        title TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        source TEXT NOT NULL,
                        lifecycle TEXT NOT NULL,
                        inInbox INTEGER NOT NULL,
                        indexingState TEXT NOT NULL,
                        contentRef TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_workspace_objects_type ON workspace_objects(type)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_workspace_objects_lifecycle_updatedAt ON workspace_objects(lifecycle, updatedAt)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS notes (
                        objectId TEXT NOT NULL PRIMARY KEY,
                        body TEXT NOT NULL,
                        revision INTEGER NOT NULL,
                        FOREIGN KEY(objectId) REFERENCES workspace_objects(id)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS note_drafts (
                        objectId TEXT NOT NULL PRIMARY KEY,
                        body TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(objectId) REFERENCES workspace_objects(id)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS conversations (id TEXT NOT NULL PRIMARY KEY, createdAt INTEGER NOT NULL, active INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS conversation_turns (id TEXT NOT NULL PRIMARY KEY, conversationId TEXT NOT NULL, userText TEXT NOT NULL, assistantText TEXT NOT NULL, timestamp INTEGER NOT NULL, metadata TEXT NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS working_set (objectId TEXT NOT NULL PRIMARY KEY)")
                db.execSQL("CREATE TABLE IF NOT EXISTS object_provenance (objectId TEXT NOT NULL PRIMARY KEY, sourceId TEXT NOT NULL, revision TEXT NOT NULL, metadata TEXT NOT NULL)")
                db.execSQL("ALTER TABLE memories ADD COLUMN sourceObjectId TEXT")
                db.execSQL("ALTER TABLE memories ADD COLUMN sourceRevision INTEGER")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS workspace_files (
                        objectId TEXT NOT NULL PRIMARY KEY,
                        uri TEXT NOT NULL,
                        displayName TEXT NOT NULL,
                        mimeType TEXT NOT NULL,
                        sizeBytes INTEGER,
                        modifiedAt INTEGER,
                        ownedCopyPath TEXT,
                        permissionPersisted INTEGER NOT NULL,
                        FOREIGN KEY(objectId) REFERENCES workspace_objects(id)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("ALTER TABLE conversations ADD COLUMN title TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE conversations ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE conversations ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS memory_records (
                        id TEXT NOT NULL PRIMARY KEY,
                        content TEXT NOT NULL,
                        kind TEXT NOT NULL,
                        scope TEXT NOT NULL,
                        scopeId TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        sourceObjectId TEXT,
                        sourceRevision INTEGER,
                        confidence REAL,
                        status TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS memory_revisions (
                        id TEXT NOT NULL PRIMARY KEY,
                        memoryId TEXT NOT NULL,
                        previousContent TEXT NOT NULL,
                        newContent TEXT NOT NULL,
                        changedAt INTEGER NOT NULL,
                        reason TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                // Migrate existing memories into memory_records
                db.execSQL(
                    """
                    INSERT INTO memory_records (id, content, kind, scope, scopeId, createdAt, updatedAt, sourceObjectId, sourceRevision, confidence, status)
                    SELECT 'mem_' || id, content, 'FACT', 'GLOBAL', NULL, timestamp, timestamp, sourceObjectId, sourceRevision, 1.0, 'ACTIVE'
                    FROM memories
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE working_set ADD COLUMN addedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE working_set ADD COLUMN source TEXT NOT NULL DEFAULT 'USER'")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS workspace_relations (
                        id TEXT NOT NULL PRIMARY KEY,
                        fromId TEXT NOT NULL,
                        type TEXT NOT NULL,
                        toId TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS agent_runs (
                        id TEXT NOT NULL PRIMARY KEY,
                        request TEXT NOT NULL,
                        startedAt INTEGER NOT NULL,
                        completedAt INTEGER,
                        state TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS agent_steps (
                        id TEXT NOT NULL PRIMARY KEY,
                        runId TEXT NOT NULL,
                        stepType TEXT NOT NULL,
                        payload TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        FOREIGN KEY(runId) REFERENCES agent_runs(id)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workspace_files ADD COLUMN ownership TEXT NOT NULL DEFAULT 'EXTERNAL_REFERENCE'")
                db.execSQL("ALTER TABLE workspace_files ADD COLUMN availability TEXT NOT NULL DEFAULT 'AVAILABLE'")
                db.execSQL("ALTER TABLE workspace_files ADD COLUMN sha256 TEXT")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS backup_records (
                        id TEXT NOT NULL PRIMARY KEY,
                        archiveId TEXT NOT NULL,
                        destinationUri TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        profile TEXT NOT NULL,
                        sizeBytes INTEGER,
                        checksum TEXT,
                        state TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // v9 was shipped/exported in both legacy and already-expanded agent layouts.
                val expandedAgents = db.query("PRAGMA table_info(agent_runs)").use { cursor ->
                    var expanded = false
                    while (cursor.moveToNext()) if (cursor.getString(cursor.getColumnIndexOrThrow("name")) == "interactionId") expanded = true
                    expanded
                }
                if (!expandedAgents) {
                // Safely migrate agent_runs
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS agent_runs_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        interactionId TEXT NOT NULL,
                        request TEXT NOT NULL,
                        startedAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        completedAt INTEGER,
                        state TEXT NOT NULL,
                        failureReason TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO agent_runs_new (
                        id, interactionId, request, startedAt, updatedAt, completedAt, state, failureReason
                    )
                    SELECT 
                        id, 'legacy:' || id, request, startedAt, startedAt, completedAt, state, NULL
                    FROM agent_runs
                    """.trimIndent()
                )

                // Safely migrate agent_steps
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS agent_steps_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        runId TEXT NOT NULL,
                        interactionId TEXT NOT NULL,
                        ordinal INTEGER NOT NULL,
                        type TEXT NOT NULL,
                        schemaVersion INTEGER NOT NULL,
                        payloadJson TEXT NOT NULL,
                        idempotencyKey TEXT,
                        state TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        completedAt INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO agent_steps_new (
                        id, runId, interactionId, ordinal, type, schemaVersion, payloadJson, idempotencyKey, state, createdAt, completedAt
                    )
                    SELECT 
                        s.id, s.runId, 'legacy:' || s.runId,
                        (SELECT COUNT(*) FROM agent_steps prior WHERE prior.runId = s.runId AND
                            (prior.timestamp < s.timestamp OR (prior.timestamp = s.timestamp AND prior.id < s.id))),
                        s.stepType, 1, s.payload, NULL, 'UNKNOWN', s.timestamp, s.timestamp
                    FROM agent_steps s
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE agent_steps")
                db.execSQL("DROP TABLE agent_runs")
                db.execSQL("ALTER TABLE agent_runs_new RENAME TO agent_runs")
                db.execSQL("ALTER TABLE agent_steps_new RENAME TO agent_steps")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_agent_steps_runId ON agent_steps(runId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_agent_steps_interactionId ON agent_steps(interactionId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_agent_steps_idempotencyKey ON agent_steps(idempotencyKey)")

                }

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS model_artifacts (
                        id TEXT NOT NULL PRIMARY KEY,
                        source TEXT NOT NULL,
                        repoId TEXT,
                        revisionSha TEXT,
                        filename TEXT NOT NULL,
                        displayName TEXT NOT NULL,
                        expectedBytes INTEGER,
                        actualBytes INTEGER,
                        quantization TEXT,
                        architecture TEXT,
                        localPath TEXT,
                        validationState TEXT NOT NULL,
                        transferState TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        failureReason TEXT
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS notifications (
                        key TEXT NOT NULL PRIMARY KEY,
                        packageName TEXT NOT NULL,
                        appLabel TEXT,
                        title TEXT,
                        text TEXT,
                        category TEXT,
                        postedAt INTEGER NOT NULL,
                        removedAt INTEGER,
                        active INTEGER NOT NULL,
                        sensitivity TEXT NOT NULL DEFAULT 'NORMAL'
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notifications_packageName ON notifications(packageName)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notifications_postedAt ON notifications(postedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notifications_active ON notifications(active)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS routines (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        enabled INTEGER NOT NULL,
                        triggerJson TEXT NOT NULL,
                        conditionsJson TEXT NOT NULL,
                        actionsJson TEXT NOT NULL,
                        schemaVersion INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        lastRunAt INTEGER,
                        nextRunAt INTEGER
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS routine_runs (
                        id TEXT NOT NULL PRIMARY KEY,
                        routineId TEXT NOT NULL,
                        agentRunId TEXT NOT NULL,
                        triggerAt INTEGER NOT NULL,
                        state TEXT NOT NULL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS workspace_assets (
                        id TEXT NOT NULL PRIMARY KEY,
                        objectId TEXT NOT NULL,
                        type TEXT NOT NULL,
                        mimeType TEXT NOT NULL,
                        storageType TEXT NOT NULL,
                        pathOrUri TEXT NOT NULL,
                        permissionPersisted INTEGER NOT NULL,
                        displayName TEXT,
                        bytes INTEGER,
                        width INTEGER,
                        height INTEGER,
                        durationMs INTEGER,
                        createdAtEpochMs INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS model_runtime_profiles (
                        id TEXT NOT NULL PRIMARY KEY,
                        artifactId TEXT NOT NULL,
                        artifactFingerprint TEXT NOT NULL,
                        deviceFingerprint TEXT NOT NULL,
                        runtime TEXT NOT NULL,
                        backend TEXT NOT NULL,
                        contextSize INTEGER NOT NULL,
                        threads INTEGER NOT NULL,
                        gpuLayers INTEGER NOT NULL,
                        loadMs INTEGER NOT NULL,
                        ttftMs INTEGER NOT NULL,
                        promptTokensPerSecond REAL NOT NULL,
                        generationTokensPerSecond REAL NOT NULL,
                        peakEstimatedMemoryBytes INTEGER,
                        testedAtEpochMs INTEGER NOT NULL,
                        appVersion TEXT NOT NULL,
                        runtimeVersion TEXT NOT NULL,
                        compatibilityEpoch INTEGER NOT NULL,
                        isStable INTEGER NOT NULL,
                        failureReason TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_model_runtime_profiles_artifactId_deviceFingerprint ON model_runtime_profiles(artifactId, deviceFingerprint)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_model_runtime_profiles_profile_variant ON model_runtime_profiles(artifactId, artifactFingerprint, deviceFingerprint, runtime, backend, contextSize, threads, gpuLayers, runtimeVersion, compatibilityEpoch)")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS note_attachments (
                        id TEXT NOT NULL PRIMARY KEY,
                        noteId TEXT NOT NULL,
                        assetId TEXT NOT NULL,
                        position INTEGER NOT NULL,
                        caption TEXT,
                        createdAtEpochMs INTEGER NOT NULL,
                        FOREIGN KEY(noteId) REFERENCES notes(objectId)
                            ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(assetId) REFERENCES workspace_assets(id)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_note_attachments_noteId ON note_attachments(noteId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_note_attachments_assetId ON note_attachments(assetId)")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS note_blocks (
                        id TEXT NOT NULL PRIMARY KEY,
                        noteId TEXT NOT NULL,
                        position INTEGER NOT NULL,
                        type TEXT NOT NULL,
                        text TEXT NOT NULL,
                        checked INTEGER NOT NULL,
                        createdAtEpochMs INTEGER NOT NULL,
                        updatedAtEpochMs INTEGER NOT NULL,
                        FOREIGN KEY(noteId) REFERENCES notes(objectId)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_note_blocks_noteId ON note_blocks(noteId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_note_blocks_noteId_position ON note_blocks(noteId, position)")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS notebook_collections (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        sortOrder INTEGER NOT NULL,
                        createdAtEpochMs INTEGER NOT NULL,
                        updatedAtEpochMs INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_notebook_collections_name ON notebook_collections(name)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS notebook_collection_members (
                        collectionId TEXT NOT NULL,
                        objectId TEXT NOT NULL,
                        addedAtEpochMs INTEGER NOT NULL,
                        PRIMARY KEY(collectionId, objectId),
                        FOREIGN KEY(collectionId) REFERENCES notebook_collections(id)
                            ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(objectId) REFERENCES workspace_objects(id)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notebook_collection_members_collectionId ON notebook_collection_members(collectionId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notebook_collection_members_objectId ON notebook_collection_members(objectId)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS notebook_favorites (
                        objectId TEXT NOT NULL PRIMARY KEY,
                        addedAtEpochMs INTEGER NOT NULL,
                        FOREIGN KEY(objectId) REFERENCES workspace_objects(id)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS canvas_documents (
                        objectId TEXT NOT NULL PRIMARY KEY,
                        viewportX REAL NOT NULL,
                        viewportY REAL NOT NULL,
                        viewportScale REAL NOT NULL,
                        revision INTEGER NOT NULL,
                        FOREIGN KEY(objectId) REFERENCES workspace_objects(id)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS canvas_nodes (
                        id TEXT NOT NULL PRIMARY KEY,
                        canvasId TEXT NOT NULL,
                        type TEXT NOT NULL,
                        refObjectId TEXT,
                        text TEXT NOT NULL,
                        x REAL NOT NULL,
                        y REAL NOT NULL,
                        width REAL NOT NULL,
                        height REAL NOT NULL,
                        zIndex INTEGER NOT NULL,
                        createdAtEpochMs INTEGER NOT NULL,
                        updatedAtEpochMs INTEGER NOT NULL,
                        FOREIGN KEY(canvasId) REFERENCES canvas_documents(objectId)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_canvas_nodes_canvasId ON canvas_nodes(canvasId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_canvas_nodes_refObjectId ON canvas_nodes(refObjectId)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS canvas_edges (
                        id TEXT NOT NULL PRIMARY KEY,
                        canvasId TEXT NOT NULL,
                        fromNodeId TEXT NOT NULL,
                        toNodeId TEXT NOT NULL,
                        label TEXT,
                        createdAtEpochMs INTEGER NOT NULL,
                        FOREIGN KEY(canvasId) REFERENCES canvas_documents(objectId)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_canvas_edges_canvasId ON canvas_edges(canvasId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_canvas_edges_fromNodeId ON canvas_edges(fromNodeId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_canvas_edges_toNodeId ON canvas_edges(toNodeId)")
            }
        }

        /**
         * v16: canonical document structure for the editor (block indent/reference/language, note
         * schema version, structured recovery drafts) plus the durable Study tables.
         */
        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE note_blocks ADD COLUMN indent INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE note_blocks ADD COLUMN refObjectId TEXT")
                db.execSQL("ALTER TABLE note_blocks ADD COLUMN language TEXT")
                db.execSQL("ALTER TABLE notes ADD COLUMN schemaVersion INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE note_drafts ADD COLUMN baseRevision INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE note_drafts ADD COLUMN sequence INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE note_drafts ADD COLUMN structureJson TEXT")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS study_sessions (
                        id TEXT NOT NULL,
                        goal TEXT,
                        assignmentLanguage TEXT,
                        explanationLanguage TEXT,
                        strongestLanguage TEXT,
                        sourceObjectIdsJson TEXT NOT NULL,
                        sourceRevision INTEGER,
                        conversationId TEXT,
                        runId TEXT,
                        interactionId TEXT,
                        tutorStage TEXT NOT NULL,
                        lifecycle TEXT NOT NULL,
                        currentExerciseId TEXT,
                        solutionRevealed INTEGER NOT NULL,
                        completedAtEpochMs INTEGER,
                        schemaVersion INTEGER NOT NULL,
                        createdAtEpochMs INTEGER NOT NULL,
                        updatedAtEpochMs INTEGER NOT NULL,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_study_sessions_lifecycle ON study_sessions(lifecycle)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_study_sessions_updatedAtEpochMs ON study_sessions(updatedAtEpochMs)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS study_attempts (
                        id TEXT NOT NULL,
                        sessionId TEXT NOT NULL,
                        exerciseId TEXT NOT NULL,
                        response TEXT NOT NULL,
                        stage TEXT NOT NULL,
                        helpRevealedJson TEXT NOT NULL,
                        submissionKey TEXT NOT NULL,
                        submittedAtEpochMs INTEGER NOT NULL,
                        PRIMARY KEY(id),
                        FOREIGN KEY(sessionId) REFERENCES study_sessions(id)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_study_attempts_sessionId ON study_attempts(sessionId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_study_attempts_exerciseId ON study_attempts(exerciseId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_study_attempts_submissionKey ON study_attempts(submissionKey)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS study_feedback (
                        id TEXT NOT NULL,
                        sessionId TEXT NOT NULL,
                        attemptId TEXT NOT NULL,
                        generationId TEXT NOT NULL,
                        verdict TEXT NOT NULL,
                        summary TEXT NOT NULL,
                        nextStep TEXT NOT NULL,
                        uncertainty TEXT,
                        citationsJson TEXT NOT NULL,
                        createdAtEpochMs INTEGER NOT NULL,
                        PRIMARY KEY(id),
                        FOREIGN KEY(attemptId) REFERENCES study_attempts(id)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_study_feedback_sessionId ON study_feedback(sessionId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_study_feedback_attemptId ON study_feedback(attemptId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_study_feedback_generationId ON study_feedback(generationId)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS study_practice_items (
                        id TEXT NOT NULL,
                        sessionId TEXT NOT NULL,
                        question TEXT NOT NULL,
                        answer TEXT NOT NULL,
                        sourceObjectId TEXT,
                        sourceRevision INTEGER,
                        accepted INTEGER NOT NULL,
                        intervalIndex INTEGER NOT NULL,
                        dueAtEpochMs INTEGER NOT NULL,
                        reviewCount INTEGER NOT NULL,
                        createdAtEpochMs INTEGER NOT NULL,
                        lastReviewedAtEpochMs INTEGER,
                        PRIMARY KEY(id),
                        FOREIGN KEY(sessionId) REFERENCES study_sessions(id)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_study_practice_items_sessionId ON study_practice_items(sessionId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_study_practice_items_sourceObjectId ON study_practice_items(sourceObjectId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_study_practice_items_dueAtEpochMs ON study_practice_items(dueAtEpochMs)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS study_review_events (
                        id TEXT NOT NULL,
                        itemId TEXT NOT NULL,
                        sessionId TEXT NOT NULL,
                        known INTEGER NOT NULL,
                        previousIntervalIndex INTEGER NOT NULL,
                        nextIntervalIndex INTEGER NOT NULL,
                        nextDueAtEpochMs INTEGER NOT NULL,
                        reviewedAtEpochMs INTEGER NOT NULL,
                        PRIMARY KEY(id),
                        FOREIGN KEY(itemId) REFERENCES study_practice_items(id)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_study_review_events_itemId ON study_review_events(itemId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_study_review_events_reviewedAtEpochMs ON study_review_events(reviewedAtEpochMs)")
            }
        }

        /**
         * v17: practice cards carry their ladder streaks, derived mastery flag and a user-edit flag
         * so generated content can never overwrite learner edits silently.
         */
        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE study_practice_items ADD COLUMN knownStreak INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE study_practice_items ADD COLUMN wrongStreak INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE study_practice_items ADD COLUMN mastered INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE study_practice_items ADD COLUMN userEdited INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE model_runtime_profiles ADD COLUMN textGenerationProven INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE model_runtime_profiles ADD COLUMN nativeToolCallingProven INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE model_runtime_profiles ADD COLUMN haloPromptToolLoopProven INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE model_runtime_profiles ADD COLUMN agenticContinuationProven INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * v19: MCP capability fabric. Configuration and discovered tool metadata only; credentials
         * live in SecretStore and live platform objects are never persisted.
         */
        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS mcp_servers (
                        id TEXT NOT NULL PRIMARY KEY,
                        displayName TEXT NOT NULL,
                        endpoint TEXT NOT NULL,
                        enabled INTEGER NOT NULL,
                        authKind TEXT NOT NULL,
                        credentialRef TEXT,
                        trust TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        protocolVersion TEXT,
                        lastConnectedAt INTEGER,
                        lastError TEXT
                    )
                    """
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS mcp_tools (
                        id TEXT NOT NULL PRIMARY KEY,
                        serverId TEXT NOT NULL,
                        remoteName TEXT NOT NULL,
                        displayName TEXT NOT NULL,
                        description TEXT NOT NULL,
                        inputSchemaJson TEXT NOT NULL,
                        risk TEXT NOT NULL,
                        enabled INTEGER NOT NULL,
                        lastRefreshedAt INTEGER NOT NULL
                    )
                    """
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_mcp_tools_serverId ON mcp_tools (serverId)")
            }
        }

        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS payload_bodies (sha256 TEXT NOT NULL PRIMARY KEY, byteCount INTEGER NOT NULL, createdAtMs INTEGER NOT NULL, unreferencedAtMs INTEGER)")
                db.execSQL("CREATE TABLE IF NOT EXISTS payload_refs (id TEXT NOT NULL, sha256 TEXT NOT NULL, ownerKind TEXT NOT NULL, ownerId TEXT NOT NULL, mediaType TEXT NOT NULL, classification TEXT NOT NULL, trust TEXT NOT NULL, provenance TEXT NOT NULL, remoteAllowed INTEGER NOT NULL, createdAtMs INTEGER NOT NULL, PRIMARY KEY(id, ownerKind, ownerId), FOREIGN KEY(sha256) REFERENCES payload_bodies(sha256) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_payload_refs_ownerKind_ownerId ON payload_refs(ownerKind, ownerId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_payload_refs_sha256 ON payload_refs(sha256)")
                for (column in listOf("parentRunId", "rootRunId", "delegatedRole", "pinnedProviderId", "pinnedModelId")) {
                    db.execSQL("ALTER TABLE agent_runs ADD COLUMN $column TEXT")
                }
                db.execSQL("ALTER TABLE agent_runs ADD COLUMN delegationDepth INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE agent_runs ADD COLUMN pinnedTargetLocal INTEGER")
                db.execSQL("ALTER TABLE agent_runs ADD COLUMN delegatedInputRefs TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_agent_runs_parentRunId ON agent_runs(parentRunId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_agent_runs_rootRunId ON agent_runs(rootRunId)")
            }
        }

        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE routines ADD COLUMN deliveryJson TEXT NOT NULL DEFAULT '{\"type\":\"silent\"}'")
                db.execSQL("ALTER TABLE routines ADD COLUMN grantDigest TEXT")
                db.execSQL("ALTER TABLE routines ADD COLUMN minimumIntervalSeconds INTEGER NOT NULL DEFAULT 0")
                // Existing definitions remain silent and require a reviewed save to mint a grant.
                db.execSQL("UPDATE routines SET enabled = 0")
                db.execSQL("ALTER TABLE routine_runs ADD COLUMN source TEXT NOT NULL DEFAULT 'SCHEDULED'")
                for(c in listOf("signalId", "rootOccurrenceId", "causedByOccurrenceId", "reason", "deliveryOutcome")) db.execSQL("ALTER TABLE routine_runs ADD COLUMN $c TEXT")
                db.execSQL("ALTER TABLE routine_runs ADD COLUMN actionsCompleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE routine_runs ADD COLUMN actionsTotal INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE routine_runs ADD COLUMN finishedAt INTEGER")
                db.execSQL("ALTER TABLE routine_runs ADD COLUMN coalescedCount INTEGER NOT NULL DEFAULT 1")
                db.execSQL("CREATE TABLE IF NOT EXISTS attention_pending (id TEXT NOT NULL PRIMARY KEY, dedupKey TEXT, json TEXT NOT NULL, createdAt INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_attention_pending_dedupKey ON attention_pending(dedupKey)")
                db.execSQL("CREATE TABLE IF NOT EXISTS attention_receipts (id TEXT NOT NULL PRIMARY KEY, outcome TEXT NOT NULL, channel TEXT, reason TEXT, at INTEGER NOT NULL, dedupKey TEXT, disruptive INTEGER NOT NULL)")
            }
        }

        val MIGRATION_21_22 = object : Migration(21,22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE memory_records ADD COLUMN origin TEXT NOT NULL DEFAULT 'IMPORT'")
                db.execSQL("ALTER TABLE memory_records ADD COLUMN evidenceJson TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE memory_records ADD COLUMN sourceEpisodeId TEXT")
                db.execSQL("ALTER TABLE memory_records ADD COLUMN evidenceCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE memory_records ADD COLUMN lastConfirmedAt INTEGER")
                db.execSQL("ALTER TABLE memory_records ADD COLUMN usageCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE memory_records ADD COLUMN lastUsedAt INTEGER")
                db.execSQL("ALTER TABLE memory_records ADD COLUMN validUntil INTEGER")
                db.execSQL("ALTER TABLE memory_records ADD COLUMN fingerprint TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE memory_records ADD COLUMN userConfirmed INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE memory_records SET confidence = NULL")
                db.execSQL("CREATE TABLE IF NOT EXISTS personal_episodes (id TEXT NOT NULL PRIMARY KEY, runId TEXT NOT NULL, json TEXT NOT NULL, endedAt INTEGER NOT NULL, importance INTEGER NOT NULL, usageCount INTEGER NOT NULL, lastReferencedAt INTEGER)")
                db.execSQL("CREATE TABLE IF NOT EXISTS reflection_states (id TEXT NOT NULL PRIMARY KEY, policyVersion INTEGER NOT NULL, status TEXT NOT NULL, mode TEXT NOT NULL, updatedAt INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS personal_suggestions (id TEXT NOT NULL PRIMARY KEY, fingerprint TEXT NOT NULL, kind TEXT NOT NULL, json TEXT NOT NULL, state TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, evidenceCount INTEGER NOT NULL, dismissedAt INTEGER, neverAgain INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_personal_suggestions_fingerprint ON personal_suggestions(fingerprint)")
            }
        }

        val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS continuation_watches (
                        id TEXT NOT NULL PRIMARY KEY,
                        runId TEXT,
                        interactionId TEXT,
                        conditionType TEXT NOT NULL,
                        conditionJson TEXT NOT NULL,
                        behaviorType TEXT NOT NULL,
                        behaviorJson TEXT NOT NULL,
                        presentationText TEXT,
                        status TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        expiresAt INTEGER,
                        deduplicationKey TEXT,
                        matchMode TEXT NOT NULL,
                        matchedAt INTEGER,
                        claimState TEXT NOT NULL DEFAULT 'UNCLAIMED'
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_continuation_watches_runId ON continuation_watches(runId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_continuation_watches_status ON continuation_watches(status)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_continuation_watches_deduplicationKey ON continuation_watches(deduplicationKey)")
            }
        }

        val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Native model-default context can be unobservable; never persist a guessed limit.
                db.execSQL("ALTER TABLE model_runtime_profiles RENAME TO model_runtime_profiles_legacy")
                db.execSQL("CREATE TABLE IF NOT EXISTS `model_runtime_profiles` (`id` TEXT NOT NULL, `artifactId` TEXT NOT NULL, `artifactFingerprint` TEXT NOT NULL, `deviceFingerprint` TEXT NOT NULL, `runtime` TEXT NOT NULL, `backend` TEXT NOT NULL, `contextSize` INTEGER, `threads` INTEGER NOT NULL, `gpuLayers` INTEGER NOT NULL, `loadMs` INTEGER NOT NULL, `ttftMs` INTEGER NOT NULL, `promptTokensPerSecond` REAL NOT NULL, `generationTokensPerSecond` REAL NOT NULL, `peakEstimatedMemoryBytes` INTEGER, `textGenerationProven` INTEGER NOT NULL, `nativeToolCallingProven` INTEGER NOT NULL, `haloPromptToolLoopProven` INTEGER NOT NULL, `agenticContinuationProven` INTEGER NOT NULL, `testedAtEpochMs` INTEGER NOT NULL, `appVersion` TEXT NOT NULL, `runtimeVersion` TEXT NOT NULL, `compatibilityEpoch` INTEGER NOT NULL, `isStable` INTEGER NOT NULL, `failureReason` TEXT, PRIMARY KEY(`id`))")
                db.execSQL("INSERT INTO model_runtime_profiles SELECT * FROM model_runtime_profiles_legacy")
                db.execSQL("DROP TABLE model_runtime_profiles_legacy")
                db.execSQL("CREATE INDEX index_model_runtime_profiles_artifactId_deviceFingerprint ON model_runtime_profiles(artifactId, deviceFingerprint)")
                db.execSQL("CREATE UNIQUE INDEX index_model_runtime_profiles_profile_variant ON model_runtime_profiles(artifactId, artifactFingerprint, deviceFingerprint, runtime, backend, contextSize, threads, gpuLayers, runtimeVersion, compatibilityEpoch, isStable)")
            }
        }

        val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24)

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "halo.db")
                .addMigrations(*ALL_MIGRATIONS)
                .build()
    }
}
