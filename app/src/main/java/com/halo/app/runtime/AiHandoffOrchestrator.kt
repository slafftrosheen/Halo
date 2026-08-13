package com.halo.app.runtime

import com.halo.engines.speech.RecognitionFinished
import com.halo.sdk.provider.AiRequest
import com.halo.sdk.provider.AiProviderRouter
import com.halo.sdk.runtime.EventBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import android.util.Log

/**
 * Orchestrates the handoff from a completed speech interaction to an AI provider.
 *
 * This component bridges the gap between sensory perception (speech ending)
 * and cognitive action (AI provider invocation).
 */
class AiHandoffOrchestrator(
    private val eventBus: EventBus,
    private val aiProviderRouter: AiProviderRouter,
    private val scope: CoroutineScope
) {
    /**
     * Starts observing the event bus for completed voice interactions.
     */
    fun start() {
        scope.launch {
            eventBus.observe(RecognitionFinished::class.java).collect {
                Log.i("HALO-AI", "provider=router operation=handoff trigger=RECOGNITION_FINISHED")
                
                eventBus.publish(com.halo.engines.speech.HandoffStarted())
                
                // For the first real loop, we trigger a generic AI request
                // which the router will forward to the Android Assistant provider.
                val request = AiRequest(
                    text = "HANDOFF"
                )
                
                try {
                    val response = aiProviderRouter.execute(request)
                    Log.i("HALO-AI", "provider=router operation=handoff result=SUCCESS response=${response.text}")
                } catch (e: Exception) {
                    Log.e("HALO-AI", "provider=router operation=handoff result=ERROR", e)
                }
            }
        }
    }
}
