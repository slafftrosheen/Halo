package com.halo.runtime.cognition.response

import com.halo.runtime.cognition.ExecutionResultPublished
import com.halo.runtime.cognition.Outcome
import com.halo.runtime.cognition.Task
import com.halo.sdk.runtime.EventBus
import com.halo.sdk.runtime.ExecutionFinished
import com.halo.sdk.runtime.RuntimeLogger
import com.halo.sdk.runtime.TtsRequested
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

class CommandResponseProcess(
    private val eventBus: EventBus,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
    private val logger: RuntimeLogger? = null,
    private val mapper: CommandResponseMapper = CommandResponseMapper()
) {
    private data class ExecutionRecord(
        val primaryTask: Task,
        val outcome: Outcome
    )

    private val activeExecutions = ConcurrentHashMap<String, ExecutionRecord>()
    private val processedExecutionIds = Collections.synchronizedSet(mutableSetOf<String>())

    fun start() {
        scope.launch {
            eventBus.observe(ExecutionResultPublished::class.java).collect { event ->
                val executionId = event.id
                activeExecutions.putIfAbsent(executionId, ExecutionRecord(event.task, event.outcome))
            }
        }

        scope.launch {
            eventBus.observe(ExecutionFinished::class.java).collect { event ->
                val executionId = event.id
                
                if (!processedExecutionIds.add(executionId)) {
                    return@collect
                }
                if (processedExecutionIds.size > 500) {
                    processedExecutionIds.clear()
                }

                val record = activeExecutions.remove(executionId)
                if (record != null) {
                    val responseText = mapper.mapResponse(record.primaryTask, record.outcome)
                    if (!responseText.isNullOrBlank()) {
                        logger?.info(
                            "HALO-TXN",
                            "speech=$executionId task=${record.primaryTask::class.simpleName} outcome=${record.outcome::class.simpleName} tts=\"$responseText\""
                        )
                        eventBus.publish(TtsRequested(text = responseText))
                    }
                }
            }
        }
    }
}
