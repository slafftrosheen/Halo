package com.halo.engines.ai

import com.halo.sdk.provider.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.UUID

class PhoneAssistantController(
    private val executionService: AssistantExecutionService
) {

    private var activeJob: Job? = null

    fun submit(
        scope: CoroutineScope,
        text: String,
        context: AiContext = AiContext.EMPTY,
        onState: (AssistantTurn) -> Unit
    ) {
        activeJob?.cancel()

        val turnId = UUID.randomUUID().toString()

        activeJob = scope.launch {
            val started = System.currentTimeMillis()

            onState(
                AssistantTurn(
                    id = turnId,
                    userText = text,
                    status = AssistantTurnStatus.ACCEPTED,
                    startedAtMs = started
                )
            )

            try {
                val response = executionService.execute(
                    AiRequest(text = text),
                    context
                )

                onState(
                    AssistantTurn(
                        id = turnId,
                        userText = text,
                        providerId = response.providerId,
                        modelId = response.modelId,
                        status = AssistantTurnStatus.COMPLETED,
                        responseText = response.text,
                        startedAtMs = started,
                        completedAtMs = System.currentTimeMillis()
                    )
                )
            } catch (error: CancellationException) {
                onState(
                    AssistantTurn(
                        id = turnId,
                        userText = text,
                        status = AssistantTurnStatus.CANCELLED,
                        startedAtMs = started
                    )
                )

                throw error
            } catch (error: Throwable) {
                onState(
                    AssistantTurn(
                        id = turnId,
                        userText = text,
                        status = AssistantTurnStatus.FAILED,
                        errorMessage = error.message,
                        startedAtMs = started,
                        completedAtMs = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    fun cancel() {
        activeJob?.cancel()
        activeJob = null
    }
}
