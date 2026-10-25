package com.halo.app.di.tools

import com.halo.runtime.cognition.procedures.*
import com.halo.runtime.cognition.tools.ToolExecutionResult
import com.halo.sdk.provider.ToolCall
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Deferred regression sources; these cases have not run during the code-only pass. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class ProcedureAdmissionTest {
    private fun recipe(id: String, name: String) = LearnedProcedure(id, name, "", emptyList(),
        listOf(ProcedureStep("halo_note_create", "Create a reviewed note")), ProcedureOrigin.USER_CREATED, 1L, 1L)

    @Test fun ambiguousNameAndIdPrefixCannotChooseOrCountAnArbitraryRecipe() = runBlocking {
        val store = InMemoryLearnedProcedureStore(listOf(recipe("first-exact", "My recipe"), recipe("second-exact", "MY RECIPE")))
        val tools = ProcedureToolExecutor(store, { true })
        for (arguments in listOf(mapOf("name" to "My recipe"), mapOf("procedureId" to "first"),
            mapOf("procedureId" to "first-exact", "name" to "MY RECIPE"))) {
            assertTrue(tools.execute(ToolCall("read", "halo_use_procedure", arguments)) is ToolExecutionResult.Rejected)
        }
        assertTrue(store.all().all { it.usageCount == 0L })
        assertTrue(tools.execute(ToolCall("exact", "halo_use_procedure", mapOf("procedureId" to "first-exact"))) is ToolExecutionResult.Success)
        assertEquals(1L, store.get("first-exact")!!.usageCount)
        assertEquals(0L, store.get("second-exact")!!.usageCount)
    }

    @Test fun removedActionDoesNotCountUsageOrDeleteTheReviewedRecipe() = runBlocking {
        val store = InMemoryLearnedProcedureStore(listOf(recipe("exact", "My recipe")))
        val tools = ProcedureToolExecutor(store, { false })
        assertTrue(tools.execute(ToolCall("read", "halo_use_procedure", mapOf("procedureId" to "exact"))) is ToolExecutionResult.Rejected)
        assertEquals(0L, store.get("exact")!!.usageCount)
        assertEquals(1, store.all().size)
    }

    @Test fun actionRemovedAfterProposalCannotBeSavedAsIfStillKnown() = runBlocking {
        val store = InMemoryLearnedProcedureStore()
        var known = true
        val tools = ProcedureToolExecutor(store, { known }, newId = { "proposal" }, now = { 1000L })
        assertTrue(tools.execute(ToolCall("propose", "halo_propose_procedure", mapOf(
            "name" to "My recipe", "steps" to listOf(mapOf("capabilityId" to "halo_note_create"))))) is ToolExecutionResult.Success)
        known = false
        assertTrue(tools.execute(ToolCall("save", "halo_save_procedure", mapOf("proposalId" to "proposal"))) is ToolExecutionResult.Rejected)
        assertTrue(store.all().isEmpty())
    }
}
