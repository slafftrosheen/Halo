package com.halo.app.ui.screens.settings

import com.halo.sdk.provider.AiExecutionLocation
import com.halo.sdk.provider.AiCapability
import com.halo.sdk.provider.AiModelRole

internal fun executionLocationLabel(location: AiExecutionLocation): String = when (location) {
    AiExecutionLocation.ON_DEVICE -> "On this phone"
    AiExecutionLocation.LOCAL_NETWORK -> "On your network"
    AiExecutionLocation.REMOTE -> "In the cloud"
}

internal fun capabilityLabel(capability: AiCapability): String = when (capability) {
    AiCapability.TEXT -> "Text"
    AiCapability.IMAGE -> "Image understanding"
    AiCapability.TOOLS -> "Actions and tools"
    AiCapability.AUDIO -> "Audio understanding"
    AiCapability.VIDEO -> "Video understanding"
    AiCapability.MULTIMODAL -> "Mixed media"
    AiCapability.STREAMING -> "Streaming replies"
}

internal fun modelRoleLabel(role: AiModelRole): String = when (role) {
    AiModelRole.CHAT -> "Conversation"
    AiModelRole.FAST -> "Quick replies"
    AiModelRole.REASONING -> "Careful thinking"
    AiModelRole.AGENTIC -> "Actions and tools"
    AiModelRole.VISION -> "Images"
}
