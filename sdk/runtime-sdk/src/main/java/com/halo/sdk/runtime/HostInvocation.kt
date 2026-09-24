package com.halo.sdk.runtime

import java.util.UUID

enum class InvocationSource { ANDROID_ASSISTANT, HALO_UI, WIDGET, HARDWARE_BUTTON, WEARABLE, SHARE }

data class HostInvocation(
    val interactionId: String,
    val source: InvocationSource,
    val invokedAtMs: Long,
    val inputDeviceId: Int? = null,
    val requestedAssistContext: Boolean,
    val requestedScreenshot: Boolean,
)

data class RectValue(val left: Int, val top: Int, val right: Int, val bottom: Int)

data class ScreenContextNode(
    val text: String?,
    val contentDescription: String?,
    val className: String?,
    val viewId: String?,
    val clickable: Boolean,
    val editable: Boolean,
    val bounds: RectValue?,
)

data class ScreenContextSnapshot(
    val packageName: String?,
    val activityName: String?,
    val title: String?,
    val webUri: String?,
    val nodes: List<ScreenContextNode>,
    val capturedAtMs: Long,
    val provenance: String = "ANDROID_ASSIST",
    val trust: ContextTrust = ContextTrust.UNTRUSTED_CONTENT,
)

enum class ContextTrust { TRUSTED_RUNTIME, UNTRUSTED_CONTENT }

data class AssistantInvocationStarted(
    val invocation: HostInvocation,
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Long = invocation.invokedAtMs,
) : HaloEvent

data class ScreenContextCaptured(
    val interactionId: String,
    val snapshot: ScreenContextSnapshot,
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Long = snapshot.capturedAtMs,
) : HaloEvent

data class AssistantInvocationEnded(
    val interactionId: String,
    val reason: String,
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis(),
) : HaloEvent
