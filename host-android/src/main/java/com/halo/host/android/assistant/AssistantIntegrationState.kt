package com.halo.host.android.assistant

sealed interface AssistantIntegrationState {
    data object Checking : AssistantIntegrationState
    data class NotEligible(val diagnostics: AssistantEligibilitySnapshot) : AssistantIntegrationState
    data class EligibleNotSelected(val diagnostics: AssistantEligibilitySnapshot) : AssistantIntegrationState
    data class RoleHeldNotActive(val diagnostics: AssistantEligibilitySnapshot) : AssistantIntegrationState
    data class Active(val diagnostics: AssistantEligibilitySnapshot) : AssistantIntegrationState
    data class Error(val message: String, val diagnostics: AssistantEligibilitySnapshot? = null) : AssistantIntegrationState
}
