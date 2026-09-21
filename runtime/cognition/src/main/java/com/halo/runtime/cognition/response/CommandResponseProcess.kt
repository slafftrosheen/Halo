package com.halo.runtime.cognition.response

import com.halo.runtime.cognition.ExecutionResultPublished
import com.halo.runtime.cognition.blackboard.Blackboard
import com.halo.runtime.cognition.blackboard.PlanKnowledge
import com.halo.sdk.runtime.AssistantResponsePublished
import com.halo.sdk.runtime.EventBus
import com.halo.sdk.runtime.ExecutionFinished
import com.halo.sdk.runtime.ResponseDelivery
import com.halo.sdk.runtime.RuntimeLogger
import com.halo.sdk.runtime.TtsRequested
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CommandResponseProcess(
    private val eventBus: EventBus,
    private val blackboard: Blackboard,
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
                            val delivery = responseDeliveryForPlan(event.planId)
                            logger?.info(
                                "HALO-TXN",
                                "execution=${event.id} plan=${event.planId} tasks=${results.size} " +
                                    "delivery=$delivery response=\"$responseText\""
                            )
                            eventBus.publish(
                                AssistantResponsePublished(
                                    text = responseText,
                                    delivery = delivery,
                                    transactionId = event.planId
                                )
                            )
                            if (delivery == ResponseDelivery.SPEECH_AND_VISUAL) {
                                eventBus.publish(TtsRequested(text = responseText, transactionId = event.planId))
                            }
                        }
                    }
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        pendingResults.clear()
    }

    private fun responseDeliveryForPlan(planId: String): ResponseDelivery {
        val plan = blackboard.flow.value.asReversed()
            .mapNotNull { knowledge -> (knowledge.payload as? PlanKnowledge)?.plan }
            .firstOrNull { it.id.toString() == planId }

        return plan?.responseDelivery ?: ResponseDelivery.VISUAL_ONLY
    }
}
