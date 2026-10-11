package com.halo.app.ui.screens.settings

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/** UI-owned single operation; busy spans cancellation cleanup as well as execution. */
internal class DiagnosticOperation(
    private val scope: CoroutineScope,
    private val started: () -> Unit,
    private val cancelled: () -> Unit,
    private val failed: (Exception) -> Unit,
    private val finished: () -> Unit,
    private val context: CoroutineContext = Dispatchers.IO,
) {
    private var job: Job? = null
    fun start(block: suspend () -> Unit) {
        if (job?.isCompleted == false) return
        started()
        job = scope.launch(context, start = CoroutineStart.LAZY) {
            try { block() }
            catch (error: CancellationException) { cancelled(); throw error }
            catch (error: Exception) { failed(error) }
            finally { finished() }
        }.also { it.start() }
    }
    fun cancel() { job?.cancel() }
}
