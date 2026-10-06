package com.halo.app.data.models

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import com.halo.providers.litert.runtime.*
import com.halo.sdk.provider.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LocalRoleQualificationRepositoryTest {
    private fun row() = LocalModelRoleQualification("model", "a".repeat(64), 100L,
        LocalComputeBackend.CPU, LocalComputeBackend.CPU, 4096, 4, 0, 100L,
        fast = LocalFastQualification(100L, listOf(10L, 20L, 30L), 3, 20L, 10L, 30L, "CPU", LocalRoleProbeVerdict.PASS),
        chat = LocalChatQualification(2, 3, true, false, true, LocalRoleProbeVerdict.FAIL),
        startedAtMs = 1L, completedAtMs = 2L, memoryPressure = "NORMAL")
    @Test fun completedFailuresPersistButCancelledAndChangedDoNot() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val repo = LocalRoleQualificationRepository(db.localRoleQualificationDao(), DeviceRuntimeIdentity("device", "m", "m", 35, 256))
            assertTrue(repo.save(row()))
            assertFalse(repo.save(row().copy(failure = LocalRoleProbeFailure.CANCELLED)))
            assertFalse(repo.save(row().copy(failure = LocalRoleProbeFailure.ARTIFACT_CHANGED)))
            val stored = repo.history.first().single()
            assertEquals("FAIL", stored.chatVerdict)
            assertEquals(2, stored.chatPassed)
            assertTrue(stored.stable)
            assertTrue(repo.save(row().copy(completedAtMs = 3, memoryPressure = "CRITICAL")))
            val latest = repo.history.first().first()
            assertFalse(latest.stable)
            assertEquals("CRITICAL", latest.memoryPressure)
            assertEquals(2, repo.history.first().size)
            assertTrue(repo.save(row().copy(completedAtMs = 4, failure = LocalRoleProbeFailure.LOAD_FAILED)))
            assertFalse(repo.history.first().first().stable)
            assertTrue(repo.save(row().copy(completedAtMs = 5, agentic = LocalAgenticQualification(
                LocalRoleProbeVerdict.FAIL, ToolCallProtocol.HALO_PROMPT, failure = LocalRoleProbeFailure.GENERATION_FAILED))))
            assertFalse(repo.history.first().first().stable)
            assertEquals(4, repo.history.first().size)
            assertTrue(repo.save(row().copy(completedAtMs = 6, chat = row().chat!!.copy(failure = LocalRoleProbeFailure.GENERATION_FAILED))))
            assertFalse(repo.history.first().first().stable)
        } finally { db.close() }
    }
}
