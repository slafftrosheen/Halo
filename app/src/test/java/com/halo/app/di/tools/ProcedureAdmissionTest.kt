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

    @Test fun generatedIdentityCollisionNeverReplacesExistingReviewedWorkflow() = runBlocking {
        val original = recipe("fixed-id", "Owner-reviewed original")
        val store = InMemoryLearnedProcedureStore(listOf(original))
        val tools = ProcedureToolExecutor(store, { true }, newId = { "fixed-id" }, now = { 100L })
        assertTrue(tools.execute(ToolCall("p", "halo_propose_procedure", mapOf(
            "name" to "Replacement attempt",
            "steps" to listOf(mapOf("capabilityId" to "halo_note_create"))
        ))) is ToolExecutionResult.Success)
        assertTrue(tools.execute(ToolCall("s", "halo_save_procedure", mapOf(
            "proposalId" to "fixed-id"
        ))) is ToolExecutionResult.Rejected)
        assertEquals(original, store.get("fixed-id"))
        assertEquals(1, store.all().size)
    }

    @Test fun atomicSaveRefusesIdentityThatAppearsBetweenPreflightAndInsert() = runBlocking {
        val original = recipe("fixed-id", "Owner-reviewed original")
        val delegate = InMemoryLearnedProcedureStore()
        val race = object : LearnedProcedureStore by delegate {
            override suspend fun insertIfAbsent(procedure: LearnedProcedure): Boolean {
                delegate.insertIfAbsent(original)
                return delegate.insertIfAbsent(procedure)
            }
        }
        val tools = ProcedureToolExecutor(race, { true }, newId = { "fixed-id" }, now = { 100L })
        assertTrue(tools.execute(ToolCall("p", "halo_propose_procedure", mapOf(
            "name" to "New proposal",
            "steps" to listOf(mapOf("capabilityId" to "halo_note_create"))
        ))) is ToolExecutionResult.Success)
        assertTrue(tools.execute(ToolCall("s", "halo_save_procedure", mapOf(
            "proposalId" to "fixed-id"
        ))) is ToolExecutionResult.Rejected)
        assertEquals(original, delegate.get("fixed-id"))
        assertEquals(1, delegate.all().size)
    }

    @Test fun collidingPendingProposalCannotReplaceAnEarlierReview() = runBlocking {
        val store = InMemoryLearnedProcedureStore()
        val tools = ProcedureToolExecutor(store, { true }, newId = { "same-id" }, now = { 100L })
        fun propose(name: String) = ToolCall("p", "halo_propose_procedure", mapOf(
            "name" to name, "steps" to listOf(mapOf("capabilityId" to "halo_note_create"))
        ))
        assertTrue(tools.execute(propose("First approved review")) is ToolExecutionResult.Success)
        assertTrue(tools.execute(propose("Second different review")) is ToolExecutionResult.Rejected)
        assertTrue(tools.execute(ToolCall("s", "halo_save_procedure", mapOf(
            "proposalId" to "same-id"
        ))) is ToolExecutionResult.Success)
        assertEquals("First approved review", store.get("same-id")!!.name)
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
