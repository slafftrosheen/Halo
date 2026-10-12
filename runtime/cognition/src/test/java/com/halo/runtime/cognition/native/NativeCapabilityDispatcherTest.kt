package com.halo.runtime.cognition.native

import com.halo.cognition.language.*
import com.halo.runtime.cognition.agent.*
import com.halo.runtime.cognition.blackboard.Interpretation
import com.halo.runtime.cognition.skills.*
import com.halo.runtime.cognition.tools.*
import com.halo.runtime.cognition.turn.*
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.*
import com.halo.sdk.runtime.skills.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Test

class NativeCapabilityDispatcherTest {
    private val commands = linkedMapOf("Set a timer for two minutes" to "halo_timer", "Open Spotify" to "halo_open_app", "Lower volume" to "halo_volume",
        "Turn off the flashlight" to "halo_flashlight", "Mute the phone" to "halo_ringer",
        "Set brightness to forty percent" to "halo_brightness", "Open Bluetooth settings" to "halo_open_settings",
        "Share hello" to "halo_share_text", "Read clipboard" to "halo_clipboard_read",
        "Copy hello" to "halo_clipboard_write", "Show battery level" to "halo_battery_status",
        "Show network status" to "halo_network_status", "Show device info" to "halo_device_info")

    @Test fun nativeMatrixHasSameExecutionAcrossChatSelectionsAndNoProviderJournal() = runTest {
        val storage = MemoryStorage()
        val store = Store()
        val coordinator = DefaultAgentRunCoordinator(store, backgroundScope)
        val calls = mutableListOf<ToolCall>()
        var blocking: CompletableDeferred<Unit>? = null
        val entered = CompletableDeferred<Unit>()
        val executor = object : ToolExecutor {
            override fun definitions() = commands.values.map { ToolDefinition(it, it, "Native test action") }
            override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
                calls += toolCall
                blocking?.let { entered.complete(Unit); it.await() }
                return ToolExecutionResult.Success(toolCall.id, toolCall.functionName, "Observed execution", ActionTruth.DISPATCHED)
            }
        }
        val registry = ToolRegistry(listOf(executor), executor.definitions().map {
            ToolDescriptor(ToolId(it.id), it, ToolCategory.HOST_CONTROL, security = ToolSecurityPolicy.READ_ONLY)
        })
        val skills = DefaultSkillRegistry(storage, listOf(SkillDescriptor(SkillId("native"), "Native", "Test", SkillCategory.DEVICE, tools = commands.values.toSet())))
        val authorizer = object : ToolAuthorizer { override suspend fun authorize(toolCall: ToolCall) = ToolAuthorization.Authorized(toolCall) }
        val published = mutableListOf<HaloEvent>()
        val bus = object : EventBus {
            override suspend fun publish(event: HaloEvent) { published += event }
            override fun <T : HaloEvent> observe(type: Class<T>): Flow<T> = emptyFlow()
            override fun events(): Flow<HaloEvent> = emptyFlow()
        }
        val delegate = DefaultToolTurnExecutor(registry, authorizer, null, coordinator, store, bus)
        var locked = false
        val service = SkillExecutionService(skills, registry, ToolAvailabilityResolver(registry, skills), delegate, isLocked = { locked }, coordinator = coordinator)
        val dispatcher = NativeCapabilityDispatcher(service, coordinator, store)
        for (provider in listOf("provider.litert.local", "gemini_503", "openai", "openrouter/concrete", "openrouter/free")) {
            storage.putString("active_ai_provider", provider)
            val before = calls.size
            for ((command, expected) in commands) {
                val intent = if (command == "Open Spotify") OpenIntent else requireNotNull(NativeCommandParser.parse(command))
                val entities = if (command == "Open Spotify") listOf(ApplicationEntity("Spotify", 1f)) else emptyList()
                val result = requireNotNull(dispatcher.dispatch(Interpretation(command, intent = intent, entities = entities, confidence = 1f)))
                assertEquals(expected, calls.last().functionName)
                assertEquals(AgentRunState.COMPLETED, store.get(result.runId)?.state)
                assertFalse(store.get(result.runId)!!.steps.any { it is AgentStep.ProviderTurnStarted })
                assertTrue(dispatcher.ownsRun(result.runId))
                assertNull(dispatcher.resume(result.runId)) // No completed action can be replayed.
            }
            assertEquals(commands.size, calls.size - before)
        }
        val sequence = NativeCommandParser.parse("Open Spotify and set volume to 30 percent")!!
        val startSequence = calls.size
        dispatcher.dispatch(Interpretation("Open Spotify and set volume to 30 percent", intent = sequence, confidence = 1f))
        // Open-app acknowledgement is not proof of the app foreground.
        // Without an app-specific screen observation, a second unrelated
        // side effect must not start on mere dispatch.
        assertEquals(listOf("halo_open_app"), calls.drop(startSequence).map { it.functionName })
        dispatcher.dispatch(Interpretation("Set volume to 50 percent", intent = VolumeIntent(percent = 50), confidence = 1f))
        dispatcher.dispatch(Interpretation("Make it 20", intent = NativeReferenceIntent("make it 20"), confidence = 1f))
        assertEquals(20, calls.last().arguments["percent"])
        assertFalse(store.rows.value.any { run -> run.steps.any { it is AgentStep.ProviderTurnStarted } })
        val before = calls.size
        service.check(SkillId("native")); SkillRuntimeAudit(skills, registry, service).inspect()
        assertEquals(before, calls.size)
        skills.setEnabled(SkillId("native"), false)
        val denied = dispatcher.dispatch(Interpretation("Read clipboard", intent = ClipboardReadIntent, confidence = 1f))!!
        assertEquals(before, calls.size)
        assertEquals(AgentRunState.FAILED, store.get(denied.runId)?.state)
        assertTrue(store.get(denied.runId)!!.steps.any { it is AgentStep.ToolRejected })
        skills.setEnabled(SkillId("native"), true)
        locked = true
        val unlockRequired = dispatcher.dispatch(Interpretation("Read clipboard", intent = ClipboardReadIntent, confidence = 1f))!!
        assertEquals(before, calls.size)
        assertTrue(unlockRequired.responseText().contains("REQUIRES_UNLOCK"))

        locked = false
        blocking = CompletableDeferred()
        val board = com.halo.runtime.cognition.blackboard.DefaultBlackboard()
        val conversations = com.halo.runtime.cognition.conversation.InMemoryConversationStore()
        val active = DefaultActiveTurnCoordinator(bus)
        val session = com.halo.runtime.cognition.session.CognitiveSession(
            eventBus=bus, conversationStore=conversations, scope=backgroundScope, blackboard=board,
            activeTurnCoordinator=active,
            agentRecoveryExecutor=DefaultAgentRecoveryExecutor(store,coordinator,registry,bus,
                com.halo.runtime.cognition.agent.recovery.DefaultAgentRecoveryPlanner()),
            turnExecutor=CognitiveTurnExecutor(object : CognitiveTurnPreparer {
                override suspend fun prepare(turn: CognitiveTurnRequest): PreparedCognitiveTurn = error("Native action must not prepare inference")
            },DefaultProviderTurnExecutor(bus),delegate,coordinator,store,bus),
            responseCommitter=DefaultCognitiveResponseCommitter(conversationStore=conversations,
                agentRunCoordinator=coordinator,eventBus=bus,runStore=store),
            nativeDispatcher=dispatcher)
        runCurrent()
        val beforeBlocked = calls.size
        board.publish(com.halo.runtime.cognition.blackboard.Knowledge(
            id=com.halo.runtime.cognition.blackboard.KnowledgeId("slow-native"),
            payload=com.halo.runtime.cognition.blackboard.InterpretationKnowledge(Interpretation("Set a timer for two minutes",
                intent=NativeCommandParser.parse("Set a timer for two minutes")!!,confidence=1f,
                metadata=mapOf("interactionId" to "native-owned"))),
            confidence=com.halo.runtime.cognition.blackboard.Confidence(1f,1f,1f),
            source=com.halo.runtime.cognition.blackboard.KnowledgeSource.USER,
            createdAt=kotlinx.datetime.Clock.System.now()))
        runCurrent()
        assertTrue(session.ownsInteraction("native-owned"))
        entered.await()
        assertEquals(beforeBlocked+1,calls.size)
        assertTrue(published.filterIsInstance<CognitiveRequestStarted>().any { it.interactionId=="native-owned" })
        advanceTimeBy(20_000);runCurrent()
        assertTrue(session.ownsInteraction("native-owned"))
        session.cancelInteraction("different");runCurrent()
        assertTrue(session.ownsInteraction("native-owned"))
        val ownedJob = active.activeTurn.value!!.job
        session.cancelInteraction("native-owned");runCurrent();ownedJob.join();runCurrent()
        assertFalse(session.ownsInteraction("native-owned"))
        assertEquals(beforeBlocked+1,calls.size)
        assertTrue(store.rows.value.last().state.isTerminal)
    }

    @Test fun scriptedPhoneActionsAreProviderIndependentAndStopOnUnconfirmedUiEffects() = runTest {
        val store = Store()
        val coordinator = DefaultAgentRunCoordinator(store, backgroundScope)
        val calls = mutableListOf<ToolCall>()
        var confirmUi = true
        val toolIds = listOf("halo_open_app", "halo_ui_click", "halo_ui_set_text", "halo_ui_scroll", "halo_ui_global_action")
        val executor = object : ToolExecutor {
            override fun definitions() = toolIds.map { ToolDefinition(it, it, "Fake semantic host") }
            override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
                calls += toolCall
                val truth = if (toolCall.functionName == "halo_open_app" || !confirmUi)
                    ActionTruth.DISPATCHED else ActionTruth.CONFIRMED
                return ToolExecutionResult.Success(toolCall.id, toolCall.functionName, "Receipt", truth)
            }
        }
        val registry = ToolRegistry(listOf(executor), executor.definitions().map {
            ToolDescriptor(ToolId(it.id), it, ToolCategory.HOST_CONTROL, security = ToolSecurityPolicy.READ_ONLY)
        })
        val skills = DefaultSkillRegistry(MemoryStorage(), listOf(SkillDescriptor(SkillId("phone-script"),
            "Phone script", "Test", SkillCategory.DEVICE, tools = toolIds.toSet())))
        val authorizer = object : ToolAuthorizer {
            override suspend fun authorize(toolCall: ToolCall) = ToolAuthorization.Authorized(toolCall)
        }
        val bus = object : EventBus {
            override suspend fun publish(event: HaloEvent) {}
            override fun <T : HaloEvent> observe(type: Class<T>): Flow<T> = emptyFlow()
            override fun events(): Flow<HaloEvent> = emptyFlow()
        }
        val delegate = DefaultToolTurnExecutor(registry, authorizer, null, coordinator, store, bus)
        val service = SkillExecutionService(skills, registry, ToolAvailabilityResolver(registry, skills),
            delegate, coordinator = coordinator)
        val expectedApps = mutableListOf<String?>()
        var stopBeforeTarget: String? = null
        var offerForeground = true
        lateinit var dispatcher: NativeCapabilityDispatcher
        dispatcher = NativeCapabilityDispatcher(service, coordinator, store,
            resolveScreenTarget = { screen, expectedApp ->
                expectedApps += expectedApp
                if (stopBeforeTarget == screen.target) {
                    stopBeforeTarget = null
                    dispatcher.dispatch(Interpretation("Stop", intent = NativeStopIntent, confidence = 1f))
                }
                NativeScreenTargetResolution.Ready(
                    when (screen.verb) {
                        NativeScreenVerb.ENTER_TEXT -> "halo_ui_set_text"
                        NativeScreenVerb.SCROLL_FORWARD, NativeScreenVerb.SCROLL_BACKWARD -> "halo_ui_scroll"
                        NativeScreenVerb.BACK -> "halo_ui_global_action"
                        else -> "halo_ui_click"
                    },
                    mapOf("snapshotId" to "fresh-snapshot", "token" to "exact-token") +
                        when (screen.verb) {
                            NativeScreenVerb.ENTER_TEXT -> mapOf("text" to (screen.value ?: ""))
                            NativeScreenVerb.SCROLL_FORWARD -> mapOf("direction" to "forward")
                            NativeScreenVerb.SCROLL_BACKWARD -> mapOf("direction" to "backward")
                            NativeScreenVerb.BACK -> mapOf("action" to "back")
                            else -> mapOf("expectedEffect" to "NONE")
                        },
                    foreground = if (offerForeground && expectedApp != null)
                        NativeForegroundObservation(expectedApp, "com.example.phone", System.currentTimeMillis())
                        else null
                )
            })
        val intent = requireNotNull(NativeCommandParser.parse(
            "Open Gmail then tap Search and type invoice into Search"))
        val result = dispatcher.dispatch(Interpretation("Phone script", intent = intent, confidence = 1f))
        assertEquals(NativeActionOutcome.CONFIRMED, result?.outcome)
        assertEquals(listOf("halo_open_app", "halo_ui_click", "halo_ui_set_text"),
            calls.map { it.functionName })
        assertEquals(listOf("Gmail", "Gmail"), expectedApps)
        val firstWorkflow = NativeWorkflowJournal.progress(store.rows.value).single()
        assertNotNull(firstWorkflow.parentRunId)
        assertEquals(AgentRunState.COMPLETED, firstWorkflow.parentState)
        assertEquals(3, firstWorkflow.steps.size)
        assertEquals(3, firstWorkflow.confirmed)
        assertTrue(firstWorkflow.steps.first().foregroundVerified)
        // The original launch tool receipt stays DISPATCHED. Foreground proof
        // lives independently on the parent and cannot rewrite action history.
        assertEquals(ActionTruth.DISPATCHED,
            store.get(firstWorkflow.steps.first().runId)!!.steps
                .filterIsInstance<AgentStep.ToolOutcomeRecorded>().last().actionTruth)
        assertEquals(1, NativeWorkflowJournal.foregroundVerifiedOrdinals(
            store.get(firstWorkflow.parentRunId!!)!!, firstWorkflow.workflowId).size)
        assertFalse(firstWorkflow.autoResumeAllowed)
        assertFalse(store.rows.value.any { run -> run.steps.any { it is AgentStep.ProviderTurnStarted } })
        assertEquals(0, store.rows.value.flatMap { it.steps }.filterIsInstance<AgentStep.DirectCommandAuthorityRecorded>()
            .count { it.authority.toolId.startsWith("halo_ui_") })

        calls.clear()
        expectedApps.clear()
        val scrollFlow = requireNotNull(NativeCommandParser.parse(
            "Open Gmail then scroll down then tap Search"))
        val scrolled = dispatcher.dispatch(Interpretation("Phone scroll script",
            intent = scrollFlow, confidence = 1f))
        assertEquals(NativeActionOutcome.CONFIRMED, scrolled?.outcome)
        assertEquals(listOf("halo_open_app", "halo_ui_scroll", "halo_ui_click"),
            calls.map { it.functionName })
        assertEquals(listOf("Gmail", "Gmail"), expectedApps)

        calls.clear()
        expectedApps.clear()
        val backFlow = requireNotNull(NativeCommandParser.parse("Open Gmail then go back"))
        val back = dispatcher.dispatch(Interpretation("Back script", intent = backFlow, confidence = 1f))
        assertEquals(NativeActionOutcome.CONFIRMED, back?.outcome)
        assertEquals(listOf("halo_open_app", "halo_ui_global_action"), calls.map { it.functionName })

        calls.clear()
        val verifiedOnly = NativeCommandSequenceIntent(listOf(
            NativeScreenActionIntent(NativeScreenVerb.TAP, "Compose"),
            NativeScreenActionIntent(NativeScreenVerb.TAP, "Search")
        ))
        val verified = dispatcher.dispatch(Interpretation(
            "Two verified controls", intent = verifiedOnly, confidence = 1f))
        assertEquals(NativeActionOutcome.CONFIRMED, verified?.outcome)
        val completedOwner = NativeWorkflowJournal.progress(store.rows.value)
            .single { it.steps.size == 2 && it.steps.all { step ->
                step.marker.kind == "TAP" && step.outcome == NativeActionOutcome.CONFIRMED
            } }
        assertEquals(AgentRunState.COMPLETED, completedOwner.parentState)

        // User Stop lands during the fresh-screen resolver, after the first
        // app launch but before the next UI action is authorized.
        calls.clear()
        stopBeforeTarget = "Search"
        val interruptedByStop = NativeCommandSequenceIntent(listOf(
            NativeOpenApplicationIntent("Gmail"),
            NativeScreenActionIntent(NativeScreenVerb.TAP, "Search")
        ))
        dispatcher.dispatch(Interpretation("Stop during app settle",
            intent = interruptedByStop, confidence = 1f))
        assertEquals(listOf("halo_open_app"), calls.map { it.functionName })
        assertTrue(NativeWorkflowJournal.progress(store.rows.value).any { it.parentState ==
            AgentRunState.CANCELLED && it.total == 2 })

        calls.clear()
        offerForeground = false
        val unobserved = dispatcher.dispatch(Interpretation(
            "App not observed", intent = intent, confidence = 1f))
        assertEquals(listOf("halo_open_app"), calls.map { it.functionName })
        assertNotNull(unobserved)
        val withoutProof = NativeWorkflowJournal.progress(store.rows.value).last()
        assertEquals(AgentRunState.INTERRUPTED, withoutProof.parentState)
        assertEquals(0, withoutProof.confirmed)
        assertTrue(store.get(withoutProof.parentRunId!!)!!.steps
            .none { it is AgentStep.PlanCreated &&
                it.planId.startsWith("halo.phone.foreground.v1:") })
        offerForeground = true

        calls.clear()
        confirmUi = false
        val incomplete = dispatcher.dispatch(Interpretation("Phone script again", intent = intent, confidence = 1f))
        assertEquals(NativeActionOutcome.DISPATCHED, incomplete?.outcome)
        assertEquals(listOf("halo_open_app", "halo_ui_click"), calls.map { it.functionName })
        val flowViews = NativeWorkflowJournal.progress(store.rows.value)
        assertEquals(7, flowViews.size)
        assertTrue(flowViews.any { it.steps.size == 2 &&
            it.steps.last().outcome == NativeActionOutcome.DISPATCHED })

        // No durable workflow position, no external effect: a local journal
        // failure must not silently execute the requested phone script.
        store.rejectWorkflowCheckpoints = true
        calls.clear()
        val blocked = dispatcher.dispatch(Interpretation(
            "No checkpoint", intent = intent, confidence = 1f))
        assertEquals(NativeActionOutcome.WAITING_FOR_INPUT, blocked?.outcome)
        assertTrue(calls.isEmpty())
        assertTrue(store.rows.value.any { it.state == AgentRunState.FAILED &&
            it.failureReason?.contains("checkpoint") == true })
    }

    @Test fun scriptAdvanceRequiresConfirmedEffectsExceptVerifiedAppScreenHandoff() {
        val open = NativeOpenApplicationIntent("Gmail")
        val tap = NativeScreenActionIntent(NativeScreenVerb.TAP, "Search")
        val alarm = TimerIntent(120)
        assertTrue(canAdvanceNativeScript(tap, alarm, NativeActionOutcome.CONFIRMED, null))
        assertFalse(canAdvanceNativeScript(tap, alarm, NativeActionOutcome.DISPATCHED, "Gmail"))
        assertFalse(canAdvanceNativeScript(alarm, tap, NativeActionOutcome.DISPATCHED, null))
        assertFalse(canAdvanceNativeScript(open, alarm, NativeActionOutcome.DISPATCHED, "Gmail"))
        assertFalse(canAdvanceNativeScript(open, tap, NativeActionOutcome.DISPATCHED, "Spotify"))
        assertTrue(canAdvanceNativeScript(open, tap, NativeActionOutcome.DISPATCHED, "Gmail"))
        assertFalse(canAdvanceNativeScript(open, tap, NativeActionOutcome.UNKNOWN, "Gmail"))
        assertFalse(canAdvanceNativeScript(open, tap, NativeActionOutcome.REJECTED, "Gmail"))
    }

    @Test fun startupInterruptsParentAndScriptedStepWithoutOfferingStaleApproval() = runTest {
        val store = Store()
        val coordinator = DefaultAgentRunCoordinator(store, backgroundScope)
        val parentId = coordinator.start(AgentRequest(
            InteractionId("scripted-command"), "Phone workflow", kind = AgentRunKind.NATIVE))
        assertTrue(coordinator.recordStep(NativeWorkflowJournal.parentCheckpoint(
            parentId, "restart-flow", 2)))
        coordinator.transition(parentId, AgentRunState.EXECUTING)

        val childId = coordinator.start(AgentRequest(
            InteractionId("scripted-command"), "Native phone script step", kind = AgentRunKind.NATIVE))
        assertTrue(coordinator.recordStep(NativeWorkflowJournal.checkpoint(
            childId, "restart-flow", 1, 2, "TAP")))
        coordinator.transition(childId, AgentRunState.EXECUTING)
        val input = AgentInputRequest.Approval(
            AgentInputRequestId("request"), childId, java.time.Instant.now(),
            invocationId = ToolInvocationId("invocation"), toolId = ToolId("halo_ui_click"),
            action = "Tap control", risk = "SENSITIVE", argumentsDigest = "opaque")
        assertTrue(coordinator.recordStep(AgentStep.InputRequested(
            AgentStepId("input"), childId, java.time.Instant.now(), input.id, input)))
        coordinator.transition(childId, AgentRunState.WAITING_FOR_APPROVAL)

        val decisions = coordinator.prepareOutstandingRunsForRecovery()
        assertTrue(decisions[parentId] is RecoveryClassification.Unrecoverable)
        assertTrue(decisions[childId] is RecoveryClassification.Unrecoverable)
        assertEquals(AgentRunState.INTERRUPTED, store.get(parentId)?.state)
        assertEquals(AgentRunState.INTERRUPTED, store.get(childId)?.state)
        assertFalse(store.nonTerminalRuns().any { it.id == childId || it.id == parentId })
        assertTrue(store.get(childId)!!.steps.none { it is AgentStep.ToolExecutionClaimed })
    }

    @Test fun screenResultNeverSpeaksUntrustedAccessibilityTextOrTypedContent() {
        val sensitive = "SECRET_VALUE_DO_NOT_REPEAT"
        val request = NativeCapabilityRequest("conversation", CapabilityId("host.ui.semantic"),
            "halo_ui_set_text", mapOf("text" to sensitive), "NativeScreenAction")
        val completed = ToolTurnResult.Completed(
            AiResponse(text = sensitive, providerId = "host"), false,
            listOf(AIMessage(Role.TOOL, sensitive))
        )
        for (outcome in NativeActionOutcome.entries) {
            val result = NativeCapabilityResult(request, AgentRunId("screen-result"),
                completed, outcome, "UNTRUSTED_SCREEN_CONTENT: $sensitive")
            assertFalse("Action $outcome must not speak the screen content",
                result.responseText().contains(sensitive))
            assertFalse(result.responseText().contains("UNTRUSTED_SCREEN_CONTENT"))
        }
        val rejected = NativeCapabilityResult(request.copy(operation = "halo_ui_click"),
            AgentRunId("screen-rejected"), completed, NativeActionOutcome.REJECTED,
            "untrusted on-screen error SECRET_VALUE_DO_NOT_REPEAT")
        assertFalse(rejected.responseText().contains(sensitive))
    }

    @Test fun stopCancelsAllOutstandingNativeRunsButLeavesScheduledRoutinesAlone() = runTest {
        val store = Store()
        val coordinator = DefaultAgentRunCoordinator(store, backgroundScope)
        val bus = object : EventBus {
            override suspend fun publish(event: HaloEvent) {}
            override fun <T : HaloEvent> observe(type: Class<T>): Flow<T> = emptyFlow()
            override fun events(): Flow<HaloEvent> = emptyFlow()
        }
        val registry = ToolRegistry(emptyList())
        val skills = DefaultSkillRegistry(MemoryStorage(), emptyList())
        val authorizer = object : ToolAuthorizer {
            override suspend fun authorize(toolCall: ToolCall) = ToolAuthorization.Authorized(toolCall)
        }
        val delegate = DefaultToolTurnExecutor(registry, authorizer, null, coordinator, store, bus)
        val service = SkillExecutionService(skills, registry, ToolAvailabilityResolver(registry, skills),
            delegate, coordinator = coordinator)
        val dispatcher = NativeCapabilityDispatcher(service, coordinator, store)
        val one = coordinator.start(AgentRequest(InteractionId("one"), "Native 1", kind = AgentRunKind.NATIVE))
        val two = coordinator.start(AgentRequest(InteractionId("two"), "Native 2", kind = AgentRunKind.NATIVE))
        val routine = coordinator.start(AgentRequest(InteractionId("routine"), "Scheduled", kind = AgentRunKind.ROUTINE))
        coordinator.transition(one, AgentRunState.EXECUTING)
        coordinator.transition(two, AgentRunState.EXECUTING)
        coordinator.transition(two, AgentRunState.WAITING_FOR_APPROVAL)
        dispatcher.dispatch(Interpretation("Stop", intent = NativeStopIntent, confidence = 1f))
        assertEquals(AgentRunState.CANCELLED, store.get(one)?.state)
        assertEquals(AgentRunState.CANCELLED, store.get(two)?.state)
        assertFalse(store.get(routine)!!.state.isTerminal)
    }

    private class Store : AgentRunStore {
        val rows = MutableStateFlow<List<AgentRun>>(emptyList())
        var rejectWorkflowCheckpoints = false
        override fun observeRuns() = rows
        override suspend fun get(runId: AgentRunId) = rows.value.find { it.id == runId }
        override suspend fun create(run: AgentRun) { rows.value += run }
        override suspend fun update(run: AgentRun) { rows.value = rows.value.map { if (it.id == run.id) run else it } }
        override suspend fun appendStep(step: AgentStep): Boolean {
            if (rejectWorkflowCheckpoints && step is AgentStep.PlanCreated &&
                step.planId.startsWith("halo.phone.workflow.v1:")) return false
            val run = get(step.runId) ?: return false
            if (run.steps.any { it.id == step.id }) return false
            update(run.copy(steps = run.steps + step)); return true
        }
        override suspend fun nonTerminalRuns() = rows.value.filterNot { it.state.isTerminal }
        override suspend fun claimResume(runId: AgentRunId, expectedState: AgentRunState): Boolean {
            val run = get(runId) ?: return false
            if (run.state != expectedState || run.state.isTerminal) return false
            update(run.copy(state = AgentRunState.EXECUTING)); return true
        }
        override suspend fun resolveInputIfPending(requestId: AgentInputRequestId, resolution: AgentInputResolution) = false
    }
    private class MemoryStorage : Storage {
        val data = mutableMapOf<String, Any?>()
        override fun getString(key: String, defaultValue: String?) = data[key] as? String ?: defaultValue
        override fun putString(key: String, value: String?) { data[key] = value }
        override fun getInt(key: String, defaultValue: Int) = data[key] as? Int ?: defaultValue
        override fun putInt(key: String, value: Int) { data[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean) = data[key] as? Boolean ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) { data[key] = value }
        override fun remove(key: String) { data.remove(key) }
        override fun clear() { data.clear() }
    }
}
