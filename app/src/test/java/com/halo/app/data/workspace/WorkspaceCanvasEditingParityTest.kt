package com.halo.app.data.workspace

import com.halo.app.di.tools.WorkspaceCanvasActions
import com.halo.runtime.cognition.tools.ToolExecutionResult
import com.halo.sdk.provider.ToolCall
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class WorkspaceCanvasEditingParityTest {
    private suspend fun version(f: WorkspaceFileToolsFixture, board: String) = "canvas:${f.database.notebookCanvasDao().getDocument(board)!!.revision}"
    private suspend fun call(f: WorkspaceFileToolsFixture, board: String, name: String, args: Map<String, Any> = emptyMap(), observed: String? = null): ToolExecutionResult {
        val current = observed ?: version(f, board)
        return WorkspaceCanvasActions(f.database, f.canvases).execute(ToolCall("parity", name, mapOf("canvasId" to board, "version" to current) + args))
    }
    private suspend fun saved(f: WorkspaceFileToolsFixture, board: String, name: String, args: Map<String, Any> = emptyMap()): JSONObject =
        JSONObject((call(f, board, name, args) as ToolExecutionResult.Success).output).also { assertEquals(version(f, board), it.getString("version")) }

    @Test fun maximumLegalSelectionReceiptFitsTheRegisteredOutputBudgetWithoutLosingIds() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val board = f.canvases.create("Boundary")
            val seed = f.canvases.addTextNode(board, 0f, 0f, "Synthetic")
            val template = f.database.notebookCanvasDao().getNode(board, seed)!!
            val ids = (0 until 100).map { "n".repeat(96) + it.toString().padStart(4, '0') }
            ids.forEach { f.database.notebookCanvasDao().insertNode(template.copy(id = it)) }
            val actions = WorkspaceCanvasActions(f.database, f.canvases)
            val result = call(f, board, "halo_canvas_delete", mapOf("nodeIds" to ids)) as ToolExecutionResult.Success
            val descriptor = com.halo.app.di.tools.HaloToolDescriptors.workspace(actions.definitions().single { it.name == "halo_canvas_delete" })!!
            assertTrue(result.output.length > 4000)
            assertTrue(result.output.length <= descriptor.execution.maxOutputChars)
            val returned = JSONObject(result.output).getJSONArray("nodeIds")
            assertEquals(ids, (0 until returned.length()).map(returned::getString))
            assertEquals(seed, f.canvases.observe(board).first()!!.nodes.single().id)
        }
    }

    @Test fun renameGroupsTagsDuplicatesAndBothArrangementModesUseSavedEditorObjects() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val board = f.canvases.create("Before")
            val source = f.text.create("Source", WorkspaceTextFormat.TXT, "Keep source")
            val first = f.canvases.addReference(board, source.id, 10f, 20f)
            val second = f.canvases.addTextNode(board, 80f, 90f, "Local text")
            val renamed = saved(f, board, "halo_canvas_rename", mapOf("title" to "  Project  "))
            assertEquals("Project", renamed.getString("title"))
            assertEquals("Project", f.canvases.observe(board).first()!!.title)
            val group = saved(f, board, "halo_canvas_add_group", mapOf("title" to "Ideas", "x" to -500, "y" to -400)).getString("nodeId")
            val tag = saved(f, board, "halo_canvas_tag", mapOf("nodeIds" to listOf(first, second), "label" to "#review")).getString("nodeId")
            val copy = saved(f, board, "halo_canvas_duplicate", mapOf("nodeId" to first)).getString("nodeId")
            val grid = saved(f, board, "halo_canvas_arrange", mapOf("nodeIds" to listOf(first, second), "operation" to "GRID"))
            assertEquals(listOf(first, second), (0 until grid.getJSONArray("nodeIds").length()).map(grid.getJSONArray("nodeIds")::getString))
            saved(f, board, "halo_canvas_arrange", mapOf("nodeIds" to listOf(first, second), "operation" to "BRING_FORWARD"))
            val canvas = f.canvases.observe(board).first()!!
            assertEquals(com.halo.app.data.notebook.CanvasNodeType.GROUP, canvas.nodes.single { it.id == group }.type)
            assertEquals("review", canvas.nodes.single { it.id == tag }.text)
            assertEquals(setOf(first, second), canvas.edges.filter { it.toNodeId == tag }.map { it.fromNodeId }.toSet())
            assertEquals(source.id, canvas.nodes.single { it.id == copy }.refObjectId)
            assertNotEquals(first, copy)
            val selected = canvas.nodes.filter { it.id in setOf(first, second) }
            assertEquals(2, selected.map { it.x to it.y }.distinct().size)
            assertTrue(selected.minOf { it.zIndex } > canvas.nodes.filter { it.id !in setOf(first, second) }.maxOf { it.zIndex })
            assertEquals("Keep source", f.text.get(source.id)!!.text)
        }
    }

    @Test fun disconnectAndDeleteRemoveOnlyExactLinksAndCardsPreservingSourcesAndUnrelatedEdges() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val board = f.canvases.create("Board")
            val source = f.text.create("Source", WorkspaceTextFormat.TXT, "Intact")
            val linked = f.canvases.addReference(board, source.id, 0f, 0f)
            val remove = f.canvases.addTextNode(board, 0f, 0f, "Remove")
            val keep = f.canvases.addTextNode(board, 0f, 0f, "Keep")
            val keepAlso = f.canvases.addTextNode(board, 0f, 0f, "Keep too")
            val edge = f.canvases.connect(board, linked, remove, "Disconnect")
            val keptEdge = f.canvases.connect(board, keep, keepAlso, "Retain")
            val disconnected = saved(f, board, "halo_canvas_disconnect", mapOf("edgeId" to edge))
            assertEquals(edge, disconnected.getString("edgeId"))
            assertEquals(4, f.canvases.observe(board).first()!!.nodes.size)
            val afterDisconnect = version(f, board)
            assertTrue(call(f, board, "halo_canvas_disconnect", mapOf("edgeId" to edge)) is ToolExecutionResult.Rejected)
            assertEquals(afterDisconnect, version(f, board))
            f.canvases.connect(board, linked, keep, "Incident")
            val beforeDelete = version(f, board)
            val args = mapOf("nodeIds" to listOf(linked, remove))
            val receipt = saved(f, board, "halo_canvas_delete", args)
            assertEquals(2, receipt.getJSONArray("nodeIds").length())
            val canvas = f.canvases.observe(board).first()!!
            assertEquals(setOf(keep, keepAlso), canvas.nodes.map { it.id }.toSet())
            assertEquals(keptEdge, canvas.edges.single().id)
            assertEquals("Intact", f.text.get(source.id)!!.text)
            assertTrue(call(f, board, "halo_canvas_delete", args, beforeDelete) is ToolExecutionResult.Rejected)
            assertEquals(receipt.getString("version"), version(f, board))
        }
    }

    @Test fun ungroupPreservesMembersAndRejectsMixedSelectionBeforeRemovingAnything() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val board = f.canvases.create("Board")
            val group = f.canvases.addGroupNode(board, 0f, 0f, "Group")
            val card = f.canvases.addTextNode(board, 20f, 20f, "Member")
            f.canvases.connect(board, group, card, "group")
            val before = f.canvases.observe(board).first()!!
            val observed = version(f, board)
            assertTrue(call(f, board, "halo_canvas_ungroup", mapOf("nodeIds" to listOf(group, card))) is ToolExecutionResult.Rejected)
            assertEquals(before, f.canvases.observe(board).first())
            assertEquals(observed, version(f, board))
            saved(f, board, "halo_canvas_ungroup", mapOf("nodeIds" to listOf(group)))
            val after = f.canvases.observe(board).first()!!
            assertEquals(card, after.nodes.single().id)
            assertTrue(after.edges.isEmpty())
        }
    }

    @Test fun olderEditorUndoCannotRestoreCardsAfterAnAssistantRemoval() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val board = f.canvases.create("Board")
            val card = f.canvases.addTextNode(board, 10f, 20f, "Remove")
            val keep = f.canvases.addTextNode(board, 50f, 60f, "Keep")
            val editorUndo = f.canvases.manualEdit(board, setOf(card, keep), "Arrange")
            saved(f, board, "halo_canvas_delete", mapOf("nodeIds" to listOf(card)))
            assertTrue(runCatching { f.canvases.undo(board, editorUndo) }.isFailure)
            assertEquals(keep, f.canvases.observe(board).first()!!.nodes.single().id)
        }
    }

    @Test fun groupMoveUsesSpatialFallbackWithoutMovingOutsideOrNestedGroupCards() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val board = f.canvases.create("Spatial")
            val group = f.canvases.addGroupNode(board, 0f, 0f, "Group")
            val inside = f.canvases.addTextNode(board, 10f, 20f, "Inside")
            val outside = f.canvases.addTextNode(board, 1000f, 1000f, "Outside")
            val nested = f.canvases.addGroupNode(board, 20f, 20f, "Nested")
            saved(f, board, "halo_canvas_move", mapOf("nodeId" to group, "x" to 100, "y" to 200))
            val positions = f.canvases.observe(board).first()!!.nodes.associate { it.id to (it.x to it.y) }
            assertEquals(100f to 200f, positions[group])
            assertEquals(110f to 220f, positions[inside])
            assertEquals(1000f to 1000f, positions[outside])
            assertEquals(20f to 20f, positions[nested])
        }
    }

    @Test fun explicitGroupMembershipOverridesGeometryAndStaleMoveCannotMoveMembersTwice() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val board = f.canvases.create("Explicit")
            val group = f.canvases.addGroupNode(board, 0f, 0f, "Group")
            val inside = f.canvases.addTextNode(board, 10f, 20f, "Unlinked inside")
            val member = f.canvases.addTextNode(board, 900f, 900f, "Explicit member")
            val edge = f.canvases.connect(board, group, member, "group")
            val observed = version(f, board)
            val args = mapOf("nodeId" to group, "x" to 200, "y" to 300)
            saved(f, board, "halo_canvas_move", args)
            val after = f.canvases.observe(board).first()!!
            assertEquals(200f to 300f, after.nodes.single { it.id == group }.let { it.x to it.y })
            assertEquals(1100f to 1200f, after.nodes.single { it.id == member }.let { it.x to it.y })
            assertEquals(10f to 20f, after.nodes.single { it.id == inside }.let { it.x to it.y })
            assertEquals(edge, after.edges.single().id)
            assertTrue(call(f, board, "halo_canvas_move", args, observed) is ToolExecutionResult.Rejected)
            assertEquals(after, f.canvases.observe(board).first())
        }
    }

    @Test fun everyAddedOperationRejectsStaleBoardWithoutChangingAnyPersistedData() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val board = f.canvases.create("Board")
            val stale = version(f, board)
            val card = f.canvases.addTextNode(board, 0f, 0f, "Stay")
            val group = f.canvases.addGroupNode(board, 0f, 0f, "Stay group")
            val edge = f.canvases.connect(board, group, card, "group")
            val requests = mapOf(
                "halo_canvas_rename" to mapOf("title" to "Wrong"),
                "halo_canvas_add_group" to mapOf("title" to "Wrong", "x" to 0, "y" to 0),
                "halo_canvas_tag" to mapOf("nodeIds" to listOf(card), "label" to "Wrong"),
                "halo_canvas_duplicate" to mapOf("nodeId" to card),
                "halo_canvas_arrange" to mapOf("nodeIds" to listOf(card), "operation" to "GRID"),
                "halo_canvas_delete" to mapOf("nodeIds" to listOf(card)),
                "halo_canvas_ungroup" to mapOf("nodeIds" to listOf(group)),
                "halo_canvas_disconnect" to mapOf("edgeId" to edge))
            val before = f.canvases.observe(board).first()!!
            val current = version(f, board)
            for ((name, args) in requests) {
                assertTrue(name, call(f, board, name, args, stale) is ToolExecutionResult.Rejected)
                assertTrue(name, call(f, board, name, args + ("unexpected" to "value")) is ToolExecutionResult.Rejected)
                assertEquals(before, f.canvases.observe(board).first())
                assertEquals(current, version(f, board))
            }
            f.workspace.trash(com.halo.app.data.notebook.WorkspaceObjectId(board))
            val trashedNodes = f.database.notebookCanvasDao().getNodes(board)
            val trashedEdges = f.database.notebookCanvasDao().getEdges(board)
            for ((name, args) in requests) {
                assertTrue(name, call(f, board, name, args) is ToolExecutionResult.Rejected)
                assertEquals(trashedNodes, f.database.notebookCanvasDao().getNodes(board))
                assertEquals(trashedEdges, f.database.notebookCanvasDao().getEdges(board))
            }
        }
    }

    @Test fun malformedForeignAndDuplicateSelectionsFailAtomicallyForEverySelectionEdit() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val board = f.canvases.create("Board")
            val group = f.canvases.addGroupNode(board, 0f, 0f, "Keep")
            val foreignBoard = f.canvases.create("Other")
            val foreign = f.canvases.addGroupNode(foreignBoard, 0f, 0f, "Foreign")
            val before = f.canvases.observe(board).first()!!
            val observed = version(f, board)
            val badSelections = listOf(emptyList<Any>(), listOf(group, group), listOf(group, foreign), listOf(group, "missing"), listOf(17), List(101) { "card-$it" }, listOf("../file"))
            for (name in listOf("halo_canvas_tag", "halo_canvas_arrange", "halo_canvas_delete", "halo_canvas_ungroup")) {
                val extras: Map<String, Any> = when (name) {
                    "halo_canvas_tag" -> mapOf("label" to "Tag")
                    "halo_canvas_arrange" -> mapOf("operation" to "GRID")
                    else -> emptyMap()
                }
                for (ids in badSelections) {
                    assertTrue(name, call(f, board, name, extras + ("nodeIds" to ids)) is ToolExecutionResult.Rejected)
                    assertEquals(before, f.canvases.observe(board).first())
                    assertEquals(observed, version(f, board))
                }
            }
            assertTrue(call(f, board, "halo_canvas_arrange", mapOf("nodeIds" to listOf(group), "operation" to "DELETE")) is ToolExecutionResult.Rejected)
            assertTrue(call(f, board, "halo_canvas_rename", mapOf("title" to " ")) is ToolExecutionResult.Rejected)
            assertTrue(call(f, board, "halo_canvas_add_group", mapOf("title" to "Group", "x" to Double.NaN, "y" to 0)) is ToolExecutionResult.Rejected)
            assertTrue(call(f, board, "halo_canvas_tag", mapOf("nodeIds" to listOf(group), "label" to "###")) is ToolExecutionResult.Rejected)
            assertTrue(call(f, board, "halo_canvas_duplicate", mapOf("nodeId" to foreign)) is ToolExecutionResult.Rejected)
            assertTrue(call(f, board, "halo_canvas_disconnect", mapOf("edgeId" to "missing")) is ToolExecutionResult.Rejected)
            assertEquals(before, f.canvases.observe(board).first())
            assertEquals(observed, version(f, board))
        }
    }
}
