package com.halo.runtime.plugins

import com.halo.sdk.runtime.EventBus
import com.halo.sdk.runtime.CommandBus
import com.halo.sdk.runtime.RuntimeContext
import com.halo.sdk.runtime.RuntimeScheduler
import com.halo.sdk.runtime.RuntimeLogger
import com.halo.sdk.runtime.RuntimeClock
import com.halo.runtime.cognition.workingmemory.WorkingMemory
import com.halo.runtime.cognition.ICapabilityRegistry

/** Host-provided permission boundary exposed to plugins. */
interface PermissionManager {
    fun hasPermission(permission: String): Boolean
}

class PluginContext(
    val runtime: RuntimeContext,
    val scheduler: RuntimeScheduler,
    val logger: RuntimeLogger,
    val eventBus: EventBus,
    val commandBus: CommandBus,
    val clock: RuntimeClock,
    val workingMemory: WorkingMemory,
    val permissionManager: PermissionManager,
    val capabilities: ICapabilityRegistry
)
