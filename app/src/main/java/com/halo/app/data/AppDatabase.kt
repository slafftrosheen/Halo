package com.halo.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.halo.app.data.notebook.NoteEntity
import com.halo.app.data.notebook.NoteDraftEntity
import com.halo.app.data.notebook.WorkspaceObjectEntity

@Database(
    entities = [Memory::class, WorkspaceObjectEntity::class, NoteEntity::class, NoteDraftEntity::class, PersistedTurn::class, PersistedConversation::class, WorkingSetEntry::class, ObjectProvenance::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun workspaceContextDao(): WorkspaceContextDao
    abstract fun memoryDao(): MemoryDao
    abstract fun notebookDao(): com.halo.app.data.notebook.NotebookDao

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

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "halo.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
    }
}
