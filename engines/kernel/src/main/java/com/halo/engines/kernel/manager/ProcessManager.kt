package com.halo.engines.kernel.manager

import com.halo.runtime.kernel.Process

class ProcessManager {
    private val processes = mutableMapOf<String, Process>()

    fun register(process: Process) {
        processes[process.id] = process
    }

    suspend fun startAll() {
        processes.values.forEach { it.start() }
    }

    suspend fun stopAll() {
        processes.values.forEach { it.stop() }
    }
}
