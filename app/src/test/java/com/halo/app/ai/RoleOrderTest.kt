package com.halo.app.ai

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import com.halo.app.data.models.*
import com.halo.providers.litert.runtime.*
import com.halo.sdk.provider.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class RoleOrderTest {
    private val device = DeviceRuntimeIdentity("device", "m", "m", 28, 256)
    private fun row(at: Long, pass: Boolean = true) = LocalModelRoleQualification("model", "a".repeat(64), 100,
        LocalComputeBackend.CPU, LocalComputeBackend.CPU, 4096, 4, 0, 100,
        fast = LocalFastQualification(100, listOf(10, 20, 30), 3, 20, 10, 30, "CPU", LocalRoleProbeVerdict.PASS),
        chat = LocalChatQualification(3, 3, true, true, true, LocalRoleProbeVerdict.PASS),
        failure = if (pass) null else LocalRoleProbeFailure.GENERATION_FAILED,
        startedAtMs = 1, completedAtMs = at, memoryPressure = "NORMAL")
    private fun database() = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
        .allowMainThreadQueries().build()
    private fun repository(db: AppDatabase) = LocalRoleQualificationRepository(db.localRoleQualificationDao(), device, "build")
    private fun project(rows: List<LocalRoleQualificationEntity>): LocalRoleEvidenceState {
        val identity = rows.first().identity()
        val eligible = AiRoleCandidate(identity.ref, AiModelRole.FAST, true, setOf(AiCapability.TEXT), AiExecutionLocation.ON_DEVICE)
        return projectLocalRoleEvidence(rows, setOf(identity), mapOf(AiModelRole.FAST to listOf(eligible)))
    }

    @Test fun rollback() = runBlocking {
        val db = database()
        try {
            val repo = repository(db)
            assertTrue(repo.save(row(10_000)))
            assertTrue(repo.save(row(9_000, false)))
            val observed = repo.history.first()
            assertEquals(listOf(9_000L, 10_000L), observed.map { it.testedAtMs })
            assertEquals(observed, db.localRoleQualificationDao().history(device.fingerprint, "model"))
            assertFalse(project(observed).current.single().stable)
            assertNull(project(observed).recommendations[AiModelRole.FAST]?.recommended)
            // A fresh successful retest can supersede failure even after another clock rollback.
            assertTrue(repo.save(row(8_000)))
            assertNotNull(project(repo.history.first()).recommendations[AiModelRole.FAST]?.recommended)
        } finally { db.close() }
    }

    @Test fun equalTimes() = runBlocking {
        val db = database()
        try {
            val repo = repository(db)
            repo.save(row(10_000)); repo.save(row(10_000, false))
            assertFalse(repo.history.first().first().stable)
            assertNull(project(repo.history.first()).recommendations[AiModelRole.FAST]?.recommended)
        } finally { db.close() }
    }

    @Test fun future() = runBlocking {
        val db = database()
        try {
            val repo = repository(db)
            repo.save(row(10_000))
            repo.save(row(System.currentTimeMillis() + 600_000, false))
            val result = project(repo.history.first())
            assertTrue(result.current.isEmpty())
            assertNull(result.recommendations[AiModelRole.FAST]?.recommended)
            assertEquals(2, result.history.size)
        } finally { db.close() }
    }

    @Test fun reopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        fun open() = Room.databaseBuilder(context, AppDatabase::class.java, "ord.db").allowMainThreadQueries().build()
        var db = open()
        try {
            val repo = repository(db)
            repo.save(row(10_000)); repo.save(row(9_000, false))
            db.close(); db = open()
            val recreated = repository(db)
            assertEquals(listOf(9_000L, 10_000L), recreated.history.first().map { it.testedAtMs })
            assertNull(project(recreated.history.first()).recommendations[AiModelRole.FAST]?.recommended)
        } finally { db.close(); context.deleteDatabase("ord.db") }
    }

    @Test fun publicationAndApplyAreSerialized() = runBlocking {
        val db = database()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        try {
            val repo = repository(db)
            repo.save(row(10_000))
            val writes = mutableListOf<AiRoleBinding>()
            val apply = async(Dispatchers.IO) { repo.withCurrentHistory { durable ->
                val result = project(durable).recommendations.getValue(AiModelRole.FAST)
                val prior = result.candidates.single()
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                applyMeasuredRecommendation(AiModelRole.FAST, prior.identity.ref, prior, result,
                    prior.identity.artifactFingerprint, true, { error("Unexpected CHAT selection") }, writes::add)
            } }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val writer = async(Dispatchers.IO) { repo.save(row(9_000, false)) }
            try {
                assertNull(withTimeoutOrNull(100) { writer.await() })
                assertTrue(writes.isEmpty())
            } finally { release.countDown() }
            assertTrue(apply.await()); assertTrue(writer.await())
            assertEquals(1, writes.size)
            // The next explicit application must read the now-durable failure, not a delayed UI flow.
            val current = repo.withCurrentHistory { project(it) }
            assertNull(current.recommendations[AiModelRole.FAST]?.recommended)
        } finally { release.countDown(); db.close() }
    }
}
