package com.halo.sdk.provider

/**
 * Typed view of provider capabilities exposed to the cognitive runtime.
 *
 * Inference and external-assistant handoff are separate contracts and should
 * never be inferred from implementation-specific provider IDs.
 */
data class AiProviderRegistry(
    val inference: List<AiInferenceProvider>,
    val handoff: List<AiHandoffProvider>
) {
    val all: List<AiProvider>
        get() = inference + handoff

    companion object {
        fun from(providers: List<AiProvider>): AiProviderRegistry = AiProviderRegistry(
            inference = providers.filterIsInstance<AiInferenceProvider>().filterNot { it is AiHandoffProvider },
            handoff = providers.filterIsInstance<AiHandoffProvider>()
        )
    }
}
