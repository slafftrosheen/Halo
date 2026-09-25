package com.halo.app.data.notebook

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomNotebookRepositoryTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: RoomNotebookRepository
    private var persistentDatabaseName: String? = null
    private var timestamp = 1_000L
    private var nextId = 0

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomNotebookRepository(
            dao = database.notebookDao(),
            now = { timestamp++ },
            newId = { "note-${nextId++}" }
        )
    }

    @After
    fun tearDown() {
        database.close()
        persistentDatabaseName?.let(context::deleteDatabase)
    }

    @Test
    fun createSaveAndLiteralSearchStayConsistent() = runTest {
        val note = repository.createNote("# Halo UI ideas\nUse 100% soft pixel cells")

        assertEquals("Halo UI ideas", note.title)
        assertTrue(note.inInbox)
        assertEquals(1, repository.observeNotes("100%").first().size)
        assertTrue(repository.observeNotes("not present").first().isEmpty())

        assertTrue(repository.saveNote(note.id, "New title\nChanged body"))
        val saved = requireNotNull(repository.getNote(note.id))
        assertEquals("New title", saved.title)
        assertEquals(2, saved.revision)
        assertEquals("Changed body", saved.body.lineSequence().drop(1).first())
    }

    @Test
    fun archiveTrashRestoreAndPermanentDeleteUseOneLifecycle() = runTest {
        val note = repository.createNote("Lifecycle note")

        assertTrue(repository.setLifecycle(note.id, WorkspaceLifecycle.ARCHIVED))
        assertTrue(repository.observeNotes(lifecycle = WorkspaceLifecycle.ACTIVE).first().isEmpty())
        assertEquals(note.id, repository.observeNotes(lifecycle = WorkspaceLifecycle.ARCHIVED).first().single().id)

        assertTrue(repository.setLifecycle(note.id, WorkspaceLifecycle.TRASHED))
        assertEquals(note.id, repository.observeNotes(lifecycle = WorkspaceLifecycle.TRASHED).first().single().id)
        assertTrue(repository.permanentlyDelete(note.id))
        assertNull(repository.getNote(note.id))
        assertFalse(repository.permanentlyDelete(note.id))
    }

    @Test
    fun inboxIsIndependentFromArchiveAndCanBeCleared() = runTest {
        val note = repository.createNote("Inbox note")

        assertEquals(note.id, repository.observeNotes(inboxOnly = true).first().single().id)
        assertTrue(repository.removeFromInbox(note.id))
        assertTrue(repository.observeNotes(inboxOnly = true).first().isEmpty())
        assertEquals(note.id, repository.observeNotes().first().single().id)
    }

    @Test
    fun draftSurvivesUntilCommitAndIsDeletedWithItsNote() = runTest {
        val note = repository.createNote("Committed")

        repository.saveDraft(note.id, "Recovered draft")
        assertEquals("Recovered draft", repository.getDraft(note.id)?.body)
        assertEquals("Committed", repository.getNote(note.id)?.body)

        assertTrue(repository.saveNote(note.id, "Recovered draft"))
        assertNull(repository.getDraft(note.id))
        assertEquals("Recovered draft", repository.getNote(note.id)?.body)

        repository.saveDraft(note.id, "Delete me")
        repository.setLifecycle(note.id, WorkspaceLifecycle.TRASHED)
        assertTrue(repository.permanentlyDelete(note.id))
        assertNull(repository.getDraft(note.id))
    }

    @Test
    fun migrationFromVersionOnePreservesMemoryAndAddsNotebook() = runTest {
        database.close()
        val databaseName = "notebook-migration-test.db"
        persistentDatabaseName = databaseName
        context.deleteDatabase(databaseName)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS memories " +
                                "(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "content TEXT NOT NULL, timestamp INTEGER NOT NULL)"
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        helper.writableDatabase.execSQL(
            "INSERT INTO memories (content, timestamp) VALUES ('preserved', 42)"
        )
        helper.close()

        database = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_7_8,
                AppDatabase.MIGRATION_8_9,
                AppDatabase.MIGRATION_9_10,
                AppDatabase.MIGRATION_10_11
            )
            .allowMainThreadQueries()
            .build()
        repository = RoomNotebookRepository(database.notebookDao(), newId = { "migrated-note" })

        assertEquals("preserved", database.memoryDao().getAllMemories().first().single().content)
        assertEquals("migrated-note", repository.createNote("After migration").id)
    }
}
