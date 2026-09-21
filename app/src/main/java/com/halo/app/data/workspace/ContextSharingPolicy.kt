package com.halo.app.data.workspace

enum class ProviderTrustBoundary {
    LOCAL,
    NETWORK,
    EXTERNAL_HANDOFF
}

data class ContextSharingPolicy(
    val allowConversation: Boolean = true,
    val allowWorkingSet: Boolean = true,
    val allowMemory: Boolean = true,
    val allowFiles: Boolean = true,
    val allowImages: Boolean = true
) {
    companion object {
        val STRICT_LOCAL_ONLY = ContextSharingPolicy(
            allowConversation = true,
            allowWorkingSet = true,
            allowMemory = true,
            allowFiles = true,
            allowImages = true
        )

        val CONSERVATIVE_NETWORK = ContextSharingPolicy(
            allowConversation = true,
            allowWorkingSet = true,
            allowMemory = false,
            allowFiles = false,
            allowImages = true
        )

        fun forBoundary(boundary: ProviderTrustBoundary, userCloudSharingAllowed: Boolean): ContextSharingPolicy {
            return when (boundary) {
                ProviderTrustBoundary.LOCAL -> STRICT_LOCAL_ONLY
                ProviderTrustBoundary.NETWORK -> {
                    if (userCloudSharingAllowed) CONSERVATIVE_NETWORK
                    else ContextSharingPolicy(
                        allowConversation = true,
                        allowWorkingSet = false,
                        allowMemory = false,
                        allowFiles = false,
                        allowImages = false
                    )
                }
                ProviderTrustBoundary.EXTERNAL_HANDOFF -> ContextSharingPolicy(
                    allowConversation = false,
                    allowWorkingSet = false,
                    allowMemory = false,
                    allowFiles = false,
                    allowImages = false
                )
            }
        }
    }
}
