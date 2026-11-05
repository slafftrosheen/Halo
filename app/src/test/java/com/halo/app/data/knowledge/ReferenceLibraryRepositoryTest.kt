package com.halo.app.data.knowledge

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import com.halo.app.data.everyday.RoomCollectorRepository
import com.halo.app.everyday.collectors.*
import com.halo.app.everyday.EverydayWriteAuthority
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import java.io.ByteArrayInputStream
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class ReferenceLibraryRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var sources: RoomCollectorRepository
    private lateinit var repo: ReferenceLibraryRepository
    private lateinit var scope: CoroutineScope
    private val files = mutableMapOf<String, String>()
    private var child = false
    private var interrupt = false
    private var interruptId: String? = null
    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).allowMainThreadQueries().build()
        db.openHelper.writableDatabase // Warm schema outside the import/race deadlines.
        sources = RoomCollectorRepository(db); scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        repo = owner(sources)
    }
    private fun owner(sourceOwner: CollectorRepository) = ReferenceLibraryRepository(db, sourceOwner,
        { ByteArrayInputStream(requireNotNull(files[it]).toByteArray()) }, { 4L * 1024 * 1024 * 1024 }, { 0L }, { { if (interrupt && interruptId?.let { db.referenceDao().jobNow(it)?.let { job -> job.state == "IMPORTING" && job.processed >= 16 } } == true) error("Interrupted fixture") } }, { child }, scope)
    @After fun tearDown() = runBlocking { scope.coroutineContext[Job]?.cancelAndJoin(); db.close() }
    private suspend fun completed(id: String): ReferenceImportJobEntity = withTimeout(60000) {
        repo.jobs.first { rows -> rows.any { it.id == id && it.state in setOf("READY", "FAILED", "INTERRUPTED", "CANCELLED") } }.single { it.id == id }
    }
    private suspend fun install(): ReferenceImportJobEntity {
        files["owned:one"] = "{\"id\":\"water\",\"title\":\"Безопасная вода\",\"text\":\"Чистая вода и reference water\",\"language\":\"ru\",\"license\":\"CC0\"}\n"
        val preview = repo.preview("owned:one", "owned.jsonl"); repo.confirm(preview.id, preview.reviewToken)
        return completed(preview.id).also { assertEquals(it.message, "READY", it.state) }
    }
    @Test fun confirmedImportUsesRealUnicodeFtsAndExactGenerationReadback() = runBlocking {
        val installed = install(); val page = repo.query("ВОДА")
        assertEquals(1, page.entries.size)
        val hit = page.entries.single(); val source = requireNotNull(sources.source(installed.sourceId))
        assertEquals("water", repo.entry(source.id, source.version, hit.generation, hit.entryId).entryId)
        assertTrue(runCatching { repo.entry(source.id, source.version + 1, hit.generation, hit.entryId) }.isFailure)
        assertTrue(runCatching { repo.preview("owned:one", "copy.jsonl") }.isFailure)
    }
    @Test fun failedReplacementLeavesOldReadyContentSelectedAndSearchable() = runBlocking {
        val installed = install(); val old = requireNotNull(db.referenceDao().library(installed.sourceId)); val source = requireNotNull(sources.source(installed.sourceId))
        files["owned:update"] = (1..3).joinToString("\n") { "{\"id\":\"$it\",\"title\":\"New\",\"text\":\"Staged body\"}" } + "\n{malformed}\n"
        val preview = repo.preview("owned:update", "update.jsonl", source.id, source.version)
        repo.confirm(preview.id, preview.reviewToken)
        assertEquals("FAILED", completed(preview.id).state)
        assertEquals(old.generation, db.referenceDao().library(source.id)?.generation)
        assertEquals(1, repo.query("reference").entries.size)
        assertTrue(sources.source(source.id)!!.selected)
    }
    @Test fun revokingChildApprovalDuringASuspendedSearchCannotReturnOldPassages() = runBlocking {
        val installed = install(); val source = requireNotNull(sources.source(installed.sourceId))
        val approved = sources.save(source.copy(childApproved = true), source.version); child = true
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val held = object : CollectorRepository by sources {
            override suspend fun states(): List<CollectorState> { val rows = sources.states(); entered.complete(Unit); release.await(); return rows }
        }
        val reader = owner(held)
        withTimeout(10000) {
            val pending = async { runCatching { reader.query("вода") } }
            entered.await(); sources.save(approved.copy(childApproved = false), approved.version); release.complete(Unit)
            assertTrue(pending.await().isFailure)
        }
    }
    @Test fun interruptedCheckpointCanBeCancelledAndResumedFromTheSameOriginalFile() = runBlocking {
        files["owned:many"] = (1..40).joinToString("\n") { "{\"id\":\"$it\",\"title\":\"Entry $it\",\"text\":\"Reference checkpoint water\"}" }
        val preview = repo.preview("owned:many", "many.jsonl"); interruptId = preview.id; interrupt = true
        repo.confirm(preview.id, preview.reviewToken)
        val stopped = completed(preview.id); assertEquals("INTERRUPTED", stopped.state); assertEquals(16, stopped.processed)
        interrupt = false; repo.cancel(stopped.id, stopped.reviewToken)
        val cancelled = repo.job(stopped.id)!!; assertEquals(0, cancelled.processed)
        val source = sources.source(stopped.sourceId)!!
        repo.resume(stopped.id, stopped.reviewToken, source.version)
        val ready = completed(stopped.id); assertEquals(ready.message, "READY", ready.state)
        assertEquals(40, db.referenceDao().count(source.id, ready.generation))
        sources.remove(source.id, ready.sourceVersion!!)
        assertNull(db.referenceDao().library(source.id)); assertEquals(0, db.referenceDao().count(source.id, ready.generation))
        assertTrue(repo.query("water").entries.isEmpty())
    }

    @Test fun cancellingAnUnconfirmedPreviewCanResumeReviewButNeverStartsImport() = runBlocking {
        files["owned:preview"] = "{\"id\":\"one\",\"title\":\"Owned\",\"text\":\"Saved reference\"}"
        val preview = repo.preview("owned:preview", "preview.jsonl")
        repo.cancel(preview.id, preview.reviewToken)
        assertNull(sources.source(preview.sourceId))
        val restored = repo.resume(preview.id, preview.reviewToken, null)
        assertEquals("REVIEWING", restored.state); assertNull(sources.source(preview.sourceId))
        repo.confirm(restored.id, restored.reviewToken)
        assertEquals("READY", completed(restored.id).state)
    }

    @Test fun previewHashesTheSampledStreamAndRejectsChangedBytesDuringReplacement() = runBlocking {
        val installed = install()
        val source = sources.source(installed.sourceId)!!
        val prior = db.referenceDao().library(source.id)!!
        val original = (1..4).joinToString("\n") { "{\"id\":\"$it\",\"title\":\"New $it\",\"text\":\"Reviewed water\"}" }
        var opens = 0
        val changing = ReferenceLibraryRepository(db, sources, {
            opens++
            (if (opens == 3) original.replace("Reviewed", "Unreviewed") else original).byteInputStream()
        }, { Long.MAX_VALUE }, { 0 }, { {} }, { false }, scope)
        val preview = changing.preview("owned:changing", "replacement.jsonl", source.id, source.version)
        assertEquals("Preview must fingerprint the same stream it sampled", 1, opens)
        val expected = java.security.MessageDigest.getInstance("SHA-256").digest(original.toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertEquals(expected, preview.fingerprint)
        changing.confirm(preview.id, preview.reviewToken)
        val finished = completed(preview.id)
        assertEquals(finished.message, "FAILED", finished.state)
        assertEquals(3, opens)
        assertEquals(prior, db.referenceDao().library(source.id))
        assertEquals(1, repo.query("reference").entries.size)
        assertEquals(0, db.referenceDao().count(source.id, preview.generation))
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun aWorkerQueuedBeforeRestoreCannotPublishAfterMaintenanceEnds() = runBlocking {
        val scheduler = TestCoroutineScheduler()
        val heldScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(scheduler))
        try {
            val held = ReferenceLibraryRepository(db, sources,
                { "{\"title\":\"Queued\",\"text\":\"Reference water\"}".byteInputStream() },
                { Long.MAX_VALUE }, { 0 }, { {} }, { false }, heldScope)
            val preview = held.preview("owned:queued", "queued.jsonl")
            held.confirm(preview.id, preview.reviewToken)
            assertEquals("QUEUED", db.referenceDao().job(preview.id)!!.state)
            EverydayWriteAuthority.beginMaintenance().close()
            scheduler.runCurrent()
            val stopped = completed(preview.id)
            assertEquals(stopped.message, "INTERRUPTED", stopped.state)
            assertNull(db.referenceDao().library(preview.sourceId)!!.generation)
            assertEquals(0, db.referenceDao().count(preview.sourceId, preview.generation))
        } finally {
            val job = heldScope.coroutineContext[Job]!!; job.cancel()
            withTimeout(10000) { while (!job.isCompleted) { scheduler.runCurrent(); delay(10) } }
        }
    }

    @Test fun maintenanceBlocksEntryAndIdentityReadsAndFreshReadsRecoverAfterward() = runBlocking {
        val installed = install()
        val source = sources.source(installed.sourceId)!!
        EverydayWriteAuthority.beginMaintenance().use {
            assertTrue(runCatching { repo.entry(source.id, source.version, installed.generation, "water") }.isFailure)
            assertTrue(runCatching { repo.readIdentity() }.isFailure)
        }
        assertEquals("water", repo.entry(source.id, source.version, installed.generation, "water").entryId)
    }

}
