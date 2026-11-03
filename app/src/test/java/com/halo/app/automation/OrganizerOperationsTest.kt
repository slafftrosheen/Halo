package com.halo.app.automation

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import com.halo.app.data.agent.RoomAgentRunRepository
import com.halo.app.data.everyday.*
import com.halo.app.data.routine.RoomRoutineRepository
import com.halo.app.di.tools.*
import com.halo.app.everyday.collectors.*
import com.halo.app.everyday.briefing.*
import com.halo.app.daily.DailyAssistantObservation
import com.halo.app.everyday.organizer.*
import com.halo.runtime.cognition.agent.*
import com.halo.runtime.cognition.attention.*
import com.halo.runtime.cognition.device.*
import com.halo.runtime.cognition.routine.*
import com.halo.runtime.cognition.tools.*
import com.halo.runtime.kernel.resource.DefaultResourceLeaseManager
import com.halo.sdk.provider.ToolCall
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = Application::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class OrganizerOperationsTest {
    private class Harness(private val capabilityFactory: ((Harness) -> OrganizerWorkCapability)? = null) : AutoCloseable {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        val organizer = RoomOrganizerRepository(db)
        val routines = RoomRoutineRepository(db.routineDao())
        val sources = RoomCollectorRepository(db)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val runs = RoomAgentRunRepository(db)
        val coordinator = DefaultAgentRunCoordinator(runs, scope)
        var modeRevision = 0L; var allowed = true; var calls = 0; var notices = 0; var schedules = 0
        var matched: Boolean? = null; var evidence = "observed:one"; var suspendWork = false
        var resultSources: List<OrganizerSourceRef> = emptyList(); var waiting = false; var failWork = false
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val capability = object : OrganizerWorkCapability {
            override val id = "monitor"
            override val argumentSchema = EverydayToolContracts.obj(mapOf("predicate" to EverydayToolContracts.choice(listOf("CHANGED")), "baseline" to EverydayToolContracts.text(128),
                "sourceId" to EverydayToolContracts.text(160), "sourceVersion" to EverydayToolContracts.number(1, Long.MAX_VALUE)), listOf("predicate", "baseline"))
            override suspend fun execute(arguments: Map<String, Any?>, requireCurrent: suspend () -> Unit): OrganizerStepResult {
                requireCurrent(); calls++; entered.complete(Unit)
                if (suspendWork) release.await()
                requireCurrent(); check(!failWork) { "Fixture failure" }
                return OrganizerStepResult("Actual fixture owner result", listOf(evidence), matched, waiting = waiting, sourceRefs = resultSources)
            }
        }
        lateinit var operations: OrganizerOperations
        val work = OrganizerWorkTool({ operations }, canRun = { allowed }, captureAuthority = {
            val admitted = modeRevision
            val verify: () -> Unit = { check(allowed && modeRevision == admitted) }
            verify
        })
        val registry = ToolRegistry(listOf(work), listOf(work.descriptor()))
        val attention = AttentionController(object : AttentionContextProvider {
            override fun snapshot() = AttentionContext(); override val changes = emptyFlow<Unit>()
        }, DefaultAttentionPolicyEngine(), InMemoryAttentionQueue(), object : AttentionDeliveryCoordinator {
            override suspend fun deliver(request: AttentionRequest, decision: AttentionDecision): DeliveryReceipt = error("Organizer owns semantic notifications")
        }, scope)
        val executor = RoutineExecutor(db, routines, coordinator, runs, registry,
            { AutomationEnvironmentSnapshot(Instant.now(), batteryPercent = 80, networkAvailable = true, screenInteractive = true) },
            attention, { allowed }, { ToolResourceCoordinator(DefaultDeviceCapabilityResolver(), DefaultResourcePlanner(), DefaultResourceLeaseManager()) },
            publishSemanticResult = { routine, summary -> operations.onRoutineFinished(routine, summary) })
        val scheduler = object : RoutineScheduler {
            override suspend fun schedule(routine: Routine): RoutineScheduleResult = if (!routine.enabled) RoutineScheduleResult.Blocked("Disabled")
                else { schedules++; RoutineScheduleResult.Scheduled(Instant.now().plusSeconds(60)) }
            override suspend fun cancel(routineId: RoutineId) {}
            override suspend fun reconcile() {}
        }
        val management = RoutineManagementActions(routines, scheduler, executor::validateForSave, executor::validate, executor::grantSummary, executor::execute)
        val useCases = OrganizerUseCases(organizer, elapsedMs = { 1000 }, bootId = { "boot" })
        init { operations = OrganizerOperations(db, useCases, routines, { management }, OrganizerWorkCapabilities(listOf(capabilityFactory?.invoke(this) ?: capability)), runs,
            sources, { coordinator.cancel(AgentRunId(it), "Stopped") }, { _, _ -> notices++ }, runAllowed = { allowed }) }
        suspend fun task(owner: CommitmentOwner = CommitmentOwner.HALO, purpose: CommitmentPurpose = CommitmentPurpose.STANDARD): OrganizerRecord =
            operations.saveDefinition(null, null, "Observed work", "Observe the fixture owner", owner, purpose,
                listOf(OrganizerPlannedAction(capability.id, "{\"predicate\":\"CHANGED\",\"baseline\":\"baseline\"}")), emptyList())
        override fun close() { scope.cancel(); db.close() }
    }
    @Test fun reviewDoesNotScheduleOrRunAndARealRunnerProducesTheConfirmedResult() = runBlocking {
        Harness().use { h ->
            val draft = h.task(); assertEquals(HaloWorkState.DRAFT, draft.commitment.state)
            val reviewed = h.operations.prepare(draft.id, draft.version)
            assertEquals(HaloWorkState.READY, reviewed.commitment.state); assertEquals(0, h.calls); assertEquals(0, h.schedules)
            val finished = h.operations.runNow(reviewed.id, reviewed.version)
            assertEquals(HaloWorkState.COMPLETED, finished.commitment.state)
            assertEquals("Actual fixture owner result", finished.commitment.result!!.summary)
            val run = h.routines.runResult(finished.commitment.execution!!.runId!!)!!
            assertEquals(RoutineRunState.COMPLETED, run.state); assertEquals(1, run.actionsCompleted)
            assertEquals(1, h.calls); assertEquals(1, h.notices)
            assertEquals(finished, RoomOrganizerRepository(h.db).get(finished.id))
        }
    }
    @Test fun sharedPreparationNeedsAHumanAndOnlyExplicitAcknowledgementCompletesIt() = runBlocking {
        Harness().use { h ->
            val draft = h.task(CommitmentOwner.SHARED); val ready = h.operations.prepare(draft.id, draft.version)
            val prepared = h.operations.runNow(ready.id, ready.version)
            assertEquals(HaloWorkState.NEEDS_USER, prepared.commitment.state); assertNotNull(prepared.commitment.nextHumanAction)
            assertThrows(IllegalArgumentException::class.java) { runBlocking { h.operations.runNow(prepared.id, prepared.version) } }
            val complete = h.operations.acknowledge(prepared.id, prepared.version)
            assertEquals(HaloWorkState.COMPLETED, complete.commitment.state); assertEquals(1, h.calls)
        }
    }
    @Test fun repeatedUnchangedMonitorEvidenceDoesNotGenerateAnotherAnnouncement() = runBlocking {
        Harness().use { h ->
            h.matched = true
            val draft = h.task(purpose = CommitmentPurpose.WATCH)
            val dated = h.organizer.save(draft.copy(time = OrganizerTime(LocalDateTime.now(ZoneOffset.UTC).withNano(0).plusMinutes(5), zoneId = "UTC"), repeat = RepeatRule(RepeatFrequency.DAILY)), draft.version)
            val ready = h.operations.prepare(dated.id, dated.version)
            val persisted = h.routines.get(RoutineId(ready.commitment.execution!!.routineId))!!.trigger as RoutineTrigger.AtTime
            assertEquals(dated.time!!.instant(ZoneOffset.UTC), persisted.notBefore)
            val first = h.operations.runNow(ready.id, ready.version)
            assertEquals(HaloWorkState.WAITING, first.commitment.state); assertEquals(1, h.notices)
            val second = h.operations.runNow(first.id, first.version)
            assertEquals(true, second.commitment.result!!.conditionMatched); assertEquals(false, second.commitment.result!!.newMatch); assertEquals(1, h.notices)
            h.evidence = "observed:changed"
            val third = h.operations.runNow(second.id, second.version)
            assertEquals(true, third.commitment.result!!.conditionMatched); assertEquals(2, h.notices)
        }
    }
    @Test fun schedulerReceiptIsRequiredBeforeWaitingCanBeShown() = runBlocking {
        Harness().use { h ->
            val draft = h.task()
            val dated = h.organizer.save(draft.copy(time = OrganizerTime(LocalDateTime.now(ZoneOffset.UTC).withNano(0).plusHours(1), zoneId = "UTC")), draft.version)
            val ready = h.operations.prepare(dated.id, dated.version)
            val waiting = h.operations.arm(ready.id, ready.version)
            assertEquals(HaloWorkState.WAITING, waiting.commitment.state)
            assertEquals("SCHEDULED", waiting.commitment.execution!!.scheduling); assertEquals(1, h.schedules); assertEquals(0, h.calls)
        }
    }
    @Test fun revocationDuringSuspendedWorkPreventsAPostHandoffResult() = runBlocking {
        Harness().use { h ->
            h.suspendWork = true
            val draft = h.task(); val ready = h.operations.prepare(draft.id, draft.version)
            val job = launch { runCatching { h.operations.runNow(ready.id, ready.version) } }
            withTimeout(10000) { h.entered.await() }
            val running = h.organizer.get(ready.id)!!
            assertEquals(HaloWorkState.RUNNING, running.commitment.state)
            val taken = h.operations.transfer(running.id, running.version, CommitmentOwner.ME)
            h.release.complete(Unit); job.join()
            val current = h.organizer.get(taken.id)!!
            assertEquals(CommitmentOwner.ME, current.commitment.owner); assertNull(current.commitment.execution)
            assertNull(current.commitment.result); assertEquals(0, h.notices)
            assertEquals(running.commitment.execution!!.runId, current.commitment.recovery!!.runId)
            assertTrue(current.commitment.recoveryNeedsReview); assertEquals(HaloWorkState.NEEDS_USER, current.commitment.state)
            assertThrows(IllegalArgumentException::class.java) { runBlocking { h.useCases.completeTask(current.id, current.version) } }
            val edited = h.operations.saveDefinition(current.id, current.version, "Human continuation", "Inspect earlier work", CommitmentOwner.ME,
                CommitmentPurpose.STANDARD, emptyList(), emptyList())
            assertEquals(current.commitment.recovery, edited.commitment.recovery)
            val reviewed = h.operations.acknowledgeRecovery(edited.id, edited.version)
            assertFalse(reviewed.commitment.recoveryNeedsReview); assertEquals(current.commitment.recovery!!.runId, reviewed.commitment.recovery!!.runId)
            assertEquals(OrganizerStatus.COMPLETED, h.useCases.completeTask(reviewed.id, reviewed.version).status)
        }
    }
    @Test fun aDirectInternalCallWithoutTheRunnerClaimCannotExecute() = runBlocking {
        Harness().use { h ->
            val draft = h.task(); val ready = h.operations.prepare(draft.id, draft.version)
            val result = h.work.execute(ToolCall("invented", OrganizerOperations.PERFORM, mapOf("id" to ready.id, "planId" to ready.commitment.execution!!.planId, "index" to 0)))
            assertTrue(result is ToolExecutionResult.Rejected); assertEquals(0, h.calls)
        }
    }
    @Test fun registeredCommitmentCommandsUseTheSameOwnerAndClosedReceipts() = runBlocking {
        Harness().use { h ->
            val api = OrganizerOperationTools(h.operations, h.sources, OrganizerResearch(h.sources), { h.allowed }, { { check(h.allowed) } },
                elapsedMs = { 1000 }, bootId = { "boot" })
            val registry = ToolRegistry(listOf(api), api.definitions().map { HaloToolDescriptors.assistant(it)!! })
            assertTrue(registry.operabilityReport(listOf(ToolFeatureOperability("organizer", OrganizerOperationContracts.names))).isEmpty())
            val context = ToolExecutionContext(interactionId = "typed-or-stt-final")
            val result = registry.executeAuthorized(ToolAuthorization.Authorized(ToolCall("save", "halo_commitment_save", mapOf("title" to "Captured", "owner" to "ME", "purpose" to "INBOX"))), context)
            assertTrue(result is ToolExecutionResult.Success)
            val record = h.organizer.records().single(); assertEquals(CommitmentPurpose.INBOX, record.commitment.purpose)
            val read = registry.executeAuthorized(ToolAuthorization.Authorized(ToolCall("read", "halo_commitments_query", mapOf("section" to "INBOX"))), context)
            assertTrue(read is ToolExecutionResult.Success)
            h.allowed = false
            assertTrue(api.execute(ToolCall("blocked", "halo_commitments_query", emptyMap()), context) is ToolExecutionResult.Rejected)
        }
    }
    @Test fun typedAndMicrophoneSemanticCaptureReachTheRegisteredCanonicalOwner() = runBlocking {
        Harness().use { h ->
            val api = OrganizerOperationTools(h.operations, h.sources, OrganizerResearch(h.sources), { true }, { {} }, elapsedMs = { 1 }, bootId = { "boot" })
            val registry = ToolRegistry(listOf(api), api.definitions().map { HaloToolDescriptors.assistant(it)!! })
            for (source in listOf(com.halo.cognition.language.Source.USER, com.halo.cognition.language.Source.MICROPHONE)) {
                for (text in listOf("Capture Friday buy material", "Delegate to Halo check the order tomorrow", "Create shared task choose a printer", "Watch for a reply")) {
                    val intent = com.halo.cognition.language.NativeCommandParser.parse(text)!!
                    val request = com.halo.runtime.cognition.native.OrganizerNativeRequests.requestFor(
                        com.halo.runtime.cognition.blackboard.Interpretation(text, intent = intent, confidence = 1f, source = source))!!
                    assertEquals("halo_commitment_save", request.operation)
                    val result = registry.executeAuthorized(ToolAuthorization.Authorized(ToolCall(request.interactionId, request.operation, request.arguments)),
                        ToolExecutionContext(interactionId = request.interactionId)) as ToolExecutionResult.Success
                    val json = org.json.JSONObject(result.output).getJSONObject("record")
                    val saved = h.organizer.get(json.getString("id"))!!
                    assertEquals(request.arguments["owner"], saved.commitment.owner.name)
                    assertNull(saved.commitment.execution); assertNull(saved.commitment.result)
                }
            }
            assertEquals(8, h.organizer.records().size); assertEquals(0, h.calls); assertEquals(0, h.schedules)
        }
    }
    @Test fun alteredPlanContentsCannotReuseAnOldRoutineApproval() = runBlocking {
        Harness().use { h ->
            val draft = h.task(); val ready = h.operations.prepare(draft.id, draft.version)
            // Simulates untrusted imported state bypassing an ordinary versioned edit.
            val tampered = ready.copy(commitment = ready.commitment.copy(intent = "Different work"))
            h.db.organizerDao().update(OrganizerCodec.entity(tampered))
            assertThrows(IllegalArgumentException::class.java) { runBlocking { h.operations.runNow(tampered.id, tampered.version) } }
            assertEquals(0, h.calls)
        }
    }
    @Test fun longOrganizerIdentitiesDoNotShareAnExecutionOwner() = runBlocking {
        Harness().use { h ->
            val template = h.task()
            val first = h.organizer.save(template.copy(id = "x".repeat(60) + "one"), null)
            val second = h.organizer.save(template.copy(id = "x".repeat(60) + "two"), null)
            val a = h.operations.prepare(first.id, first.version); val b = h.operations.prepare(second.id, second.version)
            assertNotEquals(a.commitment.execution!!.routineId, b.commitment.execution!!.routineId)
            h.operations.runNow(a.id, a.version); h.operations.runNow(b.id, b.version)
            assertEquals(2, h.calls)
        }
    }
    private suspend fun Harness.selectedSource(): CollectorSource = sources.save(CollectorSource("source", "Selected", "Publisher", CollectorKind.FEED,
        SourceCategory.CUSTOM, "en", "https://example.org/feed", selected = true), null)
    @Test fun retainedWorkResultsAreWithheldWhenTheirInputGrantIsRemoved() = runBlocking {
        Harness().use { h ->
            val source = h.selectedSource(); h.resultSources = listOf(OrganizerSourceRef(source.id, source.version))
            val draft = h.task(); val ready = h.operations.prepare(draft.id, draft.version)
            val completed = h.operations.runNow(ready.id, ready.version)
            assertTrue(h.operations.canReadResult(completed))
            h.sources.save(source.copy(selected = false), source.version)
            assertFalse(h.operations.canReadResult(completed))
            val api = OrganizerOperationTools(h.operations, h.sources, OrganizerResearch(h.sources), { true }, { {} }, elapsedMs = { 1 }, bootId = { "boot" })
            val response = api.execute(ToolCall("read", "halo_commitments_query", mapOf("id" to completed.id)), ToolExecutionContext()) as ToolExecutionResult.Success
            val row = org.json.JSONObject(response.output).getJSONArray("records").getJSONObject(0)
            assertFalse(row.getBoolean("resultAvailable")); assertFalse(row.has("result"))
            assertFalse(response.output.contains("Actual fixture owner result"))
        }
    }
    @Test fun inputRevocationDuringAClaimPreventsResultPublication() = runBlocking {
        Harness().use { h ->
            val source = h.selectedSource(); h.resultSources = listOf(OrganizerSourceRef(source.id, source.version)); h.suspendWork = true
            val draft = h.task(); val ready = h.operations.prepare(draft.id, draft.version)
            val job = launch { runCatching { h.operations.runNow(ready.id, ready.version) } }
            withTimeout(10000) { h.entered.await() }
            h.sources.save(source.copy(selected = false), source.version); h.release.complete(Unit); job.join()
            val record = h.organizer.get(ready.id)!!
            assertNotEquals(HaloWorkState.COMPLETED, record.commitment.state); assertNull(record.commitment.result)
            assertEquals(1, h.calls)
        }
    }
    @Test fun monitoringCannotStartFromAFabricatedBaseline() = runBlocking {
        Harness().use { h ->
            val source = h.selectedSource()
            val now = Instant.now(); val claim = (h.sources.reserve(source.id, source.version, now, true) as CollectorAdmission.Accepted).reservation
            h.sources.finish(claim, CollectorSnapshot(source.id, source.version, CollectorPayload.Articles(emptyList()), "observed", now, now, now.plusSeconds(600)),
                CollectorAttempt(source.id, now, CollectorAttemptState.CURRENT))
            val bad = OrganizerPlannedAction("monitor", "{\"predicate\":\"CHANGED\",\"baseline\":\"invented\",\"sourceId\":\"source\",\"sourceVersion\":1}")
            assertThrows(IllegalArgumentException::class.java) { runBlocking { h.operations.saveDefinition(null, null, "Watch", "", CommitmentOwner.HALO, CommitmentPurpose.WATCH, listOf(bad), emptyList()) } }
            assertTrue(h.organizer.records().isEmpty())
        }
    }
    @Test fun aWaitingSharedCheckCannotBeAcknowledgedAsCompletedAndFailureHasRecovery() = runBlocking {
        Harness().use { h ->
            h.waiting = true
            val draft = h.task(CommitmentOwner.SHARED); val ready = h.operations.prepare(draft.id, draft.version)
            val deferred = h.operations.runNow(ready.id, ready.version)
            assertEquals(HaloWorkState.NEEDS_USER, deferred.commitment.state)
            assertThrows(IllegalArgumentException::class.java) { runBlocking { h.operations.acknowledge(deferred.id, deferred.version) } }
            assertEquals(0, h.notices)
        }
        Harness().use { h ->
            h.failWork = true
            val draft = h.task(); val ready = h.operations.prepare(draft.id, draft.version)
            runCatching { h.operations.runNow(ready.id, ready.version) }
            val failed = h.organizer.get(draft.id)!!
            assertTrue(failed.commitment.state in setOf(HaloWorkState.FAILED, HaloWorkState.NEEDS_USER))
            assertNotNull(failed.commitment.nextHumanAction); assertNull(failed.commitment.result)
            assertEquals(1, h.calls)
        }
    }
    @Test fun reconciliationRevokesOrphanSchedulesAndDoesNotReplayAnInterruptedClaim() = runBlocking {
        Harness().use { h ->
            val draft = h.task(); val ready = h.operations.prepare(draft.id, draft.version)
            val owner = ready.commitment.execution!!.routineId
            h.routines.setEnabled(RoutineId(owner), true)
            // A restored record retains facts but deliberately has no execution grant.
            h.organizer.save(ready.copy(commitment = ready.commitment.copy(execution = null, state = HaloWorkState.DRAFT)), ready.version)
            h.operations.reconcile()
            assertFalse(h.routines.get(RoutineId(owner))!!.enabled); assertEquals(0, h.calls)
            val restored = h.organizer.get(draft.id)!!; val reviewed = h.operations.prepare(restored.id, restored.version)
            val interrupted = h.organizer.save(reviewed.copy(commitment = reviewed.commitment.copy(state = HaloWorkState.RUNNING,
                execution = reviewed.commitment.execution!!.copy(runId = "missing-run", claimedStep = 0))), reviewed.version)
            h.operations.reconcile()
            val recovered = h.organizer.get(interrupted.id)!!
            assertEquals(HaloWorkState.NEEDS_USER, recovered.commitment.state); assertEquals(0, h.calls)
            assertThrows(IllegalArgumentException::class.java) { runBlocking { h.operations.runNow(recovered.id, recovered.version) } }
        }
    }
    @Test fun pendingSharedHandoffSurvivesAnotherScheduledOccurrenceAndCanStillBeAcknowledged() = runBlocking {
        Harness().use { h ->
            val draft = h.task(CommitmentOwner.SHARED, CommitmentPurpose.ROUTINE)
            val dated = h.organizer.save(draft.copy(time = OrganizerTime(LocalDateTime.now(ZoneOffset.UTC).withNano(0).minusDays(1), zoneId = "UTC"),
                repeat = RepeatRule(RepeatFrequency.DAILY)), draft.version)
            val ready = h.operations.prepare(dated.id, dated.version); val armed = h.operations.arm(ready.id, ready.version)
            val prepared = h.operations.runNow(armed.id, armed.version)
            val originalRun = prepared.commitment.execution!!.runId
            val routine = h.routines.get(RoutineId(prepared.commitment.execution!!.routineId))!!
            h.executor.execute(RoutineOccurrence.scheduled(routine, Instant.now()))
            val held = h.organizer.get(prepared.id)!!
            assertEquals(HaloWorkState.NEEDS_USER, held.commitment.state); assertEquals(originalRun, held.commitment.execution!!.runId)
            assertEquals(1, h.calls); assertEquals(1, h.notices); assertTrue(h.routines.get(routine.id)!!.enabled)
            assertEquals(HaloWorkState.WAITING, h.operations.acknowledge(held.id, held.version).commitment.state)
        }
    }
    @Test fun aConfirmedManualRunCanBeFollowedByEnablingItsUnarmedRecurringSchedule() = runBlocking {
        Harness().use { h ->
            val draft = h.task()
            val dated = h.organizer.save(draft.copy(time = OrganizerTime(LocalDateTime.now(ZoneOffset.UTC).withNano(0).plusHours(1), zoneId = "UTC"),
                repeat = RepeatRule(RepeatFrequency.DAILY)), draft.version)
            val ready = h.operations.prepare(dated.id, dated.version)
            val run = h.operations.runNow(ready.id, ready.version)
            assertEquals(HaloWorkState.WAITING, run.commitment.state); assertEquals("UNCONFIRMED", run.commitment.execution!!.scheduling)
            assertNotNull(run.commitment.nextHumanAction)
            assertEquals(0, h.schedules)
            val armed = h.operations.arm(run.id, run.version)
            assertEquals("SCHEDULED", armed.commitment.execution!!.scheduling); assertEquals(1, h.schedules)
        }
    }
    @Test fun takeoverInsideRealBriefAssemblyPreventsItsNestedDocumentAndMorningBaseline() = runBlocking {
        lateinit var briefs: RoomBriefRepository
        Harness { h ->
            briefs = RoomBriefRepository(h.db)
            OrganizerBriefCapability(EverydayBriefService(briefs, h.sources, h.organizer,
                { h.entered.complete(Unit); withContext(NonCancellable) { h.release.await() }; DailyAssistantObservation() },
                { BriefAudience.EVERYDAY }, { emptySet() }, { true }, zone = { ZoneOffset.UTC }))
        }.use { h ->
            h.sources.save(StarterSourceCatalogue.local().first { it.kind == CollectorKind.LOCAL_ORGANIZER }, null)
            val draft = h.operations.saveDefinition(null, null, "Morning work", "", CommitmentOwner.HALO, CommitmentPurpose.STANDARD,
                listOf(OrganizerPlannedAction("summarize", "{\"period\":\"MORNING\"}")), emptyList())
            val ready = h.operations.prepare(draft.id, draft.version)
            val job = launch { runCatching { h.operations.runNow(ready.id, ready.version) } }
            withTimeout(10000) { h.entered.await() }
            val running = h.organizer.get(draft.id)!!
            val human = h.operations.transfer(running.id, running.version, CommitmentOwner.ME)
            h.release.complete(Unit); job.join()
            assertTrue(briefs.recent().isEmpty())
            assertNull(briefs.baseline(LocalDate.now(ZoneOffset.UTC), ZoneOffset.UTC, BriefAudience.EVERYDAY))
            assertEquals(human.commitment.recovery!!.runId, h.organizer.get(draft.id)!!.commitment.recovery!!.runId)
            assertEquals(0, h.notices)
        }
    }
    private class PermittedPolicy : com.halo.sdk.provider.RemoteDispatchPolicy {
        override val revision = 0L
        override fun acquire(request: com.halo.sdk.provider.RemoteDispatchRequest, expectedRevision: Long, recordActivity: Boolean, cancel: () -> Unit) =
            object : com.halo.sdk.provider.RemoteDispatchLease {
                override fun requireCurrent() {}
                override fun finish(outcome: com.halo.sdk.provider.RemoteDispatchOutcome, providerReportedCostUsd: String?) {}
            }
    }
    @Test fun modeRevisionDuringRealResearchRefreshPreventsPublicationButAFreshLocalReadCanRun() = runBlocking {
        val now = Instant.now()
        val payload = CollectorPayload.Articles(listOf(ArticleFact("printer", "Printer facts", "Printer evidence", "https://example.org/printer", "Publisher", null, "en")))
        var fetches = 0
        Harness { h ->
            val adapter = object : Collector {
                override val kind = CollectorKind.FEED
                override suspend fun fetch(source: CollectorSource, cached: CollectorSnapshot?): CollectorFetchResult {
                    fetches++; h.entered.complete(Unit); withContext(NonCancellable) { h.release.await() }
                    return CollectorFetchResult.Updated(payload, "observation")
                }
            }
            OrganizerResearchCapability(h.sources, CollectorCoordinator(h.sources, CollectorRegistry(listOf(adapter)), PermittedPolicy()), OrganizerResearch(h.sources))
        }.use { h ->
            val source = h.selectedSource()
            fun action(refresh: Boolean) = OrganizerPlannedAction("web.search", "{\"query\":\"printer\",\"sourceId\":\"source\",\"sourceVersion\":${source.version},\"refresh\":$refresh}")
            val draft = h.operations.saveDefinition(null, null, "Research", "", CommitmentOwner.HALO, CommitmentPurpose.STANDARD, listOf(action(true)), emptyList())
            val ready = h.operations.prepare(draft.id, draft.version)
            val job = launch { runCatching { h.operations.runNow(ready.id, ready.version) } }
            withTimeout(10000) { h.entered.await() }
            h.modeRevision++; h.release.complete(Unit); job.join()
            assertNull(h.sources.state(source.id)!!.snapshot); assertNull(h.organizer.get(draft.id)!!.commitment.result); assertEquals(0, h.notices)
            // A new admission in Local may use actual retained evidence without another fetch.
            h.db.collectorDao().putSnapshot(CollectorCodec.entity(CollectorSnapshot(source.id, source.version, payload, "retained", now, now, now.plusSeconds(3600))))
            val local = h.operations.saveDefinition(null, null, "Retained research", "", CommitmentOwner.HALO, CommitmentPurpose.STANDARD, listOf(action(false)), emptyList())
            val reviewed = h.operations.prepare(local.id, local.version)
            assertEquals(HaloWorkState.COMPLETED, h.operations.runNow(reviewed.id, reviewed.version).commitment.state)
            assertEquals(1, fetches)
        }
    }
}
