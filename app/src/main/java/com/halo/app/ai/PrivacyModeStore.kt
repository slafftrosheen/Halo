package com.halo.app.ai

import com.halo.sdk.provider.AiContextPolicy
import com.halo.sdk.provider.PaidMediaPolicy
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect

enum class PrivacyMode { PRIVATE, CLOUD }
const val PRIVACY_MODE_KEY = "ai_privacy_mode_v1"

/** Null is legacy data. Invalid saved modes fail closed. */
fun Storage.savedPrivacyMode(): PrivacyMode? = getString(PRIVACY_MODE_KEY)?.let {
    runCatching { PrivacyMode.valueOf(it) }.getOrDefault(PrivacyMode.PRIVATE)
}
fun PrivacyMode.contextPolicy() = AiContextPolicy(
    includeConversation = true, includeDeviceState = true, includeRecentEvents = true,
    includeMemories = true, includeAvailableTools = true, includeLocation = true,
    includeImages = true, includeAudio = true, includeVideo = true, includeWorkspace = true,
    allowCloudWorkspace = this == PrivacyMode.CLOUD, allowCloudScreenContext = this == PrivacyMode.CLOUD,
    allowCloudImages = this == PrivacyMode.CLOUD, allowCloudMemories = this == PrivacyMode.CLOUD,
)

/** One durable sharing decision. It grants neither Android permissions nor action/spend approval. */
class PrivacyModeStore(private val storage: Storage) {
    private val current = MutableStateFlow(storage.savedPrivacyMode() ?: PrivacyMode.PRIVATE)
    val mode = current.asStateFlow()
    init { set(current.value) }
    @Synchronized fun set(mode: PrivacyMode) {
        val cloud = mode == PrivacyMode.CLOUD
        storage.putBatch(strings = mapOf(PRIVACY_MODE_KEY to mode.name), booleans = mapOf(
            KEY_CONVERSATION to true, KEY_DEVICE_STATE to true, KEY_RECENT_EVENTS to true,
            KEY_MEMORIES to true, KEY_TOOLS to true, KEY_LOCATION to true, KEY_IMAGES to true,
            KEY_AUDIO to true, KEY_VIDEO to true, KEY_WORKSPACE to true,
            KEY_CLOUD_WORKSPACE to cloud, KEY_CLOUD_SCREEN to cloud, KEY_CLOUD_IMAGES to cloud,
            "policy_cloud_memories" to cloud, PaidMediaPolicy.CONSENT_KEY to cloud,
        ))
        current.value = mode
    }
}

/** A transition to Private cancels the actual cancellable HTTP operation, not just its preview. */
suspend fun <T> whileCloudMode(modes: StateFlow<PrivacyMode>?, onPrivate: () -> Unit = {}, block: suspend () -> T): T {
    if (modes == null) return block()
    return coroutineScope {
        check(modes.value == PrivacyMode.CLOUD) { "Switch to Cloud mode at the top of halo." }
        val task = async(start = CoroutineStart.LAZY) { block() }
        val watch = launch(start = CoroutineStart.UNDISPATCHED) {
            modes.collect { if (it == PrivacyMode.PRIVATE) {
                runCatching(onPrivate)
                task.cancel(CancellationException("Private mode enabled"))
            } }
        }
        try { task.await() } finally { watch.cancel() }
    }
}
