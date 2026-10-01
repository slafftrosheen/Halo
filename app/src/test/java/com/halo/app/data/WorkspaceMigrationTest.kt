package com.halo.app.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WorkspaceMigrationTest {
    private lateinit var context: Context
    private val databaseName = "workspace-migration-test.db"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun testFullMigrationFromVersionFourToTenPreservesData() = runTest {
        // Create database at version 4
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(object : SupportSQLiteOpenHelper.Callback(4) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
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
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_workspace_objects_type ON workspace_objects(type)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_workspace_objects_lifecycle_updatedAt ON workspace_objects(lifecycle, updatedAt)")
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS notes (
                                objectId TEXT NOT NULL PRIMARY KEY,
                                body TEXT NOT NULL,
                                revision INTEGER NOT NULL,
                                FOREIGN KEY(objectId) REFERENCES workspace_objects(id) ON DELETE CASCADE
                            )
                            """.trimIndent()
                        )
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS note_drafts (
                                objectId TEXT NOT NULL PRIMARY KEY,
                                body TEXT NOT NULL,
                                updatedAt INTEGER NOT NULL,
                                FOREIGN KEY(objectId) REFERENCES workspace_objects(id) ON DELETE CASCADE
                            )
                            """.trimIndent()
                        )
                        db.execSQL("CREATE TABLE IF NOT EXISTS memories (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, content TEXT NOT NULL, timestamp INTEGER NOT NULL, sourceObjectId TEXT, sourceRevision INTEGER)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS conversations (id TEXT NOT NULL PRIMARY KEY, createdAt INTEGER NOT NULL, active INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS conversation_turns (id TEXT NOT NULL PRIMARY KEY, conversationId TEXT NOT NULL, userText TEXT NOT NULL, assistantText TEXT NOT NULL, timestamp INTEGER NOT NULL, metadata TEXT NOT NULL)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS working_set (objectId TEXT NOT NULL PRIMARY KEY)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS object_provenance (objectId TEXT NOT NULL PRIMARY KEY, sourceId TEXT NOT NULL, revision TEXT NOT NULL, metadata TEXT NOT NULL)")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )

        // Insert initial data
        helper.writableDatabase.execSQL("INSERT INTO memories (content, timestamp, sourceObjectId, sourceRevision) VALUES ('Important fact', 1000, 'note-1', 1)")
        helper.writableDatabase.execSQL("INSERT INTO workspace_objects VALUES ('note-1', 'NOTE', 'Sample Note', 1000, 1000, 'USER_CREATED', 'ACTIVE', 1, 'READY', 'note:note-1')")
        helper.writableDatabase.execSQL("INSERT INTO notes VALUES ('note-1', 'Note content', 1)")
        helper.writableDatabase.execSQL("INSERT INTO working_set VALUES ('note-1')")
        helper.writableDatabase.execSQL("INSERT INTO conversations VALUES ('conv-1', 1000, 1)")
        // Agent runs table doesn't exist in v4, so we cannot insert 'run-8' here directly if the table wasn't created in v4 callback.
        // Wait, did they test AgentRun insertion?
        helper.close()

        // Migrate to version 10 using AppDatabase
        val db = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()

        // Verify preserved data
        val note = db.notebookDao().getNote("note-1")
        assertNotNull(note)
        assertEquals("Sample Note", note?.title)

        val memorySnapshot = db.query("SELECT * FROM memory_records", null).use { cursor ->
            val list = mutableListOf<String>()
            while (cursor.moveToNext()) {
                list.add(cursor.getString(cursor.getColumnIndexOrThrow("content")))
            }
            list
        }
        assertEquals(1, memorySnapshot.size)
        assertEquals("Important fact", memorySnapshot.first())

        db.close()
    }
}
