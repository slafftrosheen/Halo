package com.halo.app.automation

import androidx.room.withTransaction
import com.halo.app.data.AppDatabase
import com.halo.app.data.routine.*
import com.halo.cognition.language.InteractionId
import com.halo.runtime.cognition.agent.*
import com.halo.runtime.cognition.routine.*
import com.halo.runtime.cognition.tools.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.time.Instant

class RoutineExecutor(private val db: AppDatabase, private val repository: RoomRoutineRepository,
    private val coordinator: AgentRunCoordinator, private val store: AgentRunStore,
    private val registry: ToolRegistry, val environmentSnapshot: suspend () -> AutomationEnvironmentSnapshot,
    private val attention: com.halo.runtime.cognition.attention.AttentionController,
    private val currentlyAllowed: (String) -> Boolean,
    private val resources: () -> ToolResourceCoordinator,
    private val publishScreenResult: suspend (Routine, RoutineRunState, Int) -> Unit = { _, _, _ -> }) {
    private val policy = DefaultToolAutomationPolicy(registry::descriptor)
    fun actionDescriptors() = routineActionDescriptors(registry)
    fun grantSummary(r: Routine): String {
        validate(r)
        return routineGrantSummary(r, r.actions.map { action ->
            val descriptor = requireNotNull(registry.descriptor(action.toolId))
            val fields = RoutineArguments.decode(action.argumentsJson).entries.joinToString { (key, value) ->
                "${key.replace('_', ' ')}: ${value.toString().take(80)}"
            }
            (descriptor.presentation?.title ?: descriptor.definition.description.take(80)) + if(fields.isEmpty()) "" else " ($fields)"
        })
    }
    fun validateForSave(r: Routine) {
        validate(r)
        (r.trigger as? RoutineTrigger.Once)?.let { require(it.at > Instant.now()) { "Choose a future time" } }
        when(val t = r.trigger) {
            is RoutineTrigger.AppStarted -> require(AutomationServiceSignals.foreground.availability.value == AutomationSourceStatus.AVAILABLE) { "Set up Phone screen access before saving" }
            is RoutineTrigger.NotificationFromApp -> require(AutomationServiceSignals.notifications.availability.value == AutomationSourceStatus.AVAILABLE) { "Set up Notification access before saving" }
            else -> Unit
        }
    }
    fun validate(r: Routine) {
        require(r.id.value.length in 1..80) { "Invalid routine identity" }
        require(r.name.isNotBlank() && r.name.length <= 120) { "Enter a name of at most 120 characters" }
        require(r.actions.size in 1..10) { "A routine requires 1–10 actions" }
        require(r.conditions.size <= 10)
        require(r.minimumIntervalSeconds in 0..86400)
        r.conditions.filterIsInstance<RoutineCondition.BatteryAbove>().forEach { require(it.percent in 1..100) }
        when(val t = r.trigger) {
            is RoutineTrigger.BatteryCrossedAbove -> require(t.percent in 1..100)
            is RoutineTrigger.BatteryCrossedBelow -> require(t.percent in 1..100)
            is RoutineTrigger.AppStarted -> require(t.appPackage.matches(Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")))
            is RoutineTrigger.NotificationFromApp -> { require(t.packageName.matches(Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+"))); require(t.coalesceSeconds in 0..3600) }
            else -> Unit
        }
        val delivery = r.delivery
        if (delivery is RoutineDeliveryPolicy.WearableSpeech) require(delivery.deviceId.isNotBlank())
        when (val t = r.trigger) {
            is RoutineTrigger.DeviceConnected -> require(t.deviceId.isNotBlank()) { "Choose a device" }
            is RoutineTrigger.DeviceDisconnected -> require(t.deviceId.isNotBlank()) { "Choose a device" }
            else -> Unit
        }
        r.actions.forEachIndexed { index, action ->
            val call = call(action, "validate:$index")
            require(registry.validate(call) == null) { "Unknown tool or invalid arguments" }
            val descriptor = requireNotNull(registry.descriptor(action.toolId)) { "Unknown action" }
            require(descriptor.automation.backgroundCompatible && descriptor.automation.argumentsPersistable) { "Action cannot run in background with durable arguments" }
            require(descriptor.security.confirmation != ConfirmationPolicy.ALWAYS) { "Action requires interactive approval" }
            require(descriptor.presentation?.sensitiveArguments.orEmpty().intersect(RoutineArguments.decode(action.argumentsJson).keys).isEmpty()) { "Sensitive arguments cannot be stored" }
            require(policy.policyFor(action.toolId) == AutomationPermission.ALLOWED_WITH_ROUTINE_GRANT) { "${action.toolId} cannot run unattended" }
            require(currentlyAllowed(action.toolId)) { "Tool disabled by current permission policy" }
        }
    }
    private fun call(action: RoutineAction, id: String) = RoutineArguments.call(action, id)
    suspend fun execute(id: RoutineId, occurrence: Instant, manual: Boolean = false): String? {
        val r = repository.get(id) ?: return null
        return execute(if(manual) RoutineOccurrence.manual(r, occurrence) else RoutineOccurrence.scheduled(r, occurrence))
    }
    /** Explicit one-shot approval only. This definition never enters the scheduler's repository. */
    suspend fun executeTrial(draft: Routine, runId: String): RoutineRunSummary? {
        val snapshot = draft.copy(id = RoutineId("trial-${java.util.UUID.randomUUID()}"), enabled = false,
            actions = draft.actions.toList(), conditions = draft.conditions.toList(),
            delivery = RoutineDeliveryPolicy.SilentActivity, lastRunAt = null, nextRunAt = null, grantDigest = null)
        validate(snapshot)
        val approved = snapshot.copy(grantDigest = RoutineArguments.grant(snapshot))
        try { execute(RoutineOccurrence.manual(approved, Instant.now()).copy(id = runId, rootOccurrenceId = runId), approved) }
        catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                // Cancellation may arrive as the durable claim transaction returns.
                repository.dao.run(runId)?.takeIf { it.state == "RUNNING" }?.let {
                    repository.dao.finishRun(runId, "INTERRUPTED", it.actionsCompleted, approved.actions.size,
                        System.currentTimeMillis(), "Trial interrupted; check recorded actions")
                }
                repository.dao.deliveryEvidence(runId, "TRIAL_RECORDED")
            }
            throw cancelled
        }
        repository.dao.deliveryEvidence(runId, "TRIAL_RECORDED")
        return repository.runResult(runId)
    }
    suspend fun runResult(id: String) = repository.runResult(id)
    suspend fun execute(occurrence: RoutineOccurrence): String? = execute(occurrence, null)
    private suspend fun execute(occurrence: RoutineOccurrence, trial: Routine?): String? {
        val id = occurrence.routineId
        val manual = occurrence.source == RoutineOccurrenceSource.MANUAL
        if (id.value in occurrence.causalRoutineIds) return "Blocked: causal routine cycle"
        coordinator.prepareOutstandingRunsForRecovery()
        val identity = occurrence.id
        val runId = AgentRunId(identity)
        // The run and occurrence claim commit together. A crashed/duplicate claim is never resumed:
        // routine runs carry RunRecoveryPolicy.NO_RESUME (RUNTIME-018).
        val routine = db.withTransaction {
            if (repository.dao.run(identity) != null) return@withTransaction null
            val r = trial ?: repository.get(id) ?: return@withTransaction null
            if(occurrence.routineRevision != null && r.updatedAt != occurrence.routineRevision) return@withTransaction null
            if (!manual && !r.enabled) return@withTransaction null
            val cooldown = maxOf(r.minimumIntervalSeconds, (r.trigger as? RoutineTrigger.NotificationFromApp)?.coalesceSeconds ?: 0)
            if (!manual && occurrence.source == RoutineOccurrenceSource.EVENT && r.lastRunAt?.let { occurrence.occurredAt.isBefore(it.plusSeconds(cooldown)) } == true) {
                if(r.trigger is RoutineTrigger.NotificationFromApp) repository.dao.coalesceLatest(id.value)
                return@withTransaction null
            }
            val now = Instant.now()
            android.util.Log.i("HALO_ROUTINE_OCCURRENCE_CLAIM", "routine=${id.value.take(8)} occurrence=${identity.takeLast(12)} source=${occurrence.source}")
            store.create(AgentRun(runId, InteractionId(identity), r.name, AgentRunState.CREATED,
                createdAt = now, updatedAt = now, startedAt = now))
            store.appendStep(AgentStep.RunPrepared(AgentStepId("$identity:prepared"), runId, now,
                AgentRunKind.ROUTINE, RunRecoveryPolicy.NO_RESUME))
            check(repository.dao.insertRun(RoutineRunEntity(identity, id.value, runId.value, occurrence.occurredAt.toEpochMilli(), "RUNNING", source = if (trial != null) "TRIAL" else occurrence.source.name, signalId = occurrence.signalId, rootOccurrenceId = occurrence.rootOccurrenceId, causedByOccurrenceId = occurrence.causedByOccurrenceId)) != -1L)
            if (trial == null) repository.dao.lastRun(id.value, occurrence.occurredAt.toEpochMilli())
            r
        } ?: return null
        var completed = 0
        val limits = AgentRunLimits.forKind(AgentRunKind.ROUTINE)
        val guard = AgentRunGuard(identity, identity, limits)
        suspend fun finish(state: RoutineRunState, reason: String) {
            val boundedReason = reason.take(2400)
            coordinator.recordStep(AgentStep.ResponseProduced(AgentStepId("$identity:response"), runId, Instant.now(), identity, boundedReason))
            if (state == RoutineRunState.COMPLETED) coordinator.complete(runId)
            else if (state == RoutineRunState.SKIPPED || state == RoutineRunState.CANCELLED) coordinator.cancel(runId, reason)
            else coordinator.fail(runId, reason)
            repository.dao.finishRun(identity, state.name, completed, routine.actions.size, System.currentTimeMillis(), boundedReason)
            if (trial != null) {
                repository.dao.deliveryEvidence(identity, "TRIAL_RECORDED")
                return
            }
            // A presentation-cache failure never changes the routine's recorded outcome.
            try { publishScreenResult(routine, state, completed) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { android.util.Log.w("HALO_SCREEN", "Routine result card unavailable") }
            val currentDefinition = repository.get(id)
            val validDeliveryGrant = runCatching {
                routine.grantDigest != null && routine.grantDigest == RoutineArguments.grant(routine) &&
                    currentDefinition != null && currentDefinition.grantDigest == routine.grantDigest && currentDefinition.updatedAt == routine.updatedAt &&
                    (manual || currentDefinition.enabled)
            }.getOrDefault(false)
            val selectedDelivery = if(validDeliveryGrant && state != RoutineRunState.CANCELLED) routine.delivery else RoutineDeliveryPolicy.SilentActivity
            val channel = when(val d = selectedDelivery) {
                RoutineDeliveryPolicy.AndroidNotification -> com.halo.runtime.cognition.attention.DeliveryChannel.AndroidNotification
                RoutineDeliveryPolicy.InAppWhenForeground -> com.halo.runtime.cognition.attention.DeliveryChannel.InApp
                is RoutineDeliveryPolicy.WearableSpeech -> com.halo.runtime.cognition.attention.DeliveryChannel.WearableSpeech(d.deviceId)
                else -> null
            }
            val receipt = try { attention.submit(com.halo.runtime.cognition.attention.AttentionRequest(
                com.halo.runtime.cognition.attention.AttentionRequestId("result:$identity"),
                com.halo.runtime.cognition.attention.AttentionSource.Routine, InteractionId(identity),
                importance = com.halo.runtime.cognition.attention.AttentionImportance.NORMAL,
                deliveryIntent = com.halo.runtime.cognition.attention.DeliveryIntent.Inform(
                    com.halo.runtime.cognition.attention.UserFacingContent(routine.name.take(120),
                        RoutineResultSummary(routine.actions.size, completed, state, listOf(boundedReason)).text())),
                preferredRoutineChannel = channel, deduplicationKey = "result:$identity", expiresAt = Instant.now().plusSeconds(86400))) }
                catch(cancelled: CancellationException) { throw cancelled }
                catch(_: Exception) { com.halo.runtime.cognition.attention.DeliveryReceipt(
                    com.halo.runtime.cognition.attention.AttentionRequestId("result:$identity"), com.halo.runtime.cognition.attention.DeliveryOutcome.FAILED,
                    reason = "Delivery unavailable") }
            repository.dao.deliveryEvidence(identity, receipt.outcome.name)

        }
        try {
            coordinator.transition(runId, AgentRunState.PLANNING)
            coordinator.recordStep(AgentStep.PlanCreated(AgentStepId("$identity:plan"), runId, Instant.now(), identity,
                "Triggered ${routine.name}; ${routine.conditions.size} conditions, ${routine.actions.size} sequential actions"))
            try { validate(routine) } catch (e: IllegalArgumentException) {
                finish(RoutineRunState.BLOCKED, e.message ?: "Routine blocked"); return e.message
            }
            if (routine.grantDigest == null || routine.grantDigest != RoutineArguments.grant(routine)) {
                finish(RoutineRunState.BLOCKED, "ROUTINE_GRANT_MISMATCH: open and save this automation"); return "Routine needs approval"
            }
            val environment = environmentSnapshot()
            BackgroundWorkPolicy.blockReason(environment, routine.actions.any { registry.descriptor(it.toolId)?.automation?.requiresModel == true })?.let {
                finish(RoutineRunState.BLOCKED, it); return it
            }
            routine.conditions.forEachIndexed { index, c ->
                val outcome = RoutineConditionEvaluator.evaluate(c, environment)
                android.util.Log.i("HALO_ROUTINE_CONDITION", "type=${c.javaClass.simpleName} outcome=$outcome")
                val passed = outcome == ConditionOutcome.PASSED
                coordinator.recordStep(AgentStep.PlanCreated(AgentStepId("$identity:condition:$index"), runId,
                    Instant.now(), identity, "Condition ${index + 1} (${c.javaClass.simpleName}): $outcome"))
                if (!passed) {
                    val unavailable = outcome == ConditionOutcome.UNAVAILABLE
                    val reason = "${c.javaClass.simpleName}: ${if(unavailable) "status unavailable" else "condition not met"}"
                    finish(if(unavailable) RoutineRunState.BLOCKED else RoutineRunState.SKIPPED, reason); return reason
                }
            }
            coordinator.transition(runId, AgentRunState.EXECUTING)
            val output = mutableListOf<String>()
            routine.actions.forEachIndexed { index, action ->
                val current = trial ?: repository.get(id)
                if (current == null || (!manual && !current.enabled) || current.updatedAt != routine.updatedAt) {
                    finish(RoutineRunState.CANCELLED, "Routine changed or disabled"); return "Cancelled"
                }
                validate(current)
                if(current.grantDigest != RoutineArguments.grant(current)) { finish(RoutineRunState.BLOCKED, "ROUTINE_GRANT_MISMATCH"); return "Routine changed" }
                val deadline = guard.checkDeadline()
                val digest = java.security.MessageDigest.getInstance("SHA-256").digest(RoutineArguments.canonical(action.argumentsJson).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
                val guardDecision = if(deadline is RunGuardDecision.Stop) deadline else guard.onToolCallPrepared(action.toolId, digest)
                if(guardDecision is RunGuardDecision.Stop) {
                    coordinator.recordStep(guard.toJournalStep(guardDecision))
                    finish(if(completed > 0) RoutineRunState.PARTIAL else RoutineRunState.BLOCKED, RunGuardMessages.summary(guardDecision))
                    return "Execution limit reached"
                }
                val key = "$identity:action:$index"
                val call = call(action, key)
                coordinator.recordStep(AgentStep.ToolRequested(AgentStepId("$key:request"), runId, Instant.now(), key, action.toolId, action.argumentsJson))
                val acquisition = resources().prepareAndAcquire(
                    requireNotNull(registry.descriptor(action.toolId)),
                    com.halo.sdk.runtime.resource.ResourceHolder(key, identity, identity, "$key:invocation",
                        com.halo.sdk.runtime.resource.ResourceHolderKind.TOOL),
                    priority = com.halo.sdk.runtime.resource.ResourcePriority.BACKGROUND)
                val lease = (acquisition as? ToolResourceAcquisition.Granted)?.lease
                if (lease == null) {
                    val reason = if(acquisition is ToolResourceAcquisition.CapabilityMissing) "Required device capability unavailable" else "Required resource busy"
                    finish(if(completed > 0) RoutineRunState.PARTIAL else RoutineRunState.BLOCKED, reason)
                    return reason
                }
                val result = try {
                    // Claim before dispatch; never replay a claimed action after unknown completion.
                    if (!coordinator.recordStep(AgentStep.ToolExecutionClaimed(AgentStepId("$key:claim"), runId, Instant.now(),
                            ToolInvocationId("$key:invocation"), key, action.toolId, key, ReplaySafety.NEVER_REPLAY.name))) {
                        finish(RoutineRunState.INTERRUPTED, "Execution already claimed; completion unknown"); return "Interrupted"
                    }
                    withContext(RunToolScope(runId, targetIsLocal = true)) {
                        registry.executeAuthorized(ToolAuthorization.Authorized(call))
                    }
                } finally { lease.release() }
                android.util.Log.i("HALO_ROUTINE_ACTION", "tool=${action.toolId} index=$index status=${result.javaClass.simpleName}")
                when (result) {
                    is ToolExecutionResult.Success -> {
                        coordinator.recordStep(AgentStep.ToolOutcomeRecorded(AgentStepId("$key:outcome"), runId, Instant.now(),
                            ToolInvocationId("$key:invocation"), key, action.toolId, true, result.output.take(4000),
                            result.actionTruth, null, result.payloadRef?.id, result.payloadRef?.sha256, result.payloadRef?.byteCount))
                        coordinator.recordStep(AgentStep.ToolCompleted(AgentStepId("$key:result"), runId, Instant.now(), key, action.toolId,
                            result.output.take(4000), true, result.actionTruth))
                        completed++
                        output += result.output.take(240)
                        android.util.Log.i("HALO_ROUTINE_ACTION", "tool=${action.toolId} index=$index status=COMPLETED truth=${result.actionTruth}")
                    }
                    else -> {
                        val unknown = result is ToolExecutionResult.Failed && result.completionUnknown
                        val reason = when (result) {
                            is ToolExecutionResult.Failed -> result.message
                            is ToolExecutionResult.Rejected -> result.reason
                            else -> "Failed"
                        }
                        if (!unknown) {
                            if (result is ToolExecutionResult.Rejected) {
                                val bounded = reason.replace('\n', ' ').take(512)
                                coordinator.recordStep(
                                    AgentStep.ToolRejected(
                                        AgentStepId("$key:result"), runId, Instant.now(), key, action.toolId,
                                        ToolRejectionCode.INVALID_ARGUMENTS, bounded,
                                        retryable = false, requiresUserAction = false,
                                        modelResult = "tool_result: status=rejected reason_code=INVALID_ARGUMENTS retryable=false requires_user_action=false\nreason: $bounded",
                                    )
                                )
                            } else {
                                coordinator.recordStep(
                                    AgentStep.ToolFailed(
                                        AgentStepId("$key:result"), runId, Instant.now(), key, action.toolId, reason
                                    )
                                )
                            }
                        }
                        finish(
                            if (unknown) RoutineRunState.INTERRUPTED
                            else if (completed > 0) RoutineRunState.PARTIAL
                            else if (result is ToolExecutionResult.Rejected) RoutineRunState.BLOCKED
                            else RoutineRunState.FAILED,
                            reason,
                        )
                        return reason
                    }
                }
            }
            val text = RoutineResultSummary(routine.actions.size, completed, RoutineRunState.COMPLETED, output).text()
            finish(RoutineRunState.COMPLETED, output.joinToString("\n"))
            return text
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                val run = store.get(runId)
                val unknown = run?.hasUnknownAction == true
                if (run != null && !run.state.isTerminal) {
                    if (unknown) coordinator.transition(runId, AgentRunState.INTERRUPTED, "Cancelled after dispatch; completion unknown")
                    else coordinator.cancel(runId, "Routine execution cancelled")
                }
                if(run?.state?.isTerminal == true) repository.dao.deliveryEvidence(identity, "COMPLETION_UNKNOWN")
                else repository.dao.finishRun(identity, if (unknown) "INTERRUPTED" else "CANCELLED", completed, routine.actions.size, System.currentTimeMillis(), "Routine execution cancelled")
            }
            throw e
        }
        catch (e: Exception) {
            val run = store.get(runId)
            if (run != null && !run.state.isTerminal) coordinator.fail(runId, e.message ?: "Routine failed")
            if (run?.state?.isTerminal == true) repository.dao.deliveryEvidence(identity, "COMPLETION_UNKNOWN")
            else repository.dao.finishRun(identity, if (run?.hasUnknownAction == true) "INTERRUPTED" else if (completed > 0) "PARTIAL" else "FAILED", completed, routine.actions.size, System.currentTimeMillis(), "Routine execution failed")
            return e.message ?: "Routine failed"
        }
    }
}
