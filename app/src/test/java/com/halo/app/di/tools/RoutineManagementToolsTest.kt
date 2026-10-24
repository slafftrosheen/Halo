package com.halo.app.di.tools

import android.app.Application
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.halo.app.automation.*
import com.halo.app.data.AppDatabase
import com.halo.app.data.routine.*
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.routine.*
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class RoutineManagementToolsTest {
    private val authority = ToolExecutionContext("approved-interaction", false)
    private fun call(name: String, args: Map<String, Any> = emptyMap()) = ToolCall("test", name, args)
    private fun json(result: ToolExecutionResult) = JSONObject((result as ToolExecutionResult.Success).output)
    private class Fixture {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        val repository = RoomRoutineRepository(db.routineDao())
        val scheduled = mutableListOf<Routine>()
        val cancelled = mutableListOf<RoutineId>()
        val dispatched = mutableListOf<RoutineOccurrence>()
        var allowed = true
        var policyBlocked = false
        var validationCalls = 0
        var failSchedule = false
        var failDispatch = false
        val scheduler = object : RoutineScheduler {
            override suspend fun schedule(routine: Routine): RoutineScheduleResult {
                scheduled += routine
                if (failSchedule) throw java.io.IOException("Schedule outcome unknown")
                return if (routine.enabled) RoutineScheduleResult.EventDriven else RoutineScheduleResult.Blocked("Disabled")
            }
            override suspend fun cancel(routineId: RoutineId) { cancelled += routineId }
            override suspend fun reconcile() = Unit
        }
        val now = Instant.parse("2026-10-25T00:00:00Z")
        private fun validate(r: Routine) {
            validationCalls++
            require(!policyBlocked && r.actions.all { it.toolId == "halo_routine_message" }) { "Constituent action blocked by canonical policy" }
            require(r.actions.isNotEmpty() && r.name.isNotBlank())
        }
        val owner = RoutineManagementActions(repository, scheduler, ::validate, ::validate,
            { "Review exact saved actions: ${it.actions.size}" }, { occurrence ->
                dispatched += occurrence
                if (failDispatch) throw java.io.IOException("Run claim outcome unknown")
                repository.dao.insertRun(RoutineRunEntity(occurrence.id, occurrence.routineId.value, "agent-fixture",
                    occurrence.occurredAt.toEpochMilli(), "RUNNING", source = "MANUAL", actionsTotal = 1))
                "DISPATCHED"
            }, { now })
        val tools = RoutineManagementTools(owner) { allowed }
        suspend fun save(id: String = "routine-1", grant: Boolean = true): Routine {
            val draft = Routine(RoutineId(id), "Saved automation", true, RoutineTrigger.NetworkAvailable,
                actions = listOf(RoutineAction("halo_routine_message", "{\"message\":\"hello\"}")), createdAt = now, updatedAt = now)
            val saved = if (grant) draft.copy(grantDigest = RoutineArguments.grant(draft)) else draft
            repository.upsert(saved); return saved
        }
        suspend fun exact(id: String = "routine-1"): Map<String, Any> = mapOf("routineId" to id,
            "sourceVersion" to requireNotNull(owner.get(RoutineId(id))).sourceVersion)
        suspend fun edit(id: String = "routine-1"): Map<String, Any> {
            val value = requireNotNull(repository.get(RoutineId(id))); val codec = RoutineCodec()
            return exact(id) + mapOf("name" to value.name, "enabled" to value.enabled,
                "trigger" to codec.encodeTrigger(value.trigger), "conditions" to codec.encodeConditions(value.conditions),
                "actions" to codec.encodeActions(value.actions), "delivery" to codec.encodeDelivery(value.delivery),
                "minimumIntervalSeconds" to value.minimumIntervalSeconds)
        }
    }
    private suspend fun fixture(block: suspend Fixture.() -> Unit) { val f = Fixture(); try { f.block() } finally { f.db.close() } }

    @Test fun noContextAndForegroundGrownupGateCannotMutateButApprovedSensitiveContextWorks() = runBlocking { fixture {
        save(); val args = exact() + ("enabled" to false)
        assertTrue(tools.execute(call("halo_routine_set_enabled", args)) is ToolExecutionResult.Rejected)
        allowed = false
        assertTrue(tools.execute(call("halo_routine_set_enabled", args), authority) is ToolExecutionResult.Rejected)
        assertTrue(repository.get(RoutineId("routine-1"))!!.enabled)
        allowed = true
        assertTrue(tools.execute(call("halo_routine_set_enabled", args), authority) is ToolExecutionResult.Success)
        assertFalse(repository.get(RoutineId("routine-1"))!!.enabled)
        assertEquals(listOf(RoutineId("routine-1")), cancelled)
    } }

    @Test fun exactSourceCannotTargetAnotherRoutineAndRoomCasRejectsConcurrentSavedRowChange() = runBlocking { fixture {
        save(); save("routine-2"); val args = exact()
        assertTrue(tools.execute(call("halo_routine_delete", args + ("routineId" to "routine-2")), authority) is ToolExecutionResult.Rejected)
        val row = repository.dao.get("routine-1")!!
        repository.dao.nextRun("routine-1", now.plusSeconds(60).toEpochMilli())
        assertFalse(repository.dao.compareAndSet(row, row.copy(name = "must not overwrite")))
        assertTrue(tools.execute(call("halo_routine_delete", args), authority) is ToolExecutionResult.Rejected)
        assertEquals(2, repository.managementList().size)
        assertEquals("Saved automation", repository.get(RoutineId("routine-1"))!!.name)
    } }

    @Test fun optionalCommitAwaitsPersistenceAndRoomRollbackPreservesSourceWithoutScheduling() = runBlocking { fixture {
        val original = save()
        val before = requireNotNull(owner.get(original.id))
        val edited = original.copy(name = "Reviewed edit")
        var callbacks = 0
        try {
            owner.save(edited, before.sourceVersion) { persist ->
                db.withTransaction {
                    callbacks++
                    persist()
                    assertEquals("Reviewed edit", repository.dao.get(original.id.value)!!.name)
                    throw IllegalStateException("Review decision rolled back")
                }
            }
            fail("The review transaction must propagate rollback")
        } catch (expected: IllegalStateException) {
            assertEquals("Review decision rolled back", expected.message)
        }
        assertEquals(original, repository.get(original.id))
        assertEquals(before.sourceVersion, owner.get(original.id)!!.sourceVersion)
        assertTrue(scheduled.isEmpty())

        val fresh = original.copy(id = RoutineId("new-reviewed"))
        try {
            owner.save(fresh, commit = { persist ->
                db.withTransaction {
                    callbacks++
                    persist()
                    assertNotNull(repository.dao.get(fresh.id.value))
                    throw IllegalStateException("New review rolled back")
                }
            })
            fail("Creation must participate in the same review transaction")
        } catch (expected: IllegalStateException) {
            assertEquals("New review rolled back", expected.message)
        }
        assertNull(repository.get(fresh.id))
        assertEquals(before.sourceVersion, owner.get(original.id)!!.sourceVersion)
        assertTrue(scheduled.isEmpty())

        assertNotNull(owner.save(edited, before.sourceVersion) { persist ->
            db.withTransaction { callbacks++; persist() }
        })
        assertEquals("Reviewed edit", repository.get(original.id)!!.name)
        assertEquals(1, scheduled.size)
        assertNull(owner.save(edited, before.sourceVersion) { persist -> callbacks++; persist() })
        assertEquals(3, callbacks)
    } }

    @Test fun fullEditSharesUiOwnerPolicyGrantAndStrictCodecWithoutApprovingExtraActions() = runBlocking { fixture {
        val original = save()
        val args = edit() + ("name" to "Edited automation")
        assertTrue(tools.grantSummary(call("halo_routine_save_existing", args)).contains("Review exact saved actions"))
        assertEquals(0, scheduled.size)
        assertTrue(tools.execute(call("halo_routine_save_existing", args), authority) is ToolExecutionResult.Success)
        val saved = repository.get(original.id)!!
        assertEquals("Edited automation", saved.name); assertEquals(original.createdAt, saved.createdAt)
        assertTrue(saved.updatedAt.isAfter(original.updatedAt)); assertEquals(RoutineArguments.grant(saved), saved.grantDigest)
        assertEquals(saved.id, scheduled.single().id)
        assertTrue(tools.execute(call("halo_routine_save_existing", args), authority) is ToolExecutionResult.Rejected)
        val current = edit()
        val hiddenTrigger = "{\"type\":\"network_available\",\"approvePaidActions\":true}"
        assertTrue(tools.execute(call("halo_routine_save_existing", current + ("trigger" to hiddenTrigger)), authority) is ToolExecutionResult.Rejected)
        assertTrue(tools.execute(call("halo_routine_save_existing", current + ("actions" to "[{\"tool\":\"paid_action\",\"arguments\":{}}]")), authority) is ToolExecutionResult.Rejected)
        assertEquals(1, scheduled.size); assertTrue(validationCalls > 0)
    } }

    @Test fun enablingAndRunningRequireExistingGrantAndCurrentConstituentPolicy() = runBlocking { fixture {
        save(grant = false)
        assertTrue(tools.execute(call("halo_routine_set_enabled", exact() + ("enabled" to true)), authority) is ToolExecutionResult.Rejected)
        assertTrue(tools.execute(call("halo_routine_run_now", exact()), authority) is ToolExecutionResult.Rejected)
        assertNull(repository.get(RoutineId("routine-1"))!!.grantDigest)
        save(); policyBlocked = true
        assertTrue(tools.execute(call("halo_routine_run_now", exact()), authority) is ToolExecutionResult.Rejected)
        assertTrue(tools.execute(call("halo_routine_save_existing", edit()), authority) is ToolExecutionResult.Rejected)
        assertTrue(dispatched.isEmpty()); assertTrue(scheduled.isEmpty())
    } }

    @Test fun manualRunUsesExistingOccurrenceOwnerConsumesSourceAndReportsDispatchNotCompletion() = runBlocking { fixture {
        val saved = save(); val args = exact()
        val result = tools.execute(call("halo_routine_run_now", args), authority) as ToolExecutionResult.Success
        val body = json(result)
        assertEquals(ActionTruth.DISPATCHED, result.actionTruth)
        assertFalse(body.getBoolean("completionConfirmed")); assertFalse(body.getBoolean("deliveryConfirmed"))
        val occurrence = dispatched.single()
        assertEquals(saved.updatedAt, occurrence.routineRevision); assertEquals(saved.id, occurrence.routineId)
        assertEquals(occurrence.id, body.getString("runId")); assertTrue(body.getBoolean("runRecorded"))
        assertTrue(tools.execute(call("halo_routine_run_now", args), authority) is ToolExecutionResult.Rejected)
        val status = json(tools.execute(call("halo_routine_run_status", mapOf("runId" to occurrence.id))))
        assertEquals("RUNNING", status.getString("recordedState")); assertEquals(0, status.getInt("actionsCompleted"))
        assertFalse(status.has("output")); assertEquals(1, dispatched.size)
    } }

    @Test fun malformedDefinitionCanBeReadDisabledAndDeletedWithoutObserverMutation() = runBlocking { fixture {
        save(); val row = repository.dao.get("routine-1")!!
        repository.dao.upsert(row.copy(triggerJson = "{\"type\":\"unsupported\"}"))
        val read = json(tools.execute(call("halo_routine_get", mapOf("routineId" to "routine-1"))))
        assertFalse(read.getBoolean("editable")); assertTrue(repository.dao.get("routine-1")!!.enabled)
        assertTrue(tools.execute(call("halo_routine_set_enabled", exact() + ("enabled" to false)), authority) is ToolExecutionResult.Success)
        assertFalse(repository.dao.get("routine-1")!!.enabled)
        assertTrue(tools.execute(call("halo_routine_delete", exact()), authority) is ToolExecutionResult.Success)
        assertNull(repository.dao.get("routine-1"))
    } }

    @Test fun uncertainRunOrSchedulerOutcomesNeverBecomeCompletedSuccessAndCannotBlindlyReplay() = runBlocking { fixture {
        save(); val args = exact(); failDispatch = true
        val failed = tools.execute(call("halo_routine_run_now", args), authority) as ToolExecutionResult.Failed
        assertTrue(failed.completionUnknown)
        assertTrue(tools.execute(call("halo_routine_run_now", args), authority) is ToolExecutionResult.Rejected)
        assertEquals(1, dispatched.size)
        failSchedule = true
        val edit = edit() + ("name" to "Persisted before schedule failure")
        val scheduleFailure = tools.execute(call("halo_routine_save_existing", edit), authority) as ToolExecutionResult.Failed
        assertTrue(scheduleFailure.completionUnknown)
        assertEquals("Persisted before schedule failure", repository.get(RoutineId("routine-1"))!!.name)
        assertTrue(tools.execute(call("halo_routine_save_existing", edit), authority) is ToolExecutionResult.Rejected)
    } }

    @Test fun sourceAuthorityExpiresOnOwnerRestartAndConcurrentMutationHasOneWinner() = runBlocking { fixture {
        save(); val version = exact()["sourceVersion"] as String
        val restarted = RoomRoutineRepository(repository.dao)
        assertFalse(restarted.managementDelete(RoutineId("routine-1"), version))
        val results = coroutineScope {
            listOf(async { owner.setEnabled(RoutineId("routine-1"), false, version) },
                async { owner.delete(RoutineId("routine-1"), version) }).awaitAll()
        }
        assertEquals(1, results.count { it != null })
    } }

    @Test fun largeDefinitionPagesExactlyBelowOutputBudgetAndPartialEditPreservesOmittedFields() = runBlocking { fixture {
        val initial = save()
        val actions = List(10) { RoutineAction("halo_routine_message", JSONObject().put("message", "😀".repeat(400)).toString()) }
        val large = initial.copy(actions = actions).let { it.copy(grantDigest = RoutineArguments.grant(it)) }
        repository.upsert(large)
        val summary = json(tools.execute(call("halo_routine_get", mapOf("routineId" to large.id.value))))
        assertFalse(summary.has("actions")); assertTrue(summary.getJSONObject("sections").getInt("actions") > 8192)
        val source = summary.getString("sourceVersion")
        val restored = StringBuilder()
        var offset = 0
        do {
            val result = tools.execute(call("halo_routine_get", mapOf("routineId" to large.id.value,
                "sourceVersion" to source, "section" to "actions", "offset" to offset))) as ToolExecutionResult.Success
            assertTrue("A page must fit the normal output limit without truncation", result.output.length < 4000)
            val page = json(result); val chunk = page.getString("text")
            assertFalse(chunk.firstOrNull()?.isLowSurrogate() == true); assertFalse(chunk.lastOrNull()?.isHighSurrogate() == true)
            restored.append(chunk)
            offset = if (page.isNull("nextOffset")) -1 else page.getInt("nextOffset")
        } while (offset >= 0)
        assertEquals(RoutineCodec().encodeActions(actions), restored.toString())
        val edited = tools.execute(call("halo_routine_save_existing", mapOf("routineId" to large.id.value,
            "sourceVersion" to source, "name" to "Renamed large automation")), authority)
        assertTrue(edited is ToolExecutionResult.Success)
        val saved = repository.get(large.id)!!
        assertEquals(actions, saved.actions); assertEquals(large.trigger, saved.trigger); assertEquals(large.delivery, saved.delivery)
        assertEquals("Renamed large automation", saved.name)
        assertTrue(tools.execute(call("halo_routine_get", mapOf("routineId" to large.id.value,
            "sourceVersion" to source, "section" to "actions"))) is ToolExecutionResult.Rejected)
    } }

    @Test fun actualDescriptorsPreserveBothChannelsApprovalReplayAndClosedVersionedContracts() = runBlocking { fixture {
        val definitions = tools.definitions()
        assertEquals(7, definitions.size)
        val descriptors = definitions.map { definition ->
            val descriptor = requireNotNull(HaloToolDescriptors.assistant(definition))
            val contract = requireNotNull(RoutineManagementOperability.forDefinition(definition))
            assertEquals(contract, descriptor.operability); assertEquals(ToolInputChannel.entries.toSet(), contract.channels)
            assertEquals(false, definition.parameters["additionalProperties"])
            if (contract.operation == ToolOperation.EDIT) {
                assertEquals(setOf("sourceVersion"), contract.sourceVersionArguments)
                assertEquals(ConfirmationPolicy.ALWAYS, descriptor.security.confirmation)
                assertEquals(ReplaySafety.NEVER_REPLAY, descriptor.security.replaySafety)
            }
            descriptor
        }
        assertEquals(emptyList<ToolOperabilityProblem>(), ToolOperabilityReview.inspect(
            listOf(ToolFeatureOperability("routine-management", definitions.map { it.name }.toSet())), descriptors))
    } }
}
