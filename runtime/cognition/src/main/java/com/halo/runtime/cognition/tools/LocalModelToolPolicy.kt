package com.halo.runtime.cognition.tools

import com.halo.sdk.provider.ToolCallProtocol

/**
 * AGENTIC-001 local-model tool exposure policy.
 *
 * A 4096-token Qwen 0.6B must never receive the whole Halo registry. Exposure is
 * bounded to [allowedToolIds] (bootstrap plus the phone actions a dedicated Android
 * capability can actually perform) and, separately, [activatableToolIds] that only
 * become visible after the model explicitly discovers them or loads the skill that
 * owns them.
 *
 * Accessibility sits in the activatable group: Halo does not spend screen-control
 * schemas on every turn, and screen tools still require the enabled service through
 * capability availability.
 *
 * This is a *presentation* limit, not an authorization one. Authorization still runs
 * per tool call through ToolAuthorizer, and nothing here mutates the ToolRegistry.
 */
data class LocalModelToolPolicy(
    val protocol: ToolCallProtocol,
    val allowedToolIds: Set<String>,
    val activatableToolIds: Set<String> = emptySet(),
    val maxExposedSchemas: Int = ToolLoopLimits.DEFAULT.maxLocalExposedSchemas,
) {
    /** Hard allowlist: exposed without being asked for. */
    fun allows(toolId: String): Boolean = toolId in allowedToolIds

    /** Reachable at all for a local model (either group). */
    fun mayExpose(toolId: String): Boolean =
        toolId in allowedToolIds || toolId in activatableToolIds

    fun withActivated(toolIds: Collection<String>): LocalModelToolPolicy =
        copy(activatableToolIds = activatableToolIds + toolIds)

    companion object {
        /** Always available to a local model: discovery, skill loading, clock. */
        val BOOTSTRAP: Set<String> = setOf(
            "halo_discover_tools",
            "halo_use_skill",
            "halo_current_time",
        )

        /**
         * Phone actions that a dedicated Android capability can perform. Preferred
         * over accessibility: accessibility never sets an alarm, opens an installed
         * app, changes volume, opens settings, starts navigation or opens the dialer.
         */
        val PHONE_ACTIONS: Set<String> = setOf(
            "halo_list_apps",
            "halo_open_app",
            "halo_set_alarm",
            "halo_volume",
            "halo_flashlight",
            "halo_find_contact",
            "halo_dial_contact",
            "halo_open_settings",
            "halo_navigation",
            "halo_browser",
            "halo_search",
            "halo_share_text",
            "halo_media_status",
            "halo_media_control",
        )

        /**
         * Screen tools: cross-app control through the bounded semantic snapshot.
         * Activatable only, so Halo never floods the bootstrap prompt with them.
         */
        val SCREEN: Set<String> = setOf(
            "halo_read_screen",
            "halo_find_on_screen",
            "halo_ui_click",
            "halo_ui_scroll",
            "halo_ui_set_text",
            "halo_ui_global_action",
        )

        /**
         * Bootstrap stays three schemas wide. Phone actions are activatable rather
         * than always-exposed: a 4096-token model should spend its prompt on the
         * user's words, then discover exactly the one or two actions it needs.
         */
        fun forProtocol(
            protocol: ToolCallProtocol,
            extraActivatable: Set<String> = emptySet(),
        ): LocalModelToolPolicy = LocalModelToolPolicy(
            protocol = protocol,
            allowedToolIds = BOOTSTRAP,
            activatableToolIds = PHONE_ACTIONS + SCREEN + setOf("halo_delegate_task", "halo_research", "halo_read_payload", "halo_search_conversations", "halo_fetch_url") + extraActivatable,
        )
    }
}
