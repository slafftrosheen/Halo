package com.halo.runtime.cognition.turn

import com.halo.cognition.language.InteractionId
import com.halo.sdk.runtime.EventBus
import com.halo.sdk.runtime.InteractionTurnChanged
import com.halo.sdk.runtime.InteractionTurnState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ActiveTurn(
    val interactionId: InteractionId,
    val job: Job,
)

data class ReplacedTurn(
    val interactionId: InteractionId,
)

interface ActiveTurnCoordinator {
    val activeTurn: StateFlow<ActiveTurn?>

    suspend fun replace(
        interactionId: InteractionId,
        job: Job,
    ): ReplacedTurn?

    suspend fun clear(
        interactionId: InteractionId,
        job: Job,
    )

    suspend fun cancel(reason: String)
}

class DefaultActiveTurnCoordinator(
    private val eventBus: EventBus
) : ActiveTurnCoordinator {
    private val mutex = Mutex()
    private val _activeTurn = MutableStateFlow<ActiveTurn?>(null)
    override val activeTurn = _activeTurn.asStateFlow()

    override suspend fun replace(
        interactionId: InteractionId,
        job: Job,
    ): ReplacedTurn? = mutex.withLock {
        val current = _activeTurn.value
        current?.job?.cancel()
        _activeTurn.value = ActiveTurn(interactionId, job)
        return current?.let { ReplacedTurn(it.interactionId) }
    }

    override suspend fun clear(
        interactionId: InteractionId,
        job: Job,
    ) = mutex.withLock {
        val current = _activeTurn.value
        if (current != null && current.interactionId == interactionId && current.job === job) {
            _activeTurn.value = null
        }
    }

    override suspend fun cancel(reason: String) = mutex.withLock {
        val current = _activeTurn.value
        if (current != null) {
            current.job.cancel()
            eventBus.publish(InteractionTurnChanged(state = InteractionTurnState.Cancelled(current.interactionId.value, reason)))
            _activeTurn.value = null
        }
    }
}
