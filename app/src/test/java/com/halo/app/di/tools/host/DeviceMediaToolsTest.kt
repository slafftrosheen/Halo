package com.halo.app.di.tools.host

import com.halo.app.di.tools.HaloToolDescriptors
import com.halo.runtime.cognition.agent.AgentRunId
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.runtime.wearables.media.*
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class DeviceMediaToolsTest {
    @Test fun registeredFeaturesHaveClosedContractsAndNoReplayForEffects() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val fixture = com.halo.app.data.workspace.WorkspaceFileToolsFixture()
        try {
            val media = DeviceMediaTools({ null }, { true }, { false }, scope)
            val review = CaptureReviewTools({ error("No review access during discovery") }, { true }, { false })
            val exporter = com.halo.app.data.workspace.WorkspaceExternalExportSession(
                com.halo.app.ai.IntentQualificationFixture.MemoryStorage(),
                com.halo.app.data.workspace.WorkspaceExternalExport({ error("No source read during discovery") }, { null }, { null }, { true }), scope) { true }
            val export = WorkspaceExportTools(exporter, fixture.versions) { true }
            val definitions = media.definitions() + review.definitions() + export.definitions()
            val descriptors = definitions.map { requireNotNull(HaloToolDescriptors.device(it)) }
            val registry = ToolRegistry(listOf(media, review, export), descriptors)
            assertEquals(9, registry.definitions().size)
            assertTrue(registry.operabilityReport(listOf(ToolFeatureOperability("device-files", DeviceMediaTools.NAMES),
                ToolFeatureOperability("capture-review", CaptureReviewTools.NAMES),
                ToolFeatureOperability("external-export", WorkspaceExportTools.NAMES))).isEmpty())
            descriptors.filter { !it.name.endsWith("_status") }.forEach {
                assertEquals(ReplaySafety.NEVER_REPLAY, it.security.replaySafety)
                assertNotEquals(ConfirmationPolicy.NONE, it.security.confirmation)
            }
        } finally { scope.cancel(); fixture.close() }
    }
    @Test fun hostDispatchReturnsPromptlyAndCancellationUsesExactOperation() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val transport = object : DeviceMediaTransport {
            override suspend fun <T> withSession(deviceId: String, allowed: () -> Boolean, block: suspend (DeviceMediaSession) -> T): T {
                release.await()
                return block(object : DeviceMediaSession {
                    override val files = listOf(DeviceMediaEntry("file", "image.jpg", "PHOTO", 1))
                    override suspend fun download(file: DeviceMediaEntry, progress: (Long, Long?) -> Unit): DeviceMediaDownload = error("No download requested")
                })
            }
        }
        val manager = DeviceMediaManager(transport, DeviceMediaImporter { _,_,_,_ -> error("No save requested") }, { true })
        val tools = DeviceMediaTools({ manager }, { true }, { false }, scope)
        suspend fun call(name: String, args: Map<String, Any>) = tools.execute(ToolCall("request", name, args))
        try {
            assertTrue(call(DeviceMediaTools.REFRESH, mapOf("deviceId" to "a2")) is ToolExecutionResult.Rejected)
            withContext(RunToolScope(AgentRunId("typed"))) {
                val started = withTimeout(1000) { call(DeviceMediaTools.REFRESH, mapOf("deviceId" to "a2")) } as ToolExecutionResult.Success
                assertEquals(ActionTruth.DISPATCHED, started.actionTruth)
                val operation = JSONObject(started.output).getString("operationId")
                assertTrue(call(DeviceMediaTools.STATUS, mapOf("deviceId" to "a2", "offset" to 0, "url" to "injected")) is ToolExecutionResult.Rejected)
                assertTrue(call(DeviceMediaTools.CANCEL, mapOf("deviceId" to "other", "operationId" to operation)) is ToolExecutionResult.Rejected)
                assertTrue(call(DeviceMediaTools.CANCEL, mapOf("deviceId" to "a2", "operationId" to operation)) is ToolExecutionResult.Success)
                withTimeout(1000) { while (manager.state.value.busy) yield() }
                assertEquals("CANCELLED", manager.state.value.phase)
            }
            withContext(RunToolScope(AgentRunId("remote"), targetIsLocal = false)) {
                assertTrue(call(DeviceMediaTools.STATUS, mapOf("deviceId" to "a2", "offset" to 0)) is ToolExecutionResult.Rejected)
            }
        } finally { scope.cancel() }
    }
}
