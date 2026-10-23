package com.halo.app.data.workspace

import com.halo.app.di.tools.HaloToolDescriptors
import com.halo.app.di.tools.WorkspaceContinuityActions
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.tools.ToolExecutionResult
import com.halo.runtime.cognition.tools.WorkspaceToolAccess
import com.halo.sdk.provider.ToolCall
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WorkspaceContinuityActionsTest {
    @Test fun allSharedHandoffsRetainWorkspacePolicyAndNeverClaimRendering() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val owner = f.continuity()
            val actions = WorkspaceContinuityActions(owner)
            assertEquals(6, actions.definitions().size)
            actions.definitions().forEach { assertNotNull(HaloToolDescriptors.workspace(it)); assertTrue(it.name in WorkspaceToolAccess.readTools) }
            val source = f.text.create("Draft", WorkspaceTextFormat.TXT, "Exact source")
            suspend fun call(name: String, args: Map<String, Any>) = actions.execute(ToolCall("fixture", name, args))
            val described = call("halo_workspace_describe", mapOf("objectId" to source.id)) as ToolExecutionResult.Success
            assertEquals(source.revision.toString(), JSONObject(described.output).getString("version"))
            val opened = call("halo_workspace_open_with", mapOf("objectId" to source.id, "version" to source.revision.toString(), "destination" to "CHAT")) as ToolExecutionResult.Success
            assertEquals(ActionTruth.DISPATCHED, opened.actionTruth)
            assertEquals(source.text, f.text.get(source.id)!!.text)
            val request = owner.requests.pending.value!!
            assertTrue(owner.requests.claim(request)); owner.requests.finish(request, true)
            val status = call("halo_workspace_handoff_status", mapOf("requestId" to request.id)) as ToolExecutionResult.Success
            assertEquals("DELIVERED_TO_HOST", JSONObject(status.output).getString("phase"))
            assertTrue(JSONObject(status.output).isNull("resultId"))
            assertTrue(call("halo_workspace_open_with", mapOf("objectId" to source.id, "version" to "stale", "destination" to "CHAT")) is ToolExecutionResult.Rejected)
            assertTrue(call("halo_workspace_describe", mapOf("objectId" to source.id, "url" to "https://invalid")) is ToolExecutionResult.Rejected)
        }
    }
    @Test fun sharedInsertSelectAndDurableResultStatusUseTheCanonicalOwners() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val context = f.contextOwner()
            val owner = f.continuity(context)
            val actions = WorkspaceContinuityActions(owner)
            suspend fun call(name: String, args: Map<String, Any>) = actions.execute(ToolCall("fixture", name, args))
            val source = f.text.create("Draft", WorkspaceTextFormat.TXT, "Exact source")
            assertTrue(call("halo_workspace_select", mapOf("objectId" to source.id, "version" to "stale")) is ToolExecutionResult.Rejected)
            assertTrue(f.database.workspaceContextDao().selectedIds().isEmpty())
            assertTrue(call("halo_workspace_select", mapOf("objectId" to source.id, "version" to source.revision.toString())) is ToolExecutionResult.Success)
            assertEquals(listOf(source.id), f.database.workspaceContextDao().selectedIds())
            val canvas = f.canvases.create("Canvas")
            val revision = context.versionFor(canvas)!!
            val inserted = call("halo_workspace_insert", mapOf("objectId" to source.id, "version" to source.revision.toString(), "canvasId" to canvas, "canvasVersion" to revision)) as ToolExecutionResult.Success
            assertEquals(source.id, JSONObject(inserted.output).getString("objectId"))
            assertTrue(call("halo_workspace_insert", mapOf("objectId" to source.id, "version" to source.revision.toString(), "canvasId" to canvas, "canvasVersion" to revision)) is ToolExecutionResult.Rejected)
            val image = f.png("Photo")
            val token = UUID.randomUUID().toString()
            val result = f.studio().createFromImage("Source result", image.objectId.value, image.version!!, token)
            val status = call("halo_workspace_handoff_status", mapOf("requestId" to token)) as ToolExecutionResult.Success
            assertEquals("SAVED_RESULT_AVAILABLE", JSONObject(status.output).getString("phase"))
            assertEquals(result.id, JSONObject(status.output).getString("resultId"))
        }
    }
}
