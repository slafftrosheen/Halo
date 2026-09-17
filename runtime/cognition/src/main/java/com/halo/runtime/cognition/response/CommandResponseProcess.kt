package com.halo.runtime.cognition.response

import com.halo.runtime.cognition.ExecutionResultPublished
import com.halo.sdk.runtime.EventBus
import com.halo.sdk.runtime.RuntimeLogger
import com.halo.sdk.runtime.TtsRequested
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CommandResponseProcess(
    private val eventBus: EventBus,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
    private val logger: RuntimeLogger? = null,
    private val mapper: CommandResponseMapper = CommandResponseMapper()
) {
    fun start() {
        scope.launch {
            eventBus.observe(ExecutionResultPublished::class.java).collect { event ->
                val responseText = mapper.mapResponse(event.task, event.outcome)
                if (!responseText.isNullOrBlank()) {
                    logger?.info(
                        "HALO-TXN",
                        "speech=${event.id} task=${event.task::class.simpleName} outcome=${event.outcome::class.simpleName} tts=\"$responseText\""
                    )
                    eventBus.publish(TtsRequested(text = responseText))
                }
            }
        }
    }
}
