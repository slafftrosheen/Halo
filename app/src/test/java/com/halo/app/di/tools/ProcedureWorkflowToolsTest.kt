package com.halo.app.di.tools

import com.halo.app.data.procedure.ProcedureContracts
import com.halo.runtime.cognition.procedures.*
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Written for the deferred gate. Not executed in the owner's code-only pass. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class ProcedureWorkflowToolsTest {
    private fun descriptor() = ToolDescriptor(ToolId("halo_note_rename"), ToolDefinition(
        "halo_note_rename", "halo_note_rename", "Rename the exact note", mapOf(
            "type" to "object", "properties" to mapOf("noteId" to mapOf("type" to "string"),
                "revision" to mapOf("type" to "integer"), "title" to mapOf("type" to "string")),
            "required" to listOf("noteId", "revision", "title"), "additionalProperties" to false)),
        ToolCategory.WORKSPACE, security = ToolSecurityPolicy.REVERSIBLE)

    @Test fun namedInputsBindWholeValuesAndContractChangesInvalidateThem() {
        val descriptor = descriptor()
        val step = ProcedureContracts.step(JSONObject().put("capabilityId", descriptor.name)
            .put("arguments", JSONObject().put("title", "literal ${'$'}{note}"))
            .put("inputBindings", JSONObject().put("noteId", "note").put("revision", "revision")), descriptor)
        val declarations = listOf(ProcedureInput("note", "Current note"), ProcedureInput("revision", "Current revision", "integer"))
        val bound = ProcedureContracts.bind(step, mapOf("note" to "exact-id", "revision" to "7"), descriptor, declarations)!!
        assertEquals("exact-id", bound["noteId"])
        assertEquals(7L, bound["revision"])
        assertEquals("literal ${'$'}{note}", bound["title"])
        try {
            ProcedureContracts.bind(step, mapOf("note" to "exact-id", "revision" to "7"),
                descriptor.copy(security = ToolSecurityPolicy.DESTRUCTIVE), declarations)
            fail("Changed policy must require review")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun malformedBindingsAndNullArgumentsAreNeverSavedAsTemplates() {
        for (item in listOf(
            JSONObject().put("capabilityId", "halo_note_rename").put("arguments", JSONObject().put("title", "literal"))
                .put("inputBindings", JSONObject().put("title", "title")),
            JSONObject().put("capabilityId", "halo_note_rename").put("arguments", JSONObject().put("title", JSONObject.NULL)))) {
            try { ProcedureContracts.step(item, descriptor()); fail("Invalid template accepted") }
            catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun typedPreviewRevalidatesAndNeverDispatchesDomainActions() = runBlocking {
        val store = InMemoryLearnedProcedureStore()
        var current = descriptor()
        var validations = 0
        val tools = ProcedureToolExecutor(store, { it == current.name }, newId = { "reviewed" }, now = { 10L },
            descriptorFor = { current }, validateTemplate = { _, _ -> null }, validateCall = { call ->
                validations++
                assertEquals(7L, call.arguments["revision"])
                null
            })
        val proposed = tools.execute(ToolCall("p", "halo_propose_procedure", mapOf("name" to "Rename my note",
            "inputs" to listOf(mapOf("name" to "note", "description" to "Current note"),
                mapOf("name" to "revision", "description" to "Current revision", "valueType" to "integer")),
            "steps" to listOf(mapOf("capabilityId" to current.name, "intent" to "Rename the note",
                "arguments" to mapOf("title" to "Reviewed title"), "inputBindings" to mapOf("noteId" to "note", "revision" to "revision"))))))
        assertTrue(proposed is ToolExecutionResult.Success)
        assertTrue(store.all().isEmpty())
        val review = (proposed as ToolExecutionResult.Success).output.substringAfter("reviewSummary:\n").substringBefore("WAITING_FOR_USER_CONFIRMATION:")
        assertTrue(tools.execute(ToolCall("s", "halo_save_procedure", mapOf("proposalId" to "reviewed", "reviewSummary" to review))) is ToolExecutionResult.Success)
        val use = ToolCall("u", "halo_use_procedure", mapOf("procedureId" to "reviewed", "sourceVersion" to ProcedureContracts.version(store.get("reviewed")!!), "inputs" to mapOf("note" to "exact-note", "revision" to "7")))
        assertTrue(tools.execute(use) is ToolExecutionResult.Success)
        assertEquals(1, validations)
        assertEquals(1L, store.get("reviewed")!!.usageCount)
        current = current.copy(security = ToolSecurityPolicy.DESTRUCTIVE)
        assertTrue(tools.execute(use) is ToolExecutionResult.Rejected)
        assertEquals(1, validations)
        assertEquals(1L, store.get("reviewed")!!.usageCount)
    }

    @Test fun enableAndDeleteUseExactVersionAndAtomicOwner() = runBlocking {
        val original = LearnedProcedure("exact", "Recipe", "", emptyList(), listOf(ProcedureStep("halo_note_rename", "Rename")),
            ProcedureOrigin.USER_CREATED, 1L, 1L)
        val store = InMemoryLearnedProcedureStore(listOf(original))
        val tools = ProcedureToolExecutor(store, { true }, now = { 1L })
        val version = ProcedureContracts.version(original)
        assertTrue(tools.execute(ToolCall("e", "halo_procedure_set_enabled", mapOf("procedureId" to "exact", "sourceVersion" to version, "enabled" to false))) is ToolExecutionResult.Success)
        assertFalse(store.get("exact")!!.enabled)
        assertTrue(tools.execute(ToolCall("d", "halo_procedure_delete", mapOf("procedureId" to "exact", "sourceVersion" to version))) is ToolExecutionResult.Rejected)
        assertNotNull(store.get("exact"))
        val changed = ProcedureContracts.version(store.get("exact")!!)
        assertTrue(tools.execute(ToolCall("d2", "halo_procedure_delete", mapOf("procedureId" to "exact", "sourceVersion" to changed))) is ToolExecutionResult.Success)
        assertNull(store.get("exact"))
    }
}
