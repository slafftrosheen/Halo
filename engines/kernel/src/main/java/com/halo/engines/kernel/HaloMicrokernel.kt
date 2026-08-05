package com.halo.engines.kernel

import com.halo.engines.kernel.manager.ProcessManager
import com.halo.engines.kernel.manager.SecurityManager
import com.halo.engines.kernel.manager.ResourceScheduler
import com.halo.runtime.kernel.bus.PerceptionBus
import com.halo.runtime.kernel.bus.CognitiveBus
import com.halo.runtime.kernel.bus.ExecutionBus
import com.halo.libraries.logging.HaloLogger
import com.halo.libraries.logging.LogCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineExceptionHandler

/**
 * The Halo Microkernel.
 * 
 * Notice: This class has ZERO dependencies on LLMs, Speech, Vision, Reasoning, or Planning.
 * It strictly manages IPC (Buses) and Process lifecycle.
 */
class HaloMicrokernel(
    private val processManager: ProcessManager,
    private val securityManager: SecurityManager,
    private val resourceScheduler: ResourceScheduler,
    
    // The Core Buses
    val perceptionBus: PerceptionBus,
    val cognitiveBus: CognitiveBus,
    val executionBus: ExecutionBus
) {
    private val exceptionHandler = CoroutineExceptionHandler { _, exception ->
        HaloLogger.e(LogCategory.Runtime, "Kernel Process Crashed", throwable = exception)
    }
    
    private val kernelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + exceptionHandler)

    suspend fun boot() {
        // 1. Initialize Buses
        HaloLogger.i(LogCategory.Runtime, "Initializing ICL Buses...")
        
        // 2. Load Registered Drivers (Sensors/Actuators)
        HaloLogger.i(LogCategory.Runtime, "Loading Drivers...")
        
        // 3. Load Registered Agents (Reasoning/Planning)
        HaloLogger.i(LogCategory.Runtime, "Loading Agents...")
        
        // 4. Start all processes
        processManager.startAll()
        
        HaloLogger.i(LogCategory.Runtime, "Halo Microkernel Boot Complete.")
    }
    
    suspend fun shutdown() {
        processManager.stopAll()
        HaloLogger.i(LogCategory.Runtime, "Halo Microkernel Shutdown Complete.")
    }
}
