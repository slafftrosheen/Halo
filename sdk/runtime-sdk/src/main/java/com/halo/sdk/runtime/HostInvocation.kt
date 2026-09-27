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

enum class UiSemanticType {
    TEXT, BUTTON, INPUT, CHECKBOX, SWITCH, IMAGE, LIST, LIST_ITEM, SCROLL_CONTAINER, LINK, CONTAINER, UNKNOWN
}

data class ScreenContextNode(
    val text: String?,
    val contentDescription: String?,
    val className: String?,
    val viewId: String?,
    val clickable: Boolean,
    val editable: Boolean,
    val bounds: RectValue?,
    val scrollable: Boolean = false,
    val checkable: Boolean = false,
    val checked: Boolean = false,
    val selected: Boolean = false,
    val focused: Boolean = false,
    val enabled: Boolean = true,
    /** Opaque, snapshot-scoped reference. Models must not invent or parse it. */
    val token: String? = null,
    val semanticType: UiSemanticType = UiSemanticType.UNKNOWN,
    val depth: Int = 0,
    /** True when content was intentionally withheld from model-visible context. */
    val sensitive: Boolean = false,
)

typealias UiSemanticNode = ScreenContextNode
typealias UiSemanticSnapshot = ScreenContextSnapshot

data class ScreenContextSnapshot(
    val packageName: String?,
    val activityName: String?,
    val title: String?,
    val webUri: String?,
    val nodes: List<ScreenContextNode>,
    val capturedAtMs: Long,
    val provenance: String = "ANDROID_ASSIST",
    val trust: ContextTrust = ContextTrust.UNTRUSTED_CONTENT,
    val screenWidth: Int? = null,
    val screenHeight: Int? = null,
    val truncated: Boolean = false,
    val truncationReason: String? = null,
    /** Correlates opaque node tokens with exactly one observation generation. */
    val snapshotId: String = UUID.randomUUID().toString(),
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
