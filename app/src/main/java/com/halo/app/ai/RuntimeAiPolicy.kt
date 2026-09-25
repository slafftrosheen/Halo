package com.halo.app.ai

import com.halo.sdk.provider.AiContextPolicy
import com.halo.sdk.runtime.Storage

/**
 * Single source of truth for user-controlled cognitive context policy.
 *
 * The Intelligence UI persists these keys and CognitiveSession resolves them at the
 * beginning of every new turn through the composition-root policy provider.
 */
fun Storage.readRuntimeAiContextPolicy(): AiContextPolicy = AiContextPolicy(
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
    allowCloudWorkspace = getBoolean(KEY_CLOUD_WORKSPACE, false),
    allowCloudScreenContext = getBoolean(KEY_CLOUD_SCREEN, false),
    allowCloudImages = getBoolean(KEY_CLOUD_IMAGES, false)
)

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
