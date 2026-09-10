package com.halo.runtime.cognition.tools

import com.halo.sdk.provider.ToolCall

/**
 * Runtime security/safety boundary for tool execution.
 *
 * Implementations may later incorporate:
 * - device capabilities
 * - user permissions
 * - hardware safety state
 * - execution source
 * - confirmation policy
 * - tool risk classification
 */
interface ToolAuthorizer {

    suspend fun authorize(
        toolCall: ToolCall
    ): ToolAuthorization
}
