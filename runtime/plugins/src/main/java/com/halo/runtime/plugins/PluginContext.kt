package com.halo.runtime.plugins

import com.halo.sdk.runtime.EventBus
import com.halo.sdk.runtime.CommandBus
import com.halo.sdk.runtime.RuntimeContext
import com.halo.sdk.runtime.RuntimeScheduler
import com.halo.sdk.runtime.HaloLogger
import com.halo.sdk.runtime.RuntimeClock
import com.halo.runtime.cognition.WorkingMemory

// Basic permission manager placeholder for now
interface PermissionManager {
    fun hasPermission(permission: String): Boolean
}

class PluginContext(
    val runtime: RuntimeContext,
    val scheduler: RuntimeScheduler,
    val logger: HaloLogger,
    val eventBus: EventBus,
    val commandBus: CommandBus,
    val clock: RuntimeClock,
    val workingMemory: WorkingMemory,
    val permissionManager: PermissionManager
)
