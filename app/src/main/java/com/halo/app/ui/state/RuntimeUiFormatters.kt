package com.halo.app.ui.state

import com.halo.runtime.cognition.agent.*
import com.halo.sdk.runtime.resource.*
import com.halo.runtime.cognition.tools.*

object RuntimeUiFormatters {

    fun formatResource(resourceId: ResourceId): String = when (resourceId.value) {
        HaloResourceIds.AudioInput.value -> "Microphone"
        HaloResourceIds.AudioOutput.value -> "Audio output"
        HaloResourceIds.AudioSession.value -> "Audio session"
        HaloResourceIds.CameraSensor.value -> "Camera"
        HaloResourceIds.CameraCapture.value -> "Still capture"
        HaloResourceIds.VideoCapture.value -> "Video recording"
        HaloResourceIds.DisplayOutput.value -> "Display"
        HaloResourceIds.MediaTransfer.value -> "Media transfer"
        HaloResourceIds.ScreenCapture.value -> "Screen capture"
        HaloResourceIds.ComputeInference.value -> "Local model"
        HaloResourceIds.ComputeAsr.value -> "Speech recognition"
        HaloResourceIds.ComputeHeavy.value -> "Heavyweight model"
        HaloResourceIds.MobilityDrive.value -> "Drive"
        HaloResourceIds.ManipulatorMotion.value -> "Manipulator"
        else -> resourceId.value
    }

    fun formatCapability(capabilityId: CapabilityId): String = when (capabilityId.value) {
        "host.application.launch" -> "Open applications"
        "host.application.enumerate" -> "List applications"
        "host.web.open" -> "Open web pages"
        "host.web.search" -> "Web search"
        "host.navigation.open" -> "Open navigation"
        "host.dialer.open" -> "Open dialer"
        "host.contacts.read" -> "Read contacts"
        "host.alarm.set" -> "Set alarms"
        "host.notification.read" -> "Read notifications"
        "host.volume.control" -> "Control volume"
        "host.flashlight.control" -> "Control flashlight"
        "host.audio.input" -> "Microphone"
        "host.audio.output" -> "Speaker"
        "host.camera.still" -> "Built-in camera"
        "device.audio.input" -> "Microphone"
        "device.audio.output" -> "Speaker"
        "device.camera.still" -> "Camera"
        "device.media.transfer" -> "Media sync"
        "device.battery.read" -> "Battery"
        "device.mobility" -> "Drive"
        "device.manipulator" -> "Manipulator"
        "workspace.read" -> "Read workspace"
        "workspace.write" -> "Write workspace"
        else -> capabilityId.value.removePrefix("host.").removePrefix("device.").replace(".", " ")
            .replaceFirstChar { it.uppercase() }
    }

    fun formatRunState(state: AgentRunState): String = when (state) {
        AgentRunState.CREATED -> "Created"
        AgentRunState.PLANNING -> "Preparing"
        AgentRunState.EXECUTING -> "Working"
        AgentRunState.WAITING_FOR_CLARIFICATION -> "Needs your answer"
        AgentRunState.WAITING_FOR_APPROVAL -> "Needs your approval"
        AgentRunState.WAITING_FOR_CAPABILITY -> "Waiting for device"
        AgentRunState.WAITING_FOR_RESOURCE -> "Waiting for resource"
        AgentRunState.SUSPENDED -> "Task paused"
        AgentRunState.COMPLETED -> "Completed"
        AgentRunState.FAILED -> "Failed"
        AgentRunState.CANCELLED -> "Cancelled"
        AgentRunState.INTERRUPTED -> "Interrupted"
    }

    fun formatStepTitle(step: AgentStep): String = when (step) {
        is AgentStep.SemanticAppOperationRecorded -> step.receipt.semanticAction.value.replace('_', ' ')
        is AgentStep.UiTransactionRecorded -> "Screen transaction"
        is AgentStep.DelegationResultRecorded -> "Delegated findings recorded"
        is AgentStep.ContextUsed -> "Context used"
        is AgentStep.PlanCreated -> "Plan created"
        is AgentStep.ClarificationRequested -> "Question asked"
        is AgentStep.ApprovalRequested -> "Approval requested"
        is AgentStep.ToolRequested -> "Action requested"
        is AgentStep.ToolDispatched, is AgentStep.ToolExecutionClaimed -> "Action started"
        is AgentStep.ResourceBlocked -> "Waiting for resource"
        is AgentStep.ToolOutcomeRecorded, is AgentStep.ToolCompleted -> "Action completed"
        is AgentStep.ToolRejected -> "Action rejected"
        is AgentStep.ToolFailed -> "Action failed"
        is AgentStep.ResponseProduced -> "Response produced"
        is AgentStep.RunPrepared -> "Run initialized"
        is AgentStep.ProviderTurnStarted -> "Thinking"
        is AgentStep.ProviderTurnCompleted -> "Response prepared"
        is AgentStep.ToolInvocationPrepared -> "Action prepared"
        is AgentStep.InputRequested -> "Input requested"
        is AgentStep.InputResolved -> "Input received"
        is AgentStep.ContinuationPrepared -> "Resuming turn"
        is AgentStep.ResponseCommitted -> "Response saved"
        is AgentStep.RecoveryClaimed -> "Recovering"
        is AgentStep.RunGuardTriggered -> "Task stopped"
    }

    fun formatRiskClass(risk: ToolRiskClass): String = when (risk) {
        ToolRiskClass.READ_ONLY -> "Reads information"
        ToolRiskClass.REVERSIBLE -> "Can change state (reversible)"
        ToolRiskClass.EXTERNAL_SIDE_EFFECT -> "Affects external service"
        ToolRiskClass.SENSITIVE -> "Sensitive operation"
        ToolRiskClass.DESTRUCTIVE -> "Cannot be undone"
    }
}
