package com.halo.app.ai

import com.halo.sdk.provider.AiContextPolicy
import com.halo.sdk.runtime.Storage

/**
 * Single source of truth for user-controlled cognitive context policy.
 *
 * The Intelligence UI persists these keys and CognitiveSession resolves them at the
 * beginning of every new turn through the composition-root policy provider.
 */
fun Storage.readRuntimeAiContextPolicy(): AiContextPolicy = (this as? ModeProfileStorage)?.contextPolicy() ?: AiContextPolicy(
    includeConversation = getBoolean(KEY_CONVERSATION, true),
    includeDeviceState = getBoolean(KEY_DEVICE_STATE, true),
    includeRecentEvents = getBoolean(KEY_RECENT_EVENTS, false),
    includeMemories = getBoolean(KEY_MEMORIES, false),
    includeAvailableTools = getBoolean(KEY_TOOLS, true),
    includeLocation = getBoolean(KEY_LOCATION, false),
    includeImages = getBoolean(KEY_IMAGES, true),
    includeAudio = getBoolean(KEY_AUDIO, false),
    includeVideo = getBoolean(KEY_VIDEO, false),
    includeWorkspace = getBoolean(KEY_WORKSPACE, true),
    allowCloudWorkspace = savedPrivacyMode() != PrivacyMode.PRIVATE && getBoolean(KEY_CLOUD_WORKSPACE, false),
    allowCloudScreenContext = savedPrivacyMode() != PrivacyMode.PRIVATE && getBoolean(KEY_CLOUD_SCREEN, false),
    allowCloudImages = savedPrivacyMode() != PrivacyMode.PRIVATE && getBoolean(KEY_CLOUD_IMAGES, false),
    allowCloudMemories = savedPrivacyMode() != PrivacyMode.PRIVATE && getBoolean("policy_cloud_memories", false)
)

fun Storage.writeRuntimeAiContextPolicy(policy: AiContextPolicy) {
    putBatch(booleans = mapOf(KEY_CONVERSATION to policy.includeConversation,
        KEY_DEVICE_STATE to policy.includeDeviceState, KEY_RECENT_EVENTS to policy.includeRecentEvents,
        KEY_MEMORIES to policy.includeMemories, KEY_TOOLS to policy.includeAvailableTools,
        KEY_LOCATION to policy.includeLocation, KEY_IMAGES to policy.includeImages,
        KEY_AUDIO to policy.includeAudio, KEY_VIDEO to policy.includeVideo,
        KEY_WORKSPACE to policy.includeWorkspace, KEY_CLOUD_WORKSPACE to policy.allowCloudWorkspace,
        KEY_CLOUD_SCREEN to policy.allowCloudScreenContext, KEY_CLOUD_IMAGES to policy.allowCloudImages,
        "policy_cloud_memories" to policy.allowCloudMemories))
}

const val KEY_CONVERSATION = "policy_conversation"
const val KEY_DEVICE_STATE = "policy_device_state"
const val KEY_RECENT_EVENTS = "policy_recent_events"
const val KEY_MEMORIES = "policy_memories"
const val KEY_TOOLS = "policy_tools"
const val KEY_LOCATION = "policy_location"
const val KEY_IMAGES = "policy_images"
const val KEY_AUDIO = "policy_audio"
const val KEY_VIDEO = "policy_video"
const val KEY_WORKSPACE = "policy_workspace"
const val KEY_CLOUD_WORKSPACE = "policy_cloud_workspace"
const val KEY_CLOUD_SCREEN = "policy_cloud_screen_context"
const val KEY_CLOUD_IMAGES = "policy_cloud_images"
