package com.halo.app.data.memory

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import com.halo.app.data.notebook.WorkspaceObjectId
import kotlinx.coroutines.flow.first
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
        val candidate = MemoryCandidate(
            content = "Prefer local inference when available",
            sourceObjectId = WorkspaceObjectId("note-99"),
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
}
