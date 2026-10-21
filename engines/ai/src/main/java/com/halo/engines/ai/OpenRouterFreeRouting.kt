package com.halo.engines.ai

/** Cost boundary for automatic routing; not a price estimate or tool authorization. */
internal object OpenRouterFreeRouting {
    const val PROVIDER_ID = "openrouter"
    const val DEFAULT_MODEL_ID = "openrouter/free"

    fun canonicalModelId(modelId: String?): String? = modelId?.trim()?.takeIf { it.isNotEmpty() }
        ?.let { if (it == "free") DEFAULT_MODEL_ID else it }

    fun isFreeModel(modelId: String?): Boolean {
        val canonical = canonicalModelId(modelId) ?: return false
        return canonical == DEFAULT_MODEL_ID || canonical.endsWith(":free")
    }

    fun isFreeSelection(providerId: String?, modelId: String?): Boolean =
        providerId == PROVIDER_ID && isFreeModel(canonicalModelId(modelId) ?: DEFAULT_MODEL_ID)

    fun allowsProvider(providerId: String, isLocal: Boolean): Boolean =
        isLocal || providerId == PROVIDER_ID

    fun allowsTarget(providerId: String, isLocal: Boolean, modelId: String?): Boolean =
        isLocal || (providerId == PROVIDER_ID && isFreeModel(modelId))

    fun exactModelId(
        providerId: String,
        requestedModelId: String?,
        preferredModelId: String?,
        allowPreferredModelFallback: Boolean,
    ): String? = if (providerId == PROVIDER_ID) {
        // An unset choice is the free route, never whichever paid model ranks first.
        // Even a preferred model cannot silently become another model in this provider.
        canonicalModelId(requestedModelId) ?: canonicalModelId(preferredModelId) ?: DEFAULT_MODEL_ID
    } else {
        requestedModelId ?: preferredModelId.takeIf { !allowPreferredModelFallback }
    }
}
