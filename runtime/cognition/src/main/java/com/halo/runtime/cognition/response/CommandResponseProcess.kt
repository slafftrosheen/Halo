package com.halo.runtime.cognition.response

import com.halo.runtime.cognition.ExecutionResultPublished
import com.halo.sdk.runtime.EventBus
import com.halo.sdk.runtime.ExecutionFinished
import com.halo.sdk.runtime.RuntimeLogger
import com.halo.sdk.runtime.TtsRequested
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CommandResponseProcess(
    private val eventBus: EventBus,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
    private val logger: RuntimeLogger? = null,
    private val mapper: CommandResponseMapper = CommandResponseMapper()
) {
    private val pendingResults = mutableMapOf<String, MutableList<ExecutionResultPublished>>()
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return

        pendingResults.clear()
        job = scope.launch {
            // Use the single ordered event stream so ExecutionFinished cannot overtake
            // the result event that immediately precedes it on another collector.
            eventBus.events().collect { event ->
                when (event) {
                    is ExecutionResultPublished -> {
                        pendingResults.getOrPut(event.executionId) { mutableListOf() }
                            .add(event)
                    }

                    is ExecutionFinished -> {
                        val results = pendingResults.remove(event.id).orEmpty()
                        val responseText = mapper.mapExecution(results)

                        if (!responseText.isNullOrBlank()) {
                            logger?.info(
                                "HALO-TXN",
                                "execution=${event.id} plan=${event.planId} tasks=${results.size} tts=\"$responseText\""
                            )
                            eventBus.publish(TtsRequested(text = responseText))
                        }
                    }
                }
            }
        }
    }
}
