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
import com.halo.app.data.notification.NotificationEntity
import com.halo.app.data.notebook.NoteEntity
import com.halo.app.data.notebook.NoteDraftEntity
import com.halo.app.data.notebook.WorkspaceObjectEntity
import com.halo.app.data.routine.RoutineEntity
import com.halo.app.data.routine.RoutineRunEntity
import com.halo.app.data.workspace.WorkspaceAssetEntity
import com.halo.app.data.workspace.WorkspaceFileDao
import com.halo.app.data.workspace.WorkspaceFileEntity
import com.halo.app.data.workspace.WorkspaceRelationEntity
import com.halo.app.backup.BackupRecordDao
import com.halo.app.backup.BackupRecordEntity
import com.halo.app.backup.BrainSnapshotDao
import com.halo.app.data.notebook.NotebookDao

@Database(
    entities = [
        Memory::class,
        WorkspaceObjectEntity::class,
        NoteEntity::class,
        NoteDraftEntity::class,
        PersistedTurn::class,
        PersistedConversation::class,
        WorkingSetEntry::class,
        ObjectProvenance::class,
        WorkspaceFileEntity::class,
        MemoryRecordEntity::class,
        MemoryRevisionEntity::class,
        WorkspaceRelationEntity::class,
        AgentRunEntity::class,
        AgentStepEntity::class,
        BackupRecordEntity::class,
        ModelArtifactEntity::class,
        NotificationEntity::class,
        RoutineEntity::class,
        RoutineRunEntity::class,
        WorkspaceAssetEntity::class
    ],
    version = 10,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun agentRunDao(): AgentRunDao
    abstract fun conversationDao(): ConversationDao
    abstract fun workspaceContextDao(): WorkspaceContextDao
    abstract fun memoryDao(): MemoryDao
    abstract fun memoryRecordDao(): MemoryRecordDao
    abstract fun notebookDao(): NotebookDao
    abstract fun workspaceFileDao(): WorkspaceFileDao
    abstract fun brainSnapshotDao(): BrainSnapshotDao
    abstract fun backupRecordDao(): BackupRecordDao

    companion object {
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
                        id, id, request, startedAt, startedAt, completedAt, state, NULL
                    FROM agent_runs
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE agent_runs")
                db.execSQL("ALTER TABLE agent_runs_new RENAME TO agent_runs")

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
                        id, runId, runId, 0, stepType, 1, payload, NULL, 'COMPLETED', timestamp, timestamp
                    FROM agent_steps
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE agent_steps")
                db.execSQL("ALTER TABLE agent_steps_new RENAME TO agent_steps")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_agent_steps_runId ON agent_steps(runId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_agent_steps_interactionId ON agent_steps(interactionId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_agent_steps_idempotencyKey ON agent_steps(idempotencyKey)")

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

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "halo.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                .fallbackToDestructiveMigration(true)
                .build()
    }
}
