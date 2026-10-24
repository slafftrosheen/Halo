package com.halo.app.data.workspace

import com.halo.app.data.notebook.NotebookCanvasRepository
import com.halo.app.di.tools.HaloToolDescriptors
import com.halo.app.di.tools.RemoteInkActions
import com.halo.app.ink.CrossDeviceInkController
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.runtime.DeviceIdentity
import com.halo.sdk.runtime.embodiment.DeviceSessionManager
import com.halo.sdk.runtime.embodiment.InMemoryDeviceRegistry
import com.halo.sdk.runtime.ink.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class RemoteInkActionsTest {
    @Test fun maximumValidSelectionKeepsAllReferencesInsideTheDeclaredReceiptBudget() = runBlocking {
        WorkspaceFileToolsFixture().use { fixture ->
            val enabled = InkActivationPolicy { true }
            val canvases = NotebookCanvasRepository(fixture.database, fixture.database.notebookCanvasDao(),
                fixture.database.notebookDao(), remoteInkActivation = enabled)
            val ink = InkDocument(List(256) { index ->
                InkStroke(InkTool.STYLUS, listOf(InkPoint(10, 10)), index.toString().padEnd(128, 'x'))
            })
            val encoded = InkCodec.encode(ink)
            val board = canvases.create("Receipt budget")
            val node = canvases.addSketchNode(board, 0f, 0f, encoded)
            val devices = DeviceSessionManager(InMemoryDeviceRegistry(), enabled, object : InkCheckpointStore {
                override suspend fun commit(request: InkCheckpointRequest) = canvases.commitRemoteInk(request)
                override suspend fun selectionIsCurrent(session: InkSession, expectedRevision: String,
                    selection: InkSelectionContext) = canvases.remoteInkSelectionIsCurrent(session, expectedRevision, selection)
            })
            val pin = "a".repeat(64)
            devices.ink.associate(DeviceIdentity("tablet", "Android", "Tablet"),
                InkAssociation(InkEndpoint("tablet", width = 1000, height = 700), pin))
            val owner = CrossDeviceInkController(devices, canvases, "phone", enabled)
            val revision = canvases.remoteInkRevision(board, node)!!
            val session = owner.start("tablet", board, node, revision, "interaction")!!
            val sourceRevision = java.security.MessageDigest.getInstance("SHA-256").digest(("\n" + encoded).toByteArray())
                .joinToString("") { "%02x".format(it) }
            val now = System.currentTimeMillis()
            val selection = InkSelectionContext(board, node, InkSelectionBounds(0, 0, 100, 100),
                ink.strokes.map { it.strokeId }, sourceRevision, InkSelectionOrigin.BOX, now, now)
            val frame = InkEventCodec.encode(InkEventEnvelope(sourceDeviceId = "tablet", sessionId = session.sessionId,
                interactionId = "interaction", boardId = board, documentId = node, sequence = 1, sentAtMs = now,
                expectedRevision = revision, payload = InkEventPayload.Selection(selection)))
            assertEquals(InkReceiveStatus.PREVIEW, devices.ink.receive(pin, frame).status)
            val actions = RemoteInkActions(owner)
            val result = actions.execute(ToolCall("selection", "halo_ink_selection", mapOf("sessionId" to session.sessionId)),
                ToolExecutionContext("interaction", true)) as ToolExecutionResult.Success
            val descriptor = HaloToolDescriptors.workspace(actions.definitions().single { it.name == "halo_ink_selection" })!!
            assertTrue(result.output.length > 4000)
            assertTrue(result.output.length <= descriptor.execution.maxOutputChars)
            assertEquals(256, JSONObject(result.output).getJSONArray("strokeIds").length())
        }
    }
    @Test fun registeredControlsHaveClosedSchemasAndMandatoryStartApproval() = runBlocking {
        WorkspaceFileToolsFixture().use { fixture ->
            val actions = RemoteInkActions(CrossDeviceInkController(DeviceSessionManager(InMemoryDeviceRegistry()), fixture.canvases, "phone"))
            val descriptors = actions.definitions().map { requireNotNull(HaloToolDescriptors.workspace(it)) }
            val registry = ToolRegistry(listOf(actions), descriptors)
            assertEquals(emptyList<ToolOperabilityProblem>(), registry.operabilityReport(listOf(
                ToolFeatureOperability("cross-device-ink", descriptors.map { it.name }.toSet()))))
            val start = registry.descriptor("halo_ink_start")!!
            assertEquals(ConfirmationPolicy.ALWAYS, start.security.confirmation)
            assertEquals(ReplaySafety.NEVER_REPLAY, start.security.replaySafety)
            assertEquals(setOf("expectedRevision"), start.operability!!.sourceVersionArguments)
            val policy = PolicyToolAuthorizer(defaultPolicyProvider = { ToolApprovalPolicy.ALWAYS_ALLOW }, descriptorProvider = { start })
            assertTrue(policy.authorize(ToolCall("test", start.name, emptyMap())) is ToolAuthorization.Rejected)
            assertFalse(actions.definitions().any { it.name.contains("pair") || it.name.contains("inject") })
            val status = actions.execute(ToolCall("status", "halo_ink_status", emptyMap())) as ToolExecutionResult.Success
            assertFalse(JSONObject(status.output).getBoolean("enabled"))
        }
    }

    @Test fun startRequiresCurrentAuthorityAndRevisionAndStopSharesTheCanonicalOwner() = runBlocking {
        WorkspaceFileToolsFixture().use { fixture ->
            val enabled = InkActivationPolicy { true }
            val registry = InMemoryDeviceRegistry()
            val devices = DeviceSessionManager(registry, enabled)
            val canvases = NotebookCanvasRepository(fixture.database, fixture.database.notebookCanvasDao(),
                fixture.database.notebookDao(), remoteInkActivation = enabled)
            val board = canvases.create("Remote ink")
            val node = canvases.addSketchNode(board, 0f, 0f, InkCodec.encode(InkDocument(listOf(
                InkStroke(InkTool.FINGER, listOf(InkPoint(1, 1)), "original")))))
            devices.ink.associate(DeviceIdentity("tablet", "Android", "Tablet"),
                InkAssociation(InkEndpoint("tablet", width = 1400, height = 1000), "a".repeat(64)))
            val owner = CrossDeviceInkController(devices, canvases, "phone", enabled)
            val actions = RemoteInkActions(owner)
            val arguments = mapOf("deviceId" to "tablet", "boardId" to board, "documentId" to node,
                "expectedRevision" to canvases.remoteInkRevision(board, node)!!)
            val call = ToolCall("start", "halo_ink_start", arguments)
            assertTrue(actions.execute(call) is ToolExecutionResult.Rejected)
            assertTrue(actions.execute(call, ToolExecutionContext("interaction", false)) is ToolExecutionResult.Rejected)
            assertTrue(actions.execute(call.copy(arguments = arguments + ("expectedRevision" to "stale")),
                ToolExecutionContext("interaction", true)) is ToolExecutionResult.Rejected)
            assertTrue(owner.sessions.value.isEmpty())
            val result = actions.execute(call, ToolExecutionContext("interaction", true)) as ToolExecutionResult.Success
            val json = JSONObject(result.output)
            assertTrue(json.getBoolean("started")); assertFalse(json.getBoolean("connected"))
            val sessionId = json.getString("sessionId")
            assertEquals("interaction", devices.ink.sessions.value.single().interactionId)
            assertTrue(actions.execute(ToolCall("stop", "halo_ink_stop", mapOf("sessionId" to sessionId))) is ToolExecutionResult.Success)
            assertTrue(devices.ink.sessions.value.isEmpty())
        }
    }
}
