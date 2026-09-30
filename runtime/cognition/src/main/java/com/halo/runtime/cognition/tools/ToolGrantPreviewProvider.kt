package com.halo.runtime.cognition.tools

import com.halo.sdk.provider.ToolCall

/** Read-only validation and bounded grant copy before durable approval. No persistence or dispatch. */
interface ToolGrantPreviewProvider { suspend fun grantSummary(toolCall: ToolCall): String }
