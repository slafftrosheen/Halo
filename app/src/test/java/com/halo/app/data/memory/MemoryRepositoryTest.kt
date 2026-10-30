package com.halo.app.data.memory

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import com.halo.app.data.notebook.WorkspaceObjectId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MemoryRepositoryTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var memoryRepo: RoomMemoryRepository
    private lateinit var memoryContextSource: BoundedMemoryContextSource

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        memoryRepo = RoomMemoryRepository(db)
        memoryContextSource = BoundedMemoryContextSource(memoryRepo)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun promoteCandidateCreatesMemoryRecordAndAllowsRevision() = runTest {
        val source = com.halo.app.data.notebook.RoomNotebookRepository(db.notebookDao()).createNote("Prefer local inference when available")
        val candidate = MemoryCandidate(
            content = "Prefer local inference when available",
            sourceObjectId = WorkspaceObjectId(source.id),
            kind = MemoryKind.PREFERENCE,
            scope = MemoryScope.GLOBAL
        )

        val memory = memoryRepo.promoteCandidate(candidate)
        assertNotNull(memory.id)
        assertEquals("Prefer local inference when available", memory.content)
        assertEquals(MemoryKind.PREFERENCE, memory.kind)

        memoryRepo.correctMemory(memory.id, "Prefer local inference always", reason = "User edit")
        val updated = memoryRepo.getMemory(memory.id)
        assertNotNull(updated)
        assertEquals("Prefer local inference always", updated?.content)

        val revisions = memoryRepo.observeRevisions(memory.id).first()
        assertEquals(1, revisions.size)
        assertEquals("Prefer local inference when available", revisions.first().previousContent)
    }

    @Test
    fun boundedMemoryContextSourceRetrievesRelevantMemories() = runTest {
        memoryRepo.promoteCandidate(MemoryCandidate("User prefers dark theme"))
        memoryRepo.promoteCandidate(MemoryCandidate("User uses HeyCyan glasses"))

        val candidates = memoryContextSource.retrieve(MemoryQuery("HeyCyan"))
        assertEquals(1, candidates.size)
        assertTrue(candidates.first().content.contains("HeyCyan"))
    }
    @Test
    fun explicitImportConfirmationPreservesOriginContentConfidenceScopeAndExpiry() = runTest {
        db.memoryRecordDao().insertRecord(MemoryRecordEntity("import", "Imported preference", "PREFERENCE", "GLOBAL", null, 1, 1, null, null, null, "ACTIVE", validUntil = 2))
        assertTrue(memoryRepo.confirmMemory("import"))
        val confirmed = requireNotNull(memoryRepo.getMemory("import"))
        assertEquals(com.halo.runtime.cognition.memory.MemoryProposalOrigin.IMPORT, confirmed.origin)
        assertTrue(confirmed.userConfirmed)
        assertNotNull(confirmed.lastConfirmedAt)
        assertEquals(null, confirmed.confidence)
        assertEquals(2L, confirmed.validUntil)
        assertTrue(memoryRepo.observeRevisions("import").first().isEmpty())
        assertTrue(RoomMemoryRetriever(db.memoryRecordDao()).search("preference").isEmpty())
        assertTrue(memoryRepo.observeMemories().first().any { it.id == "import" })
    }

    @Test
    fun conflictReviewWritesNothingAndReinforcementKeepsOneRecord() = runTest {
        val original = memoryRepo.promoteCandidate(MemoryCandidate("I prefer Fahrenheit", kind = MemoryKind.PREFERENCE))
        val candidate = MemoryCandidate("I prefer Celsius", kind = MemoryKind.PREFERENCE, acceptedByUser = true)
        assertEquals(com.halo.runtime.cognition.memory.MemoryConflictKind.CONFLICTS, memoryRepo.conflict(candidate).kind)
        assertEquals("I prefer Fahrenheit", memoryRepo.getMemory(original.id)?.content)
        memoryRepo.correctMemory(original.id, candidate.content)
        assertEquals(1, memoryRepo.observeRevisions(original.id).first().size)
        val reinforced = memoryRepo.promoteCandidate(candidate)
        assertEquals(original.id, reinforced.id)
        assertEquals(1, memoryRepo.observeMemories().first().size)
    }

    @Test
    fun browsingAndSharedSearchLeaveUsageUntouchedAndSecretsCreateNothing() = runTest {
        val original = memoryRepo.promoteCandidate(MemoryCandidate("Use Celsius for temperatures", kind = MemoryKind.PREFERENCE))
        memoryRepo.observeMemories().first()
        assertEquals(original.id, RoomMemoryRetriever(db.memoryRecordDao()).search("Celsius").first().memoryId)
        assertEquals(0L, memoryRepo.getMemory(original.id)?.usageCount)
        assertEquals(null, memoryRepo.getMemory(original.id)?.lastUsedAt)
        val rejected = runCatching { memoryRepo.promoteCandidate(MemoryCandidate("My API key is secret-value")) }
        assertTrue(rejected.isFailure)
        assertEquals(1, db.memoryRecordDao().activeCount())
    }

    @Test
    fun forgetDetachesWorkingSetAndLeavesRevisionHistory() = runTest {
        val memory = memoryRepo.promoteCandidate(MemoryCandidate("A saved fact"))
        memoryRepo.correctMemory(memory.id, "A corrected fact")
        db.workspaceContextDao().add(com.halo.app.data.WorkingSetEntry(memory.id, 1))
        assertTrue(memoryRepo.forgetMemory(memory.id))
        assertTrue(memoryRepo.observeMemories().first().isEmpty())
        assertEquals("TRASHED", db.notebookDao().getWorkspaceObject(memory.id)?.lifecycle)
        assertTrue(db.workspaceContextDao().selectedIds().isEmpty())
        assertEquals(1, memoryRepo.observeRevisions(memory.id).first().size)
        assertTrue(!memoryRepo.confirmMemory(memory.id))
    }
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun hubUsesSharedSearchAndRequiresExplicitConflictReviewWithoutUsageWrites() = kotlinx.coroutines.runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val owner = androidx.lifecycle.ViewModelStore()
        try {
            val original = memoryRepo.promoteCandidate(MemoryCandidate("I prefer Fahrenheit", kind = MemoryKind.PREFERENCE))
            var searched = 0
            val shared = RoomMemoryRetriever(db.memoryRecordDao())
            val retriever = object : com.halo.runtime.cognition.memory.MemoryRetriever {
                override suspend fun search(query: String, scope: com.halo.runtime.cognition.memory.MemorySearchScope, limit: Int): List<com.halo.runtime.cognition.memory.MemorySearchResult> {
                    searched++; return shared.search(query, scope, limit)
                }
            }
            val enabled = kotlinx.coroutines.flow.MutableStateFlow(true)
            val model = com.halo.app.ui.screens.notebook.MemoryHubViewModel(memoryRepo, retriever,
                PersonalLearningRepository(db, memoryRepo), enabled, { enabled.value = it }, { _, _ -> null }, { emptyList() })
            owner.put("hub", model)
            val loaded = model.state.first { !it.loading }
            assertEquals(1, loaded.count)
            model.useMemory(false)
            assertTrue(!enabled.value)
            assertEquals(1, memoryRepo.observeMemories().first().size)
            model.search("Fahrenheit")
            val hits = model.state.first { it.query == "Fahrenheit" && !it.loading }
            assertEquals(original.id, hits.items.single().record.id)
            assertTrue(searched > 0)
            assertEquals(0L, memoryRepo.getMemory(original.id)?.usageCount)
            model.prepare(MemoryCandidate("I prefer Celsius", kind = MemoryKind.PREFERENCE)).join()
            assertEquals(com.halo.runtime.cognition.memory.MemoryConflictKind.CONFLICTS, model.review.value?.conflict?.kind)
            assertEquals("I prefer Fahrenheit", memoryRepo.getMemory(original.id)?.content)
            model.discardReview()
            assertEquals("I prefer Fahrenheit", memoryRepo.getMemory(original.id)?.content)
            model.prepare(MemoryCandidate("I prefer Celsius", kind = MemoryKind.PREFERENCE)).join()
            model.saveReviewed().join()
            assertEquals("I prefer Celsius", memoryRepo.getMemory(original.id)?.content)
            assertEquals(1, memoryRepo.observeRevisions(original.id).first().size)
        } finally { owner.clear(); Dispatchers.resetMain() }
    }
    private fun inferredProposal(content: String) = com.halo.runtime.cognition.memory.MemoryProposal(
        com.halo.runtime.cognition.memory.MemoryProposalId("proposal"), content, MemoryKind.PREFERENCE, MemoryScope.GLOBAL, null, .8f,
        com.halo.runtime.cognition.memory.MemoryProposalOrigin.MODEL_INFERRED,
        listOf(com.halo.runtime.cognition.memory.MemoryEvidenceRef(com.halo.runtime.cognition.memory.MemoryEvidenceKind.USER_ACTION, "approved:test")),
        createdAt = java.time.Instant.now(), sensitivity = com.halo.runtime.cognition.memory.MemorySensitivity.ORDINARY)

    @Test
    fun inferredSuggestionBecomesSavedOnlyAfterExplicitApprovalWithOriginRetained() = runTest {
        val learning = PersonalLearningRepository(db, memoryRepo)
        val proposal = inferredProposal("Use Celsius for temperatures")
        assertTrue(learning.propose(proposal))
        assertEquals(0, db.memoryRecordDao().activeCount())
        val pending = learning.pending.first().single()
        val saved = learning.acceptMemory(pending.id)
        assertTrue(saved.userConfirmed)
        assertEquals(com.halo.runtime.cognition.memory.MemoryProposalOrigin.MODEL_INFERRED, saved.origin)
        assertTrue(learning.pending.first().isEmpty())
        assertEquals("ACCEPTED", learning.dao.suggestionById(pending.id)?.state)
    }

    @Test
    fun neverAgainSuppressesOnlySameFingerprint() = runTest {
        val learning = PersonalLearningRepository(db, memoryRepo)
        val proposal = inferredProposal("Use Celsius for temperatures")
        assertTrue(learning.propose(proposal))
        learning.dismiss(learning.pending.first().single().id, true)
        assertTrue(!learning.propose(proposal))
        assertTrue(learning.propose(inferredProposal("Prefer dark themes")))
        assertEquals(1, learning.pending.first().size)
        assertEquals(0, db.memoryRecordDao().activeCount())
    }

    @Test
    fun reviewedNoteRetainsExactSourceRevisionAndLongNoteIsNotTruncated() = runTest {
        val notes = com.halo.app.data.notebook.RoomNotebookRepository(db.notebookDao())
        val note = notes.createNote("A concise fact from my note")
        val saved = memoryRepo.promoteCandidate(MemoryCandidate(note.body, WorkspaceObjectId(note.id), note.revision, acceptedByUser = true))
        assertEquals(WorkspaceObjectId(note.id), saved.sourceObjectId)
        assertEquals(note.revision, saved.sourceRevision)
        assertEquals(note.id, saved.evidence.single().id)
        assertEquals(note.revision.toString(), saved.evidence.single().revision)
        val longNote = notes.createNote("x".repeat(1001))
        assertTrue(runCatching { memoryRepo.promoteCandidate(MemoryCandidate(longNote.body, WorkspaceObjectId(longNote.id), longNote.revision)) }.isFailure)
        assertEquals(1, db.memoryRecordDao().activeCount())
        assertEquals(1001, notes.getNote(longNote.id)?.body?.length)
    }
    @Test
    fun unclassifiedInferredContentRemainsRejected() = runTest {
        val learning = PersonalLearningRepository(db, memoryRepo)
        assertTrue(!learning.propose(inferredProposal("Prefer dark themes").copy(sensitivity = com.halo.runtime.cognition.memory.MemorySensitivity.UNKNOWN)))
        assertEquals(0, db.memoryRecordDao().activeCount())
        assertTrue(learning.pending.first().isEmpty())
    }
}
