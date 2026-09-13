package com.halo.runtime.kernel

import com.halo.sdk.runtime.RuntimeLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

class ProcessManager(
    private val scope: CoroutineScope,
    private val logger: RuntimeLogger
) {
    companion object {
        private const val STARTUP_TIMEOUT_MS = 5_000L
    }

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
            val job = startProcessWithRecovery(process)
            awaitRunning(process, job)
        }
    }

    private fun startProcessWithRecovery(process: Process): Job {
        processJobs[process.id]?.let { existing ->
            if (existing.isActive) return existing
        }

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
        return job
    }

    private suspend fun awaitRunning(process: Process, job: Job) {
        val terminalState = withTimeoutOrNull(STARTUP_TIMEOUT_MS) {
            process.state.first { state ->
                state == ProcessState.Running ||
                    state == ProcessState.Crashed ||
                    state == ProcessState.Stopped
            }
        }

        when (terminalState) {
            ProcessState.Running -> Unit
            ProcessState.Crashed -> throw IllegalStateException(
                "Process ${process.id} crashed during startup"
            )
            ProcessState.Stopped -> throw IllegalStateException(
                "Process ${process.id} stopped during startup"
            )
            null -> {
                job.cancel()
                throw IllegalStateException(
                    "Process ${process.id} did not reach Running within ${STARTUP_TIMEOUT_MS}ms"
                )
            }
            else -> error("Unexpected startup state for ${process.id}: $terminalState")
        }
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
