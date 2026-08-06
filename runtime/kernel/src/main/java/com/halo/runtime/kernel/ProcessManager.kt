package com.halo.runtime.kernel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.delay
import android.util.Log

class ProcessManager(private val scope: CoroutineScope) {
    private val _processes = mutableListOf<Process>()
    val processes: List<Process> get() = _processes

    fun register(process: Process) {
        _processes.add(process)
    }

    suspend fun startAll() {
        _processes.forEach { process ->
            startProcessWithRecovery(process)
        }
    }

    private fun startProcessWithRecovery(process: Process) {
        val handler = CoroutineExceptionHandler { _, exception ->
            Log.e("ProcessManager", "Process ${process.id} crashed: ${exception.message}")
            // Basic recovery loop for Alpha MVP
            scope.launch {
                try {
                    delay(2000) // Backoff
                    Log.i("ProcessManager", "Attempting to restart ${process.id}")
                    startProcessWithRecovery(process)
                } catch (e: Exception) {
                    Log.e("ProcessManager", "Failed to recover ${process.id}")
                }
            }
        }
        
        scope.launch(SupervisorJob() + handler) {
            process.start()
        }
    }

    suspend fun stopAll() {
        _processes.forEach { it.stop() }
    }
}
