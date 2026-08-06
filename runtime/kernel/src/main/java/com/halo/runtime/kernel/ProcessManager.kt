package com.halo.runtime.kernel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.delay
import android.util.Log

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ProcessManager(private val scope: CoroutineScope) {
    private val _processes = MutableStateFlow<List<Process>>(emptyList())
    val processFlow: StateFlow<List<Process>> = _processes.asStateFlow()
    val processes: List<Process> get() = _processes.value

    fun register(process: Process) {
        _processes.update { it + process }
    }

    suspend fun startAll() {
        _processes.value.forEach { process ->
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
        _processes.value.forEach { it.stop() }
    }
}
