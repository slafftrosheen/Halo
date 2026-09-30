package com.halo.app.diagnostics

import android.content.Context
import android.os.Build
import com.halo.app.di.ManualDependencyGraph
import com.halo.providers.litert.LiteRtLocalProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * DiagnosticsExport aggregates system state, provider statuses, and connected device
 * states into a single exportable text report for TestFlight participants.
 *
 * MINING-P0-001 adds three content-free sections: local LiteRT execution truth, native
 * residency-vs-execution lease facts, and interaction audio continuity. Nothing here may ever
 * contain raw prompts, generated conversation text, user speech, full tool arguments, secrets,
 * tokens or credentials.
 */
object DiagnosticsExport {

    fun generateReport(context: Context): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(context.cacheDir, "halo_diagnostics_$timestamp.txt")
        
        val sb = StringBuilder()
        sb.append("==== HALO TESTFLIGHT DIAGNOSTICS ====\n")
        sb.append("Timestamp: ${Date()}\n")
        sb.append("OS: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
        sb.append("Device: ${Build.MANUFACTURER} ${Build.MODEL}\n\n")

        sb.append("--- PROVIDER STATUS ---\n")
        val router = ManualDependencyGraph.aiProviderRouter
        
        sb.append("Active LLM: ${router.selectedProviderId.value ?: "None"}\n")
        sb.append("Selected model: ${router.selectedModelId.value ?: "None"}\n")
        sb.append("Providers:\n")
        val providers = router.getAllProviders()

        providers.forEach { p ->
            val status = p.status.value
            sb.append("  - ${p.displayName} [${p.id}]: ${status.state}\n")
        }
        sb.append("\n")

        sb.append("--- PROVIDER CONTROL ---\n")
        ManualDependencyGraph.providerControlRepository.rows.value.forEach { row ->
            sb.append("${row.descriptor.providerId}: configured=${row.configured}; selected=${row.selected}; connection=${row.connection}; catalogue=${row.catalogue}; models=${row.modelCount}\n")
        }
        val tts = ManualDependencyGraph.speechSynthesisRegistry
        sb.append("TTS selection=${tts.selector.ttsSelection.value.id}; registered=${tts.providers.map { it.metadata.id }}\n")
        sb.append("Cloud synthesis fallback=${ManualDependencyGraph.storage.getBoolean(com.halo.app.ai.SpeechSynthesisRegistry.FALLBACK, true)}; independent of audio-route fallback\n")
        appendLocalLiteRt(sb, providers)
        appendNativeResources(sb)
        appendAudioContinuity(sb)
        sb.append("--- AUTOMATION ---\n")
        sb.append("Sources: ${ManualDependencyGraph.automationRuntime.registry.availability()}\n")
        sb.append("State: ${com.halo.runtime.cognition.routine.AutomationDiagnostics.snapshot.value}\n")
        sb.append("Durable occurrence claims; event definitions require reviewed grants; runtime-only triggers miss process-offline events\n")
        sb.append("--- MEMORY & LEARNING ---\n")
        sb.append("Learning: ${ManualDependencyGraph.learningPreferences()}\n")
        sb.append("Counts/status: ${com.halo.runtime.cognition.memory.PersonalMemoryDiagnostics.snapshot.value}\n")
        sb.append("Canonical Room memory; bounded episodes and proposals; lexical retrieval available; semantic encoder not installed\n")
        sb.append("Reflection status is auxiliary and never changes completed AgentRun truth; content-free diagnostics only\n")
        sb.append("--- ATTENTION ---\n")
        sb.append("Initiative: ${com.halo.runtime.cognition.attention.AttentionInitiativeLevel.fromStored(ManualDependencyGraph.storage.getString("attention_initiative"))}\n")
        sb.append("Queue: ${ManualDependencyGraph.attentionQueue.pending.value.size}; DND respected: ${ManualDependencyGraph.storage.getBoolean("attention_respect_dnd", true)}\n")
        sb.append("Receipt outcomes: ${ManualDependencyGraph.attentionQueue.receipts.value.groupingBy { it.outcome }.eachCount()}\n")
        sb.append("--- PHONE OPERATOR V1 ---\n")
        sb.append("Metrics: ${com.halo.runtime.cognition.ui.UiOperatorDiagnostics.metrics.value}\n")
        com.halo.runtime.cognition.ui.UiOperatorDiagnostics.lastReceipt.value?.let {
            sb.append("Last receipt: operation=${it.operation}; dispatch=${it.dispatchStatus}; verification=${it.verificationStatus}; truth=${it.actionTruth}; change=${it.changeKind}; settle=${it.settleStatus}; settleMs=${it.settleMs}\n")
        }
        sb.append("--- LONG TASKS ---\n")
        val budget = com.halo.runtime.cognition.context.ContextDiagnostics.composition.value
        budget?.let {
            sb.append("Context: window=${it.budget.modelContextTokens}; output=${it.budget.reservedOutputTokens}; system=${it.budget.reservedSystemTokens}; request=${it.requestTokens}; remaining=${it.remainingTokens}\n")
            sb.append("Segments: ${it.segmentTokens}\n")
        }
        sb.append("Compactions: ${com.halo.runtime.cognition.context.ContextDiagnostics.compactionCount.get()}\n")
        sb.append("Payloads (last passive snapshot): ${com.halo.runtime.cognition.delegation.LongTaskDiagnostics.payloads.value ?: "not observed"}\n")
        sb.append("Delegation: ${com.halo.runtime.cognition.delegation.LongTaskDiagnostics.delegation.value}\n\n")
        sb.append("--- MULTIMODAL ---\n")
        val visionBinding = router.roleBinding(com.halo.sdk.provider.AiModelRole.VISION)
        sb.append("Vision binding: ${visionBinding.selection} ${visionBinding.providerId ?: "automatic/inherited"}/${visionBinding.modelId ?: "automatic/inherited"}\n")
        sb.append("Vision target: ${com.halo.app.capture.MultimodalDiagnostics.visionTarget}\n")
        sb.append("Vision evidence: ${com.halo.app.capture.MultimodalDiagnostics.visionEvidence}\n")
        val preparedImage = ManualDependencyGraph.imageInputPreparer.lastSummary
        preparedImage?.let {
            sb.append("Image preparation: ${it.sourceBytes} -> ${it.outputBytes} bytes; ${it.outputWidth}x${it.outputHeight}; ${it.mimeType}; transformed=${it.transformed}\n")
            sb.append("Last visual source: ${it.sourceRef?.provenance ?: "provider-boundary image"}\n")
        }
        sb.append("Audio strategy: DEDICATED_STT; audio model disabled\n")
        sb.append("Capability states: ${com.halo.app.capture.MultimodalDiagnostics.capabilityStates}\n")
        sb.append("Capture retention: interaction-owned; maximum age 15 minutes; no media contents exported\n\n")

        sb.append("--- DEVICE SESSIONS ---\n")
        val sessionManager = ManualDependencyGraph.deviceSessionManager
        val sessions = sessionManager.activeSessions.value
        if (sessions.isEmpty()) {
            sb.append("No active device sessions.\n")
        } else {
            sessions.forEach { s ->
                sb.append("  - ${s.identity.displayName} (${s.identity.deviceId})\n")
                sb.append("    Type: ${s.identity.deviceType}\n")
                sb.append("    State: ${s.state.value}\n")
            }
        }
        sb.append("\n")

        sb.append("--- END OF REPORT ---\n")
        
        file.writeText(sb.toString())
        return file
    }

    /**
     * LEVEL 0/1/2 facts for the selected on-device artifact. Reads published state only: an export
     * must never trigger a load, a probe or any inference.
     */
    private fun appendLocalLiteRt(
        sb: StringBuilder,
        providers: List<*>,
    ) {
        sb.append("--- LOCAL LITERT ---\n")
        val local = providers.filterIsInstance<LiteRtLocalProvider>().firstOrNull()
        val diagnostics = local?.diagnosticsSnapshot()
        if (local == null || diagnostics == null) {
            sb.append("No on-device model selected.\n\n")
            return
        }
        diagnostics.exportLines().forEach { line -> sb.append("  $line\n") }
        local.conversationDiagnostics.value?.let { conversation ->
            // Bounded metadata only: no preface text, no generated text.
            sb.append("  conversation=${conversation.summaryLine()}\n")
        }
        sb.append("\n")
    }

    /**
     * Residency is not execution: `executionLeaseHeld=false` on a RESIDENT owner is the expected
     * steady state that explains ASR/LLM coexistence.
     */
    private fun appendNativeResources(sb: StringBuilder) {
        sb.append("--- NATIVE RESOURCES ---\n")
        val resources = ManualDependencyGraph.runtimeSnapshotManager.snapshot.value.nativeResources
        if (resources.isEmpty()) {
            sb.append("No native resource owners registered.\n\n")
            return
        }
        resources.forEach { snapshot ->
            sb.append("  - owner=${snapshot.ownerId} kind=${snapshot.kind}\n")
            sb.append("    state=${snapshot.state} executionLeaseHeld=${snapshot.executionLeaseHeld}\n")
            sb.append(
                "    resource=${snapshot.executionResourceId ?: "none"} " +
                    "waitMs=${snapshot.executionWaitMs ?: "none"} " +
                    "reason=${snapshot.reason ?: "none"}\n",
            )
        }
        sb.append("\n")
    }

    /**
     * Interaction audio continuity. Display labels only: never MAC addresses or raw endpoint ids.
     */
    private fun appendAudioContinuity(sb: StringBuilder) {
        sb.append("--- AUDIO CONTINUITY ---\n")
        val affinity = ManualDependencyGraph.haloSpeechOutputCoordinator.session.affinity.value
        if (affinity == null) {
            sb.append("No interaction audio affinity recorded yet.\n\n")
            return
        }
        sb.append("  interaction=${affinity.interactionId.take(24)}\n")
        sb.append("  origin=${affinity.originLabel}\n")
        sb.append("  intendedTarget=${affinity.intendedTargetLabel}\n")
        sb.append("  actualEndpoint=${affinity.resolvedEndpointLabel ?: "unresolved"}\n")
        sb.append("  fallback=${affinity.fallbackPolicyLabel}\n")
        sb.append("  routeState=${affinity.routeState}\n")
        sb.append("  boundAtMs=${affinity.boundAtMs} lastTransitionAtMs=${affinity.lastTransitionAtMs}\n\n")
    }
}
