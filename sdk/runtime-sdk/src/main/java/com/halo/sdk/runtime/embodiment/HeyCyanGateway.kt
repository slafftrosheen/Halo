package com.halo.sdk.runtime.embodiment

import com.halo.sdk.runtime.*

class HeyCyanGateway(
    private val eventBus: EventBus,
    private val sessionManager: DeviceSessionManager,
    private val executionMode: ExecutionMode = ExecutionMode.LIVE
) : EmbodimentGateway {
    
    override suspend fun dispatch(intent: EmbodimentIntent) {
        if (executionMode == ExecutionMode.REPLAY) {
            // Execution event will be injected by the replay player
            return
        }

        val activeSession = sessionManager.activeSessions.value.find { 
            it.identity.deviceType == "HeyCyan" 
        }

        if (activeSession != null && activeSession.state.value == SessionState.READY) {
            activeSession.dispatch(intent)
        } else {
            val deviceIdentity = DeviceIdentity("unavailable", "HeyCyan", "Unavailable")
            eventBus.publish(EmbodimentExecutionEvent(
                device = deviceIdentity,
                result = ExecutionResult(
                    intentId = intent.intentId,
                    status = ExecutionStatus.FAILED,
                    reason = "No active session",
                    timestamp = System.currentTimeMillis(),
                    embodimentId = deviceIdentity.deviceId
                )
            ))
        }
    }
}
