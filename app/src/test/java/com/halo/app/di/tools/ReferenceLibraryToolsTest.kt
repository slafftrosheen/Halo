package com.halo.app.di.tools

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import com.halo.app.data.everyday.RoomCollectorRepository
import com.halo.app.data.knowledge.ReferenceLibraryRepository
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ReferenceLibraryToolsTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Application>(), AppDatabase::class.java).allowMainThreadQueries().build()
    private val sources = RoomCollectorRepository(db)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var child = false
    private val body = ("Вода \\\"quoted\\\"\n😀".repeat(2000))
    private val input get() = JSONObject().put("id", "one").put("title", "Вода reference").put("text", body).toString().byteInputStream()
    private val repo = ReferenceLibraryRepository(db, sources, { input }, { Long.MAX_VALUE }, { 0 }, { {} }, { child }, scope)
    private val executor = ReferenceLibraryTools(repo) { error("No network in this fixture") }
    private val descriptors = executor.definitions().map { requireNotNull(HaloToolDescriptors.assistant(it)) }
    private val registry = ToolRegistry(listOf(executor), descriptors)
    @After fun close() = runBlocking { scope.coroutineContext[Job]?.cancelAndJoin(); db.close() }
    private suspend fun call(name: String, args: Map<String, Any> = emptyMap()) = registry.executeAuthorized(
        ToolAuthorization.Authorized(ToolCall("owned", name, args)), ToolExecutionContext(interactionId = "owned"))
    private fun json(result: ToolExecutionResult) = JSONObject((result as ToolExecutionResult.Success).output)
    @Test fun reviewedImportAndExactReadUseRegisteredContractsAndCompleteChunks() = runBlocking {
        db.openHelper.writableDatabase
        val preview = repo.preview("content://owned/document", "reference.jsonl")
        val history = json(call("halo_library_import_review"))
        assertEquals(preview.id, history.getJSONArray("imports").getJSONObject(0).getString("id"))
        assertFalse(history.toString().contains("content://"))
        val reviewed = json(call("halo_library_import_review", mapOf("jobId" to preview.id)))
        assertFalse(reviewed.toString().contains("content://"))
        json(call("halo_library_import_action", mapOf("jobId" to preview.id, "reviewToken" to reviewed.getString("reviewToken"), "action" to "CONFIRM")))
        withTimeout(60000) { repo.jobs.first { rows -> rows.any { it.id == preview.id && it.state in setOf("READY", "FAILED", "INTERRUPTED") } } }
        assertEquals("READY", repo.job(preview.id)!!.state)
        val hit = json(call("halo_library_query", mapOf("query" to "ВОДА"))).getJSONArray("hits").getJSONObject(0)
        val args = mapOf("sourceId" to hit.getString("sourceId"), "sourceVersion" to hit.getLong("sourceVersion"), "generation" to hit.getString("generation"), "entryId" to hit.getString("entryId"))
        val first = json(call("halo_library_get", args)); val snapshot = first.getString("snapshot")
        var offset = 0; val text = StringBuilder()
        do {
            val page = json(call("halo_library_get", args + mapOf("snapshot" to snapshot, "path" to listOf("text"), "view" to "text", "readOffset" to offset)))
            assertTrue(page.toString().length <= 4000); text.append(page.getString("text"))
            offset = if (page.isNull("nextReadOffset")) -1 else page.getInt("nextReadOffset")
        } while (offset >= 0)
        assertEquals(body, text.toString())
        val source = sources.source(preview.sourceId)!!; sources.save(source.copy(selected = false), source.version)
        assertTrue(call("halo_library_get", args) is ToolExecutionResult.Rejected)
        assertTrue(registry.operabilityReport(listOf(ToolFeatureOperability("reference-libraries", ReferenceLibraryContracts.names))).isEmpty())
    }
    @Test fun aCancelledUnconfirmedJobResumesOnlyItsReviewThroughTheRegistry() = runBlocking {
        val preview = repo.preview("content://owned/cancelled", "reference.jsonl")
        val args = mapOf("jobId" to preview.id, "reviewToken" to preview.reviewToken)
        json(call("halo_library_import_action", args + ("action" to "CANCEL")))
        val restored = json(call("halo_library_import_action", args + ("action" to "RESUME")))
        assertEquals("REVIEWING", restored.getString("state")); assertFalse(restored.getBoolean("completed"))
        assertNull(sources.source(preview.sourceId))
    }
    @Test fun libraryOperationsKeepWorkspaceDisclosureAndNeverReplayDownloadsOrImports() {
        descriptors.forEach { descriptor ->
            assertTrue(descriptor.id.value in WorkspaceToolAccess.readTools)
            assertNotNull(descriptor.operability)
            if (descriptor.id.value !in ReferenceLibraryContracts.queries) {
                assertEquals(ConfirmationPolicy.ALWAYS, descriptor.security.confirmation)
                assertEquals(ReplaySafety.NEVER_REPLAY, descriptor.security.replaySafety)
            }
        }
    }
}
