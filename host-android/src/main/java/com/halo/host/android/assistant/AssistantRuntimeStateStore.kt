package com.halo.host.android.assistant

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class AssistantState {
    NOT_ELIGIBLE,
    ELIGIBLE,
    ROLE_REQUESTED,
    ROLE_HELD,
    SERVICE_READY,
    SESSION_CREATED,
    SESSION_SHOWN,
    LISTENING,
    TRANSCRIBING,
    THINKING,
    WAITING_FOR_INPUT,
    RESPONDING,
    COMPLETED,
    FAILED
}

data class AssistantRecognitionDiagnostics(
    val serviceCreatedCount: Long = 0,
    val startListeningCount: Long = 0,
    val lastRecognizerIntentAction: String? = null,
    val lastInvocationAtMs: Long? = null,
    val compatibilityEndpointOnly: Boolean = true,
)

data class AssistantRuntimeSnapshot(
    val serviceReady: Boolean = false,
    val sessionServiceCreated: Boolean = false,
    val sessionCreated: Boolean = false,
    val sessionVisible: Boolean = false,
    val interactionId: String? = null,
    val state: AssistantState = AssistantState.NOT_ELIGIBLE,
    val lastInvocationSource: String? = null,
    val lastFailure: String? = null,
    val inputRoute: String? = null,
    val outputRoute: String? = null,
    val lastResponseAtMs: Long? = null,
    val lastTransitionAtMs: Long = System.currentTimeMillis(),
    val recognition: AssistantRecognitionDiagnostics = AssistantRecognitionDiagnostics(),
)

/** Process-level, content-free truth for Android assistant lifecycle diagnostics. */
object AssistantRuntimeStateStore {
    private val mutableState = MutableStateFlow(AssistantRuntimeSnapshot())
    val state: StateFlow<AssistantRuntimeSnapshot> = mutableState.asStateFlow()

    fun serviceReady(ready: Boolean) = transition {
        if (ready) copy(serviceReady = true, state = AssistantState.SERVICE_READY)
        else copy(
            serviceReady = false,
            sessionServiceCreated = false,
            sessionCreated = false,
            sessionVisible = false,
            interactionId = null,
            state = AssistantState.ROLE_HELD,
        )
    }
    fun sessionServiceCreated() = transition { copy(sessionServiceCreated = true) }
    fun sessionCreated() = transition { copy(sessionCreated = true, state = AssistantState.SESSION_CREATED) }
    fun sessionDestroyed() = transition {
        copy(sessionCreated = false, sessionVisible = false, interactionId = null, state = AssistantState.SERVICE_READY)
    }
    fun sessionVisible(visible: Boolean) = transition { 
        copy(sessionVisible = visible, state = if (visible) AssistantState.SESSION_SHOWN else AssistantState.SESSION_CREATED) 
    }

    fun invocationStarted(interactionId: String, source: String) = transition {
        copy(
            interactionId = interactionId,
            lastInvocationSource = source,
            state = AssistantState.SESSION_SHOWN,
            lastFailure = null,
            inputRoute = null,
            outputRoute = null,
        )
    }

    fun capture(interactionId: String?, newState: AssistantState, inputRoute: String? = null) = transition {
        if (interactionId == null || this.interactionId != interactionId) this
        else copy(state = newState, inputRoute = inputRoute ?: this.inputRoute)
    }

    fun outputRoute(interactionId: String?, route: String) = transition {
        if (interactionId == null || this.interactionId != interactionId) this
        else copy(outputRoute = route)
    }

    fun failed(interactionId: String?, reason: String) = transition {
        if (interactionId == null || this.interactionId != interactionId) this
        else copy(state = AssistantState.FAILED, lastFailure = reason.take(240))
    }

    fun responseCompleted(interactionId: String?) = transition {
        if (interactionId == null || this.interactionId != interactionId) this
        else copy(state = AssistantState.COMPLETED, lastResponseAtMs = System.currentTimeMillis())
    }

    fun invocationEnded(interactionId: String) = transition {
        if (this.interactionId != interactionId) this
        else copy(
            interactionId = null,
            sessionVisible = false,
            state = AssistantState.SERVICE_READY,
        )
    }

    fun recognitionServiceCreated() = transition {
        copy(recognition = recognition.copy(serviceCreatedCount = recognition.serviceCreatedCount + 1))
    }

    fun recognitionRequested(intentAction: String?) = transition {
        copy(
            recognition = recognition.copy(
                startListeningCount = recognition.startListeningCount + 1,
                lastRecognizerIntentAction = intentAction?.take(120),
                lastInvocationAtMs = System.currentTimeMillis(),
            )
        )
    }

    private inline fun transition(crossinline transform: AssistantRuntimeSnapshot.() -> AssistantRuntimeSnapshot) {
        mutableState.update { current ->
            val next = current.transform()
            if (next == current) current else next.copy(lastTransitionAtMs = System.currentTimeMillis())
        }
    }
}
