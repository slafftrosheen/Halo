package com.halo.runtime.kernel

import com.halo.sdk.runtime.RuntimeLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

class ProcessManager(
    private val scope: CoroutineScope,
    private val logger: RuntimeLogger
) {
    private val _processes = MutableStateFlow<List<Process>>(emptyList())
    val processFlow: StateFlow<List<Process>> = _processes.asStateFlow()
    val processes: List<Process> get() = _processes.value

    private val processJobs = ConcurrentHashMap<String, Job>()

    fun register(process: Process) {
        require(_processes.value.none { it.id == process.id }) {
            "Duplicate process id: ${process.id}"
        }
        _processes.update { it + process }
    }

    suspend fun startAll() {
        _processes.value.forEach { process ->
            startProcessWithRecovery(process)
        }
    }

    private fun startProcessWithRecovery(process: Process) {
        if (processJobs[process.id]?.isActive == true) return

        val handler = CoroutineExceptionHandler { _, exception ->
            if (exception is CancellationException) return@CoroutineExceptionHandler
            logger.error(
                "ProcessManager",
                "Process ${process.id} crashed: ${exception.message}",
                exception
            )
        }

        val job = scope.launch(SupervisorJob() + handler) {
            try {
                process.start()
                // A Process.start() implementation is expected to suspend for the
                // lifetime of the process. If it returns, the process is not actually
                // being supervised anymore; do not recursively spawn unbounded jobs.
                logger.info("ProcessManager", "Process ${process.id} start() returned")
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                logger.error("ProcessManager", "Process ${process.id} failed during start", exception)
            } finally {
                processJobs.remove(process.id)
            }
        }
        processJobs[process.id] = job
    }

    suspend fun stopAll() {
        val snapshot = _processes.value
        snapshot.forEach { process ->
            runCatching { process.stop() }
                .onFailure { logger.error("ProcessManager", "Failed to stop ${process.id}", it) }
        }
        processJobs.values.forEach { it.cancel() }
        processJobs.clear()
    }
}
