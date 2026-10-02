package com.halo.app.di.tools

import android.content.Context
import com.halo.app.di.tools.host.AccessibilityTools
import com.halo.app.di.tools.host.AlarmTools
import com.halo.app.di.tools.host.ApplicationTools
import com.halo.app.di.tools.host.CommunicationTools
import com.halo.app.di.tools.host.DeviceTools
import com.halo.app.di.tools.host.HostToolRegistry
import com.halo.app.di.tools.host.MediaTools
import com.halo.app.di.tools.host.NavigationTools
import com.halo.app.di.tools.host.SystemTools
import com.halo.runtime.cognition.tools.ToolExecutionResult
import com.halo.runtime.cognition.tools.ToolExecutor
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition

/**
 * The Android host adapter: every phone-side tool executor behind the single portable
 * [ToolExecutor] contract the Runtime registers.
 *
 * RUNTIME-056: executors that need runtime-owned repositories (workspace files, for
 * example) are supplied by the composition root rather than constructed here, so this
 * class never reaches into storage it does not own.
 */
class HostTools(
    private val context: Context,
    private val isForeground: () -> Boolean = { true },
    private val additionalExecutors: List<ToolExecutor> = emptyList(),
) : ToolExecutor, com.halo.runtime.cognition.tools.ToolPreflightProvider {
    override suspend fun preflight(toolCall: ToolCall) = registry.preflight(toolCall)
    override fun restoreTransientCall(toolCall: ToolCall) = registry.restoreTransientCall(toolCall)
    private val registry = HostToolRegistry(
        buildList {
            add(ApplicationTools(context, isForeground))
            add(MediaTools(context))
            add(DeviceTools(context))
            add(AlarmTools(context))
            add(com.halo.app.di.tools.host.CalendarTools(context))
            add(com.halo.app.di.tools.host.NotificationControlTools(context))
            add(CommunicationTools(context))
            add(SystemTools(context))
            add(NavigationTools(context, isForeground))
            add(AccessibilityTools())
            addAll(additionalExecutors)
        }
    )

    override fun definitions(): List<ToolDefinition> = registry.definitions()

    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult = registry.execute(toolCall)
}
