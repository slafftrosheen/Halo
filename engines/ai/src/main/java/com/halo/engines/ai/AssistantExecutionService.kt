package com.halo.engines.ai

import com.halo.sdk.provider.*
import com.halo.sdk.runtime.RuntimeLogger
import kotlinx.coroutines.CancellationException

class AssistantExecutionService(
    private val router: AiProviderRouter,
    private val logger: RuntimeLogger? = null
) {

    suspend fun execute(
        request: AiRequest,
        context: AiContext = AiContext.EMPTY
    ): AiResponse {
        request.validateForTextGeneration()

        val startedAt = System.currentTimeMillis()

        logger?.info(
            "AssistantExecutionService",
            "AI request started"
        )

        return try {
            val response = router.execute(
                request = request,
                context = context
            )

            if (response.text.isBlank()) {
                throw AiProviderException(
                    AiErrorType.EMPTY_RESPONSE,
                    "AI request completed without response text"
                )
            }

            logger?.info(
                "AssistantExecutionService",
                "AI request completed in ${System.currentTimeMillis() - startedAt}ms"
            )

            response
        } catch (error: CancellationException) {
            logger?.debug(
                "AssistantExecutionService",
                "AI request cancelled"
            )
            throw error
        } catch (error: Throwable) {
            logger?.error(
                "AssistantExecutionService",
                "AI request failed: ${error.message}"
            )
            throw error
        }
    }
}
