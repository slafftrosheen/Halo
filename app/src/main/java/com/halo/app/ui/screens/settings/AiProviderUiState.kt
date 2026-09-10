package com.halo.app.ui.screens.settings

import com.halo.sdk.provider.AiCapability
import com.halo.sdk.provider.ProviderStatus

data class AiProviderUiState(
    val id: String,
    val name: String,
    val status: ProviderStatus,
    val isSelected: Boolean,
    val capabilities: Set<AiCapability>,
    /**
     * True when this provider only hands work to an external assistant.
     *
     * The current Android assistant provider is the only handoff-only
     * provider exposed by the Android host. Keep this derived here until the
     * UI projection is moved to the provider-type boundary in the view model.
     */
    val isHandoff: Boolean = id == "android.system.assistant"
)
