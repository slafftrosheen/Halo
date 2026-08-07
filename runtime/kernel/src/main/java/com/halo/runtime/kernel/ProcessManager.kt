package com.halo.runtime.kernel

import com.halo.sdk.runtime.RuntimeLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.delay

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ProcessManager(
    private val scope: CoroutineScope,
    private val logger: RuntimeLogger
) {
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
            logger.error("ProcessManager", "Process ${process.id} crashed: ${exception.message}", exception)
            // Basic recovery loop for Alpha MVP
            scope.launch {
                try {
                    delay(2000) // Backoff
                    logger.info("ProcessManager", "Attempting to restart ${process.id}")
                    startProcessWithRecovery(process)
                } catch (e: Exception) {
                    logger.error("ProcessManager", "Failed to recover ${process.id}", e)
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
