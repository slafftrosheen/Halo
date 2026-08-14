package com.halo.sdk.runtime.embodiment

import com.halo.sdk.runtime.*

class BuddyBotGateway(
    private val eventBus: EventBus,
    private val sessionManager: DeviceSessionManager
) : EmbodimentGateway {
    
    override suspend fun dispatch(intent: EmbodimentIntent) {
        // Find the active BuddyBot session
        // In a real system we'd route by deviceId, but for now we pick the first BuddyBot
        val activeSession = sessionManager.activeSessions.value.find { 
            it.identity.deviceType == "BuddyBot" 
        }

        if (activeSession != null && activeSession.state.value == SessionState.READY) {
            activeSession.dispatch(intent)
        } else {
            // Drop or return FAILED due to no connection
            val deviceIdentity = DeviceIdentity("unavailable", "BuddyBot", "Unavailable")
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
