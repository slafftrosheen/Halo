package com.halo.app.data.workspace

import com.halo.app.di.tools.WorkspaceFileEditingActions
import com.halo.app.di.tools.HaloToolDescriptors
import com.halo.runtime.cognition.tools.ToolExecutionResult
import com.halo.runtime.cognition.tools.WorkspaceToolAccess
import com.halo.runtime.cognition.agent.ActionTruth
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
class WorkspaceFileEditingActionsTest {
    @Test fun everyOperationHasCanonicalPolicyAndWorkspaceContentBoundary() {
        WorkspaceFileToolsFixture().use { f ->
            val definitions = WorkspaceFileEditingActions(f.tools).definitions()
            assertEquals(18, definitions.size)
            assertEquals(18, definitions.map { it.name }.distinct().size)
            definitions.forEach { definition ->
                assertNotNull(HaloToolDescriptors.file(definition))
                assertTrue(definition.name in WorkspaceToolAccess.readTools)
            }
        }
    }
    @Test fun sharedActionsUseExactRevisionBoundedReadsAndDispatchTruth() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val actions = WorkspaceFileEditingActions(f.tools)
            suspend fun call(name: String, args: Map<String, Any> = emptyMap()) = actions.execute(ToolCall("fixture", name, args))
            val created = call("halo_text_create", mapOf("title" to "Action draft", "format" to "TXT", "text" to "x".repeat(5000)))
            assertTrue(created is ToolExecutionResult.Success)
            val note = f.tools.workspace.observe().first().single()
            val current = f.text.get(note.id.value)!!
            val read = call("halo_text_get", mapOf("noteId" to current.id, "length" to 20)) as ToolExecutionResult.Success
            val payload = JSONObject(read.output)
            assertEquals(20, payload.getString("text").length)
            assertEquals(5000, payload.getInt("totalChars"))
            assertTrue(payload.getBoolean("hasMore"))
            val edited = call("halo_text_edit", mapOf("noteId" to current.id, "revision" to current.revision,
                "operation" to "replace", "fields" to mapOf("text" to "new")))
            assertTrue(edited is ToolExecutionResult.Success)
            assertTrue(call("halo_text_edit", mapOf("noteId" to current.id, "revision" to current.revision,
                "operation" to "replace", "fields" to mapOf("text" to "stale"))) is ToolExecutionResult.Rejected)
            assertTrue(call("halo_file_tools_open", mapOf("objectId" to current.id, "url" to "https://invalid")) is ToolExecutionResult.Rejected)
            val opened = call("halo_file_tools_open", mapOf("objectId" to current.id)) as ToolExecutionResult.Success
            assertEquals(ActionTruth.DISPATCHED, opened.actionTruth)
            assertEquals("new", f.text.get(current.id)!!.text)
        }
    }
}
