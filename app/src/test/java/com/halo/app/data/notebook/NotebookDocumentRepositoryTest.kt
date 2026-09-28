package com.halo.app.data.notebook

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
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

/**
 * The document repository is the only writer of note content, so these tests pin the promises the
 * editor, the tools and the recovery path all rely on: a revision compare-and-swap, a derived text
 * projection that cannot drift, and drafts that only the commit covering them may acknowledge.
 */
@RunWith(RobolectricTestRunner::class)
class NotebookDocumentRepositoryTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var notes: RoomNotebookRepository
    private lateinit var documents: NotebookDocumentRepository
    private var timestamp = 1_000L
    private var nextId = 0

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        documents = NotebookDocumentRepository(
            dao = database.notebookDocumentDao(),
            database = database,
            now = {
                timestamp += 10
                timestamp
            },
            newId = { "block-${nextId++}" },
        )
        notes = RoomNotebookRepository(
            dao = database.notebookDao(),
            now = { timestamp++ },
            newId = { "note-${nextId++}" },
            documents = documents,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun block(
        id: String,
        type: NoteBlockType = NoteBlockType.PARAGRAPH,
        text: String = "",
        checked: Boolean = false,
        refObjectId: String? = null,
    ) = NoteBlock(
        id = id,
        position = 0,
        type = type,
        text = text,
        checked = checked,
        refObjectId = refObjectId,
    )

    private suspend fun createNote(body: String): NotebookNote = notes.createNote(body)

    private suspend fun openDocument(note: NotebookNote): NotebookDocument {
        documents.ensureDocument(note.id, note.body)
        return requireNotNull(documents.load(note.id))
    }

    @Test
    fun `a stale commit is refused instead of overwriting`() = runTest {
        val note = createNote("# Title\nFirst")
        val opened = openDocument(note)

        val first = documents.commit(
            noteId = note.id,
            expectedRevision = opened.revision,
            blocks = listOf(block("b1", NoteBlockType.PARAGRAPH, "First")),
        )
        assertTrue(first is CommitOutcome.Committed)
        val committedRevision = (first as CommitOutcome.Committed).revision

        val stale = documents.commit(
            noteId = note.id,
            expectedRevision = opened.revision,
            blocks = listOf(block("b1", NoteBlockType.PARAGRAPH, "Overwritten by a stale writer")),
        )
        assertEquals(CommitOutcome.Conflict(committedRevision), stale)
        assertEquals(
            "First",
            requireNotNull(documents.load(note.id)).blocks.single().text,
        )
    }

    @Test
    fun `a trashed note rejects commits`() = runTest {
        val note = createNote("# Title\nBody")
        val opened = openDocument(note)
        assertTrue(notes.setLifecycle(note.id, WorkspaceLifecycle.TRASHED))

        val outcome = documents.commit(note.id, opened.revision, listOf(block("b1", text = "Nope")))

        assertTrue(outcome is CommitOutcome.Rejected)
        assertEquals(DocumentRejection.TRASHED, (outcome as CommitOutcome.Rejected).reason)
    }

    @Test
    fun `the stored body is exactly the committed structure`() = runTest {
        val note = createNote("body")
        val opened = openDocument(note)
        val blocks = listOf(
            block("b1", NoteBlockType.HEADING, "Design notes"),
            block("b2", NoteBlockType.CHECKLIST, "Ship the editor", checked = true),
            block("b3", NoteBlockType.REFERENCE, "Halo spec", refObjectId = "note-spec"),
        )

        val outcome = documents.commit(note.id, opened.revision, blocks)
        assertTrue(outcome is CommitOutcome.Committed)

        val stored = requireNotNull(documents.load(note.id))
        assertEquals(
            NotebookDocumentCodec.serialize(blocks),
            requireNotNull(notes.getNote(note.id)).body,
        )
        assertEquals(NotebookDocumentCodec.serialize(blocks), NotebookDocumentCodec.serialize(stored.blocks))
        assertEquals(listOf("b1", "b2", "b3"), stored.blocks.map(NoteBlock::id))
        // Positions are always re-sequenced, whatever the caller passed in.
        assertEquals(listOf(0, 1, 2), stored.blocks.map(NoteBlock::position))
        assertEquals("Design notes", stored.title)
        assertTrue(stored.titleIsDerived)
    }

    @Test
    fun `apply reads the stored document inside the commit`() = runTest {
        val note = createNote("# Title\nkept")
        val opened = openDocument(note)
        val stored = requireNotNull(documents.load(note.id))
        val kept = stored.blocks.first { it.text == "kept" }

        val outcome = documents.apply(
            noteId = note.id,
            expectedRevision = stored.revision,
            command = DocumentCommand.InsertBlock(
                afterBlockId = kept.id,
                type = NoteBlockType.PARAGRAPH,
                text = "added",
            ),
            title = "A user title",
        )

        assertTrue(outcome is CommitOutcome.Committed)
        val after = requireNotNull(documents.load(note.id))
        assertEquals(listOf("Title", "kept", "added"), after.blocks.map(NoteBlock::text))
        // An explicit title wins and stops following the body.
        assertEquals("A user title", after.title)
        assertFalse(after.titleIsDerived)
    }

    @Test
    fun `an automatic title keeps following the body`() = runTest {
        val note = createNote("# First idea\nbody")
        val opened = openDocument(note)
        assertEquals("First idea", opened.title)

        documents.commit(
            note.id,
            opened.revision,
            listOf(block("b1", NoteBlockType.HEADING, "Second idea")),
        )

        assertEquals("Second idea", requireNotNull(documents.load(note.id)).title)
    }

    @Test
    fun `a recovery draft newer than the commit survives its acknowledgement`() = runTest {
        val note = createNote("body")
        val opened = openDocument(note)

        documents.saveRecovery(note.id, listOf(block("b1", text = "draft one")), opened.revision, sequence = 1)
        documents.saveRecovery(note.id, listOf(block("b2", text = "draft two")), opened.revision, sequence = 2)

        // The commit only covers the edit sequence it was written for.
        val outcome = documents.commit(note.id, opened.revision, listOf(block("b1", text = "committed")), acknowledgeDraftSequence = 1)
        assertTrue(outcome is CommitOutcome.Committed)
        val survivor = requireNotNull(documents.loadRecovery(note.id))
        assertEquals(2, survivor.sequence)
        assertEquals(listOf("draft two"), survivor.blocks.map(NoteBlock::text))

        documents.commit(note.id, opened.revision + 1, listOf(block("b2", text = "committed two")), acknowledgeDraftSequence = 2)
        assertNull(documents.loadRecovery(note.id))
    }

    @Test
    fun `a structural recovery draft round-trips block identity`() = runTest {
        val note = createNote("body")
        val opened = openDocument(note)
        val blocks = listOf(
            block("b1", NoteBlockType.CHECKLIST, "with a checkbox", checked = true),
            block("b2", NoteBlockType.REFERENCE, "target", refObjectId = "note-7"),
            block("b3", NoteBlockType.CODE, "val x = 1"),
        )

        documents.saveRecovery(note.id, blocks, baseRevision = opened.revision, sequence = 3)

        val draft = requireNotNull(documents.loadRecovery(note.id))
        assertEquals(opened.revision, draft.baseRevision)
        assertEquals(blocks.map(NoteBlock::id), draft.blocks.map(NoteBlock::id))
        assertEquals(blocks.map(NoteBlock::type), draft.blocks.map(NoteBlock::type))
        assertEquals(blocks.map(NoteBlock::text), draft.blocks.map(NoteBlock::text))
        assertEquals(listOf(true, false, false), draft.blocks.map(NoteBlock::checked))
        assertEquals(listOf(null, "note-7", null), draft.blocks.map(NoteBlock::refObjectId))
        assertTrue(draft.supersedes(opened.revision, opened.updatedAt))
        assertFalse(draft.supersedes(opened.revision + 1, opened.updatedAt))
    }

    @Test
    fun `opening a legacy body parses it once without claiming an edit`() = runTest {
        val note = createNote("# Title\n- [ ] task")

        val first = requireNotNull(documents.ensureDocument(note.id, note.body))
        val second = requireNotNull(documents.ensureDocument(note.id, note.body))

        assertEquals(note.revision.toLong(), first.revision)
        assertEquals(first.revision, second.revision)
        assertEquals(first.blocks, second.blocks)
        assertEquals(NoteBlockType.CHECKLIST, first.blocks.last().type)
        assertFalse(first.blocks.last().checked)
    }
}
