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

class HostTools(
    private val context: Context,
    private val isForeground: () -> Boolean = { true }
) : ToolExecutor {
    private val registry = HostToolRegistry(
        listOf(
            ApplicationTools(context, isForeground),
            MediaTools(context),
            DeviceTools(context),
            AlarmTools(context),
            CommunicationTools(context),
            SystemTools(context),
            NavigationTools(context, isForeground),
            AccessibilityTools()
        )
    )

    override fun definitions(): List<ToolDefinition> = registry.definitions()

    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult = registry.execute(toolCall)
}
