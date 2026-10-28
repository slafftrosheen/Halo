package com.halo.app.context

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import com.halo.app.data.agent.RoomAgentRunRepository
import com.halo.app.di.tools.ContextReceiptTools
import com.halo.app.di.tools.HaloToolDescriptors
import com.halo.cognition.language.InteractionId
import com.halo.runtime.cognition.agent.*
import com.halo.runtime.cognition.context.*
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/** Deferred source and typed-contract regressions; not executed during the no-test pass. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ContextSourceReceiptsTest {
    @Test fun recordedSelectedRangeSurvivesJournalReadbackWithoutReplacingItsSource() = runBlocking { fixture {
        val anchor = com.halo.sdk.runtime.study.StudyTextAnchor("block-1", 3, 15)
        val id = run("anchored", listOf(ContextReference.WorkspaceObject("source", "7", anchor)))
        source.current["source"] = WorkspaceReferenceMetadata("source", "8", "Original source")
        val receipt = reader.read(id.value)!!.sources.single()
        assertEquals(anchor, receipt.textAnchor)
        assertEquals("7", receipt.recordedVersion)
        assertEquals(SourceReceiptStatus.CHANGED, receipt.status)
    } }

    private class MetadataSource : WorkspaceContextSource {
        val current = mutableMapOf<String, WorkspaceReferenceMetadata>()
        var metadataReads = 0
        var afterMetadataRead: () -> Unit = {}
        override suspend fun content(includeMemories: Boolean): List<String> = error("Receipt inspection must not read content")
        override suspend fun contentFor(objectId: String): String = error("Receipt inspection must not read a body")
        override suspend fun referenceMetadata(objectId: String): WorkspaceReferenceMetadata? {
            metadataReads++
            afterMetadataRead()
            return current[objectId]
        }
    }
    private class Fixture(val db: AppDatabase) {
        val store = RoomAgentRunRepository(db)
        val source = MetadataSource()
        val reader = ContextSourceReceipts(store, source)
        suspend fun run(id: String, references: List<ContextReference.WorkspaceObject>?): AgentRunId {
            val runId = AgentRunId(id); val at = Instant.now()
            store.create(AgentRun(runId, InteractionId("capture-$id"), "PRIVATE QUESTION MUST NOT LEAK",
                AgentRunState.EXECUTING, createdAt = at, updatedAt = at))
            if (references != null) store.appendStep(AgentStep.ContextUsed(AgentStepId("sources-$id"), runId, at,
                references.map { it.objectId }, emptyList(), references.filter { it.version != null }))
            return runId
        }
    }
    private suspend fun fixture(block: suspend Fixture.() -> Unit) {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        try { Fixture(db).block() } finally { db.close() }
    }

    @Test fun exactRecordedRevisionStaysSeparateFromCurrentMetadataAndLegacyUnknown() = runBlocking { fixture {
        val id = run("exact", listOf(ContextReference.WorkspaceObject("same", "1"),
            ContextReference.WorkspaceObject("changed", "1"), ContextReference.WorkspaceObject("legacy", null),
            ContextReference.WorkspaceObject("deleted", "1")))
        listOf("same" to "1", "changed" to "2", "legacy" to "3").forEach { (key, version) ->
            source.current[key] = WorkspaceReferenceMetadata(key, version, "Literal title $key")
        }
        val frame = requireNotNull(reader.read(id.value))
        assertTrue(frame.recorded)
        assertEquals(listOf(SourceReceiptStatus.UNCHANGED, SourceReceiptStatus.CHANGED,
            SourceReceiptStatus.UNKNOWN, SourceReceiptStatus.UNAVAILABLE), frame.sources.map { it.status })
        assertEquals("1", frame.sources[1].recordedVersion)
        assertEquals("2", frame.sources[1].currentVersion)
        assertNull(frame.sources[2].recordedVersion)
        assertNull(frame.sources[3].currentVersion)
        assertNull(frame.sources[3].title)
        assertNull(reader.read("exac"))
    } }

    @Test fun missingReceiptIsUnknownCoverageAndDoesNotResolveUnrelatedSources() = runBlocking { fixture {
        val id = run("no-receipt", null)
        source.current["unrelated"] = WorkspaceReferenceMetadata("unrelated", "7", "Unrelated title")
        val frame = requireNotNull(reader.read(id.value))
        assertFalse(frame.recorded)
        assertTrue(frame.sources.isEmpty())
        assertTrue(frame.memoryIds.isEmpty())
        assertEquals(0, source.metadataReads)
    } }

    @Test fun typedReadFailsClosedWithoutScopeSharingOrPolicyAndUsesExactCurrentRun() = runBlocking { fixture {
        val id = run("owned", listOf(ContextReference.WorkspaceObject("source", "1")))
        source.current["source"] = WorkspaceReferenceMetadata("source", "2", "Literal title")
        var enabled = true; var share = false
        val tool = ContextReceiptTools(reader, { enabled }, { share })
        val call = ToolCall("read", ContextReceiptTools.NAME, emptyMap())
        assertTrue(tool.execute(call) is ToolExecutionResult.Rejected)
        withContext(RunToolScope(id, targetIsLocal = false)) {
            assertTrue(tool.execute(call) is ToolExecutionResult.Rejected)
        }
        enabled = false
        withContext(RunToolScope(id, targetIsLocal = true)) {
            assertTrue(tool.execute(call) is ToolExecutionResult.Rejected)
        }
        assertEquals(0, source.metadataReads)
        enabled = true; share = true
        withContext(RunToolScope(id, targetIsLocal = false)) {
            val result = tool.execute(call) as ToolExecutionResult.Success
            val json = JSONObject(result.output)
            assertEquals(id.value, json.getString("runId"))
            assertEquals("CHANGED", json.getJSONArray("sources").getJSONObject(0).getString("comparison"))
            assertFalse(result.output.contains("PRIVATE QUESTION"))
            assertTrue(tool.execute(call.copy(arguments = mapOf("runId" to "owne"))) is ToolExecutionResult.Rejected)
        }
        val descriptors = tool.definitions().map { requireNotNull(HaloToolDescriptors.workspace(it)) }
        assertTrue(ToolRegistry(listOf(tool), descriptors).operabilityReport(
            listOf(ToolFeatureOperability("source-history", setOf(ContextReceiptTools.NAME)))).isEmpty())
    } }

    @Test fun permissionChangeDuringMetadataReadCannotReleaseItsResult() = runBlocking { fixture {
        val id = run("changed-policy", listOf(ContextReference.WorkspaceObject("source", "1")))
        source.current["source"] = WorkspaceReferenceMetadata("source", "1", "PRIVATE TITLE")
        var allowed = true
        source.afterMetadataRead = { allowed = false }
        val tool = ContextReceiptTools(reader, { true }, { allowed })
        withContext(RunToolScope(id, targetIsLocal = false)) {
            assertTrue(tool.execute(ToolCall("read", ContextReceiptTools.NAME, emptyMap())) is ToolExecutionResult.Rejected)
        }
        assertEquals(1, source.metadataReads)
    } }

    @Test fun repeatedSourceAndMemoryUseRemainRecentAcrossTheHistoryBound() = runBlocking { fixture {
        val id = run("repeated", null); val at = Instant.now()
        val a = ContextReference.WorkspaceObject("a", "1")
        store.appendStep(AgentStep.ContextUsed(AgentStepId("first"), id, at, listOf("a"), listOf("old-memory"), listOf(a)))
        val others = (0 until 20).map { ContextReference.WorkspaceObject("source-$it", "1") }
        store.appendStep(AgentStep.ContextUsed(AgentStepId("other-sources"), id, at,
            others.map { it.objectId }, (0 until 12).map { "memory-$it" }, others))
        store.appendStep(AgentStep.ContextUsed(AgentStepId("reuse-source"), id, at, listOf("a"), listOf("memory-12"), listOf(a)))
        store.appendStep(AgentStep.ContextUsed(AgentStepId("reuse-memory"), id, at, emptyList(), listOf("old-memory")))
        val frame = requireNotNull(reader.read(id.value))
        assertTrue(frame.truncated)
        assertEquals(20, frame.sources.size)
        assertEquals("a", frame.sources.last().objectId)
        assertFalse(frame.sources.any { it.objectId == "source-0" })
        assertEquals(12, frame.memoryIds.size)
        assertEquals("old-memory", frame.memoryIds.last())
        assertFalse(frame.memoryIds.contains("memory-0"))
        assertFalse(frame.memoryIds.contains("memory-1"))
    } }

    @Test fun boundedHistoryRetainsRecentReferencesAndSignalsTruncation() = runBlocking { fixture {
        val id = run("bounded", null); val at = Instant.now()
        (0 until 27).chunked(20).forEachIndexed { index, values ->
            val references = values.map { ContextReference.WorkspaceObject("source-$it", "1") }
            store.appendStep(AgentStep.ContextUsed(AgentStepId("chunk-$index"), id, at,
                references.map { it.objectId }, emptyList(), references))
        }
        val frame = requireNotNull(reader.read(id.value))
        assertTrue(frame.truncated)
        assertEquals(20, frame.sources.size)
        assertEquals("source-7", frame.sources.first().objectId)
        assertEquals("source-26", frame.sources.last().objectId)
        assertEquals(20, source.metadataReads)
    } }
}
