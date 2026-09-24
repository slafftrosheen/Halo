package com.halo.app.ui.screens.settings

import com.halo.sdk.provider.AiCapability
import com.halo.sdk.provider.ProviderStatus

data class AiProviderUiState(
    val id: String,
    val name: String,
    val status: ProviderStatus,
    val isSelected: Boolean,
    val capabilities: Set<AiCapability>,
)
