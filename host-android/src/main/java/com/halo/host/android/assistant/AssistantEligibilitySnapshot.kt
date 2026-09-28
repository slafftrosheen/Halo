package com.halo.host.android.assistant

import android.content.ComponentName

enum class ActiveInteractorStatus { ACTIVE, INACTIVE, UNKNOWN }

data class AssistantEligibilitySnapshot(
    val roleAvailable: Boolean = false,
    val roleHeld: Boolean = false,

    val voiceInteractionServiceDeclared: Boolean = false,
    val voiceInteractionServiceDiscoverable: Boolean = false,
    val bindPermissionCorrect: Boolean = false,
    val serviceMetadataPresent: Boolean = false,

    val sessionServiceDeclared: Boolean = false,
    val sessionServiceClassValid: Boolean = false,
    val recognitionServiceDeclared: Boolean = false,
    val recognitionServicePermissionCorrect: Boolean = false,

    val activeInteractorStatus: ActiveInteractorStatus = ActiveInteractorStatus.UNKNOWN,
    val activeInteractorComponent: String? = null,

    val roleRequestResolvable: Boolean = false,
    val defaultAppsSettingsResolvable: Boolean = false,
    val voiceSettingsResolvable: Boolean = false,

    val discoveredSelfServices: List<ComponentName> = emptyList(),
    val problems: List<String> = emptyList(),
) {
    val activeVoiceInteractionService: Boolean
        get() = activeInteractorStatus == ActiveInteractorStatus.ACTIVE
}
