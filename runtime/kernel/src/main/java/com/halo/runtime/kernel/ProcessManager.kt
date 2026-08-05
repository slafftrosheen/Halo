package com.halo.runtime.kernel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class ProcessManager(private val scope: CoroutineScope) {
    private val processes = mutableListOf<Process>()

    fun register(process: Process) {
        processes.add(process)
    }

    suspend fun startAll() {
        processes.forEach { process ->
            scope.launch {
                process.start()
            }
        }
    }

    suspend fun stopAll() {
        processes.forEach { it.stop() }
    }
}
