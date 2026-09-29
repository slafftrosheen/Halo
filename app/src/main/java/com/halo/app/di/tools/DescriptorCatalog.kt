package com.halo.app.di.tools

import com.halo.runtime.cognition.tools.ToolDescriptor
import com.halo.runtime.cognition.tools.ToolExecutor
import com.halo.sdk.provider.ToolDefinition

/**
 * RUNTIME-017 wiring helper: binds each executor's definitions to its portable
 * [ToolDescriptor]s so the ToolRegistry catalogue carries real metadata.
 */
object DescriptorCatalog {

    fun descriptorsFor(
        kind: HaloToolDescriptors.ExecutorKind,
        executor: ToolExecutor,
    ): List<ToolDescriptor> = executor.definitions().mapNotNull { definition ->
        HaloToolDescriptors.forExecutor(kind, definition)
    }

    /**
     * Build the full descriptor list for the phone tool registry in registration
     * order. Executors are passed as kind + instance pairs so descriptor lookup
     * cannot drift from the actual definition list.
     */
    fun buildDescriptors(
        vararg entries: Pair<HaloToolDescriptors.ExecutorKind, ToolExecutor>,
    ): List<ToolDescriptor> = entries.flatMap { (kind, executor) ->
        descriptorsFor(kind, executor)
    }
}
