package com.halo.app.data.workspace

import com.halo.app.context.*
import com.halo.app.di.tools.WorkspaceCanvasActions
import com.halo.runtime.cognition.tools.ToolExecutionResult
import com.halo.sdk.provider.ToolCall
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class CurrentCanvasContextOwnerTest {
    @Test fun currentQueryHasRegisteredBoundedMetadataAndMutationContractsCarrySelectionGuard() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val actions = WorkspaceCanvasActions(f.database, f.canvases, CurrentCanvasContextOwner(f.database))
            val descriptors = actions.definitions().map { requireNotNull(com.halo.app.di.tools.HaloToolDescriptors.workspace(it)) }
            val registry = com.halo.runtime.cognition.tools.ToolRegistry(listOf(actions), descriptors)
            assertTrue(registry.operabilityReport(listOf(com.halo.runtime.cognition.tools.ToolFeatureOperability("canvas-current", descriptors.map { it.name }.toSet()))).isEmpty())
            val current = descriptors.single { it.name == "halo_canvas_current" }
            assertEquals(com.halo.runtime.cognition.tools.CapabilityId("workspace.read"), current.operability!!.capabilityId)
            val properties = current.operability!!.resultSchema["properties"] as Map<*, *>
            assertEquals(setOf("canvasId", "version", "selectedNodeIds", "selectionToken"), properties.keys)
            assertEquals(setOf("version", "selectionToken"), descriptors.single { it.name == "halo_canvas_edit_text" }.operability!!.sourceVersionArguments)
        }
    }

    @Test fun selectionChangeDetectedBeforeCommitRollsBackTheExistingCanonicalMutation() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val board = f.canvases.create()
            val card = f.canvases.addTextNode(board, 0f, 0f, "Before")
            val owner = CurrentCanvasContextOwner(f.database)
            val original = CanvasContextProjection(board, setOf(card), setOf(card), Any())
            owner.attach {
                // Synthetic view change while the canonical edit is staged, before transaction commit.
                if (f.database.notebookCanvasDao().getNode(board, card)?.text == "After")
                    CanvasContextProjection(board, emptySet(), setOf(card), Any()) else original
            }
            val current = owner.current()
            val actions = WorkspaceCanvasActions(f.database, f.canvases, owner)
            assertTrue(actions.execute(ToolCall("race", "halo_canvas_edit_text", mapOf("canvasId" to board,
                "version" to current.version, "nodeId" to card, "text" to "After", "selectionToken" to current.selectionToken))) is ToolExecutionResult.Rejected)
            assertEquals("Before", f.database.notebookCanvasDao().getNode(board, card)?.text)
            assertEquals(current.version, "canvas:${f.database.notebookCanvasDao().getDocument(board)!!.revision}")
        }
    }
    @Test fun currentSelectionIsMetadataOnlyAndTokenStopsChangedSelectionWithoutAWrite() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val board = f.canvases.create("Current")
            val first = f.canvases.addTextNode(board, 0f, 0f, "Private body must not be returned")
            val second = f.canvases.addTextNode(board, 100f, 0f, "Second")
            val owner = CurrentCanvasContextOwner(f.database)
            var projection = CanvasContextProjection(board, setOf(first), setOf(first, second), Any())
            val attachment = owner.attach { projection }
            val actions = WorkspaceCanvasActions(f.database, f.canvases, owner)
            val read = actions.execute(ToolCall("read", "halo_canvas_current", emptyMap())) as ToolExecutionResult.Success
            assertFalse(read.output.contains("Private body"))
            val snapshot = JSONObject(read.output)
            assertEquals(board, snapshot.getString("canvasId"))
            assertEquals(first, snapshot.getJSONArray("selectedNodeIds").getString(0))
            val args = mapOf("canvasId" to board, "version" to snapshot.getString("version"),
                "nodeId" to first, "text" to "Changed", "selectionToken" to snapshot.getString("selectionToken"))
            projection = CanvasContextProjection(board, setOf(second), setOf(first, second), Any())
            assertTrue(actions.execute(ToolCall("stale", "halo_canvas_edit_text", args)) is ToolExecutionResult.Rejected)
            assertEquals("Private body must not be returned", f.database.notebookCanvasDao().getNode(board, first)?.text)
            // Explicit exact-ID/revision requests remain compatible without view-relative authority.
            assertTrue(actions.execute(ToolCall("explicit", "halo_canvas_edit_text", args - "selectionToken")) is ToolExecutionResult.Success)
            owner.detach(attachment)
            assertTrue(actions.execute(ToolCall("gone", "halo_canvas_current", emptyMap())) is ToolExecutionResult.Rejected)
        }
    }

    @Test fun matchingSelectionEditsThroughExistingOwnerButCannotBeReplayedOrBorrowed() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val board = f.canvases.create("Selected")
            val card = f.canvases.addTextNode(board, 0f, 0f, "Before")
            val owner = CurrentCanvasContextOwner(f.database)
            val projection = CanvasContextProjection(board, setOf(card), setOf(card), Any())
            owner.attach { projection }
            val actions = WorkspaceCanvasActions(f.database, f.canvases, owner)
            val current = owner.current()
            val args = mapOf("canvasId" to board, "version" to current.version, "nodeId" to card,
                "text" to "After", "selectionToken" to current.selectionToken)
            assertTrue(actions.execute(ToolCall("edit", "halo_canvas_edit_text", args)) is ToolExecutionResult.Success)
            assertTrue(actions.execute(ToolCall("replay", "halo_canvas_edit_text", args)) is ToolExecutionResult.Rejected)
            assertEquals("After", f.database.notebookCanvasDao().getNode(board, card)?.text)
            val other = owner.attach { projection }
            assertTrue(runCatching { owner.current() }.isFailure)
            owner.detach(other)
            assertTrue(runCatching { owner.requireSelection(current.selectionToken, board, current.version, setOf("foreign")) }.isFailure)
        }
    }

    @Test fun sourceAndViewChangesDuringReadNeverPublishASelectionToken() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val board = f.canvases.create()
            val owner = CurrentCanvasContextOwner(f.database)
            owner.attach { CanvasContextProjection(board, emptySet(), emptySet(), Any()) }
            assertTrue(runCatching { owner.current() }.isFailure)
        }
    }
}
