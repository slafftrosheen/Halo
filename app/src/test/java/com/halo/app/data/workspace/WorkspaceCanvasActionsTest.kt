package com.halo.app.data.workspace

import com.halo.app.di.tools.*
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class WorkspaceCanvasActionsTest {
    @Test fun typedCanvasChangesAreSavedInTheEditorOwnerAndRejectStaleOrLinkedTextWrites() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val actions = WorkspaceCanvasActions(f.database, f.canvases)
            suspend fun call(name: String, args: Map<String, Any>) = actions.execute(ToolCall("fixture", name, args))
            suspend fun saved(name: String, args: Map<String, Any>) = JSONObject((call(name, args) as ToolExecutionResult.Success).output)
            val created = saved("halo_canvas_create", mapOf("title" to "Ideas"))
            val id = created.getString("canvasId")
            val first = saved("halo_canvas_add_text", mapOf("canvasId" to id, "version" to "canvas:1", "text" to "Original", "x" to 0, "y" to 0))
            val node = first.getString("nodeId")
            assertTrue(call("halo_canvas_edit_text", mapOf("canvasId" to id, "version" to "canvas:1", "nodeId" to node, "text" to "Stale")) is ToolExecutionResult.Rejected)
            val edited = saved("halo_canvas_edit_text", mapOf("canvasId" to id, "version" to first.getString("version"), "nodeId" to node, "text" to "Reviewed"))
            val moved = saved("halo_canvas_move", mapOf("canvasId" to id, "version" to edited.getString("version"), "nodeId" to node, "x" to 120, "y" to -40))
            val second = saved("halo_canvas_add_text", mapOf("canvasId" to id, "version" to moved.getString("version"), "text" to "Next", "x" to 300, "y" to 0))
            val connected = saved("halo_canvas_connect", mapOf("canvasId" to id, "version" to second.getString("version"), "fromNodeId" to node, "toNodeId" to second.getString("nodeId"), "label" to "next"))
            val read = saved("halo_canvas_get", mapOf("canvasId" to id))
            assertEquals(connected.getString("version"), read.getString("version"))
            val canvas = f.canvases.observe(id).first()!!
            assertEquals("Reviewed", canvas.nodes.single { it.id == node }.text)
            assertEquals(120f, canvas.nodes.single { it.id == node }.x)
            assertEquals(connected.getString("edgeId"), canvas.edges.single().id)
            val source = f.text.create("Linked source", WorkspaceTextFormat.TXT, "Intact")
            val linked = f.canvases.addReference(id, source.id, 0f, 0f)
            val version = f.continuity().describe(id).version
            assertTrue(call("halo_canvas_edit_text", mapOf("canvasId" to id, "version" to version, "nodeId" to linked, "text" to "Overwrite")) is ToolExecutionResult.Rejected)
            assertEquals("Intact", f.text.get(source.id)!!.text)
            f.workspace.trash(com.halo.app.data.notebook.WorkspaceObjectId(id))
            assertTrue(call("halo_canvas_get", mapOf("canvasId" to id)) is ToolExecutionResult.Rejected)
        }
    }
    @Test fun malformedArgumentsAndUnknownCoordinatesCannotCreateOrMoveAnything() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val actions = WorkspaceCanvasActions(f.database, f.canvases)
            val malformed = actions.execute(ToolCall("fixture", "halo_canvas_create", mapOf("title" to "No", "url" to "https://invalid")))
            assertEquals(ToolRejectionKind.INVALID_ARGUMENTS, (malformed as ToolExecutionResult.Rejected).kind)
            assertTrue(f.continuity().canvases.first().isEmpty())
            val canvas = f.canvases.create("Board")
            val node = f.canvases.addTextNode(canvas, 0f, 0f, "Stay")
            val revision = f.continuity().describe(canvas).version
            assertTrue(actions.execute(ToolCall("fixture", "halo_canvas_move", mapOf("canvasId" to canvas, "version" to revision, "nodeId" to node, "x" to Double.NaN, "y" to 0))) is ToolExecutionResult.Rejected)
            assertEquals(revision, f.continuity().describe(canvas).version)
        }
    }
}
