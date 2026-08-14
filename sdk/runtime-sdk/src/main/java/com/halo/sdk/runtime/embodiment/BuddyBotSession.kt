package com.halo.sdk.runtime.embodiment

import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.halo.sdk.runtime.embodiment.ExecutionResult
import com.halo.sdk.runtime.embodiment.ExecutionStatus
import com.halo.sdk.runtime.EventBus
import com.halo.sdk.runtime.EmbodimentExecutionEvent

import com.halo.sdk.runtime.DeviceIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


import com.halo.sdk.runtime.ExecutionMode

/**
 * A semantic session for BuddyBot. Wraps a raw transport (like WebSocket)
 * and handles the v1 protocol handshake, JSON translation, and intent tracking.
 */
class BuddyBotSession(
    override val identity: DeviceIdentity,
    private val transport: EmbodimentTransport,
    private val eventBus: EventBus,
    private val executionMode: ExecutionMode = ExecutionMode.LIVE
) : EmbodimentSession {

    private val _state = MutableStateFlow(SessionState.SAVED)
    override val state: StateFlow<SessionState> = _state.asStateFlow()
    private var requestIdCounter = 1

    init {
        // Observe raw transport connection state
        // In a real app, we'd use a coroutine scope to collect this
        // transport.connectionState.collect { ... }

        transport.onMessageReceived { message ->
            try {
                val isExecResult = message.contains("\"type\":\"exec_result\"") || message.contains("\"type\": \"exec_result\"")
                if (isExecResult) {
                    val intentIdMatch = Regex("\"intentId\"\\s*:\\s*\"([^\"]+)\"").find(message)
                    val statusMatch = Regex("\"status\"\\s*:\\s*\"([^\"]+)\"").find(message)
                    val reasonMatch = Regex("\"reason\"\\s*:\\s*\"([^\"]+)\"").find(message)
                    
                    val intentId = intentIdMatch?.groupValues?.get(1) ?: ""
                    val statusStr = statusMatch?.groupValues?.get(1) ?: ""
                    val reason = reasonMatch?.groupValues?.get(1)
                    
                    val status = if (statusStr == "SUCCEEDED") ExecutionStatus.SUCCEEDED else ExecutionStatus.FAILED
                    
                    if (intentId.isNotEmpty()) {
                        CoroutineScope(Dispatchers.IO).launch {
                            eventBus.publish(EmbodimentExecutionEvent(
                                device = identity,
                                result = ExecutionResult(
                                    intentId = intentId,
                                    status = status,
                                    reason = reason,
                                    timestamp = System.currentTimeMillis(),
                                    embodimentId = identity.deviceId
                                )
                            ))
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore parse errors
            }
        }
    }

    override suspend fun connect() {
        _state.value = SessionState.CONNECTING
        if (executionMode == ExecutionMode.REPLAY) {
            _state.value = SessionState.CONNECTED
            return
        }
        transport.connect()
        // Once connected, we send handshake
        // _state.value = SessionState.CONNECTED
        // sendHandshake()
    }

    override suspend fun disconnect() {
        _state.value = SessionState.DISCONNECTING
        if (executionMode != ExecutionMode.REPLAY) {
            transport.disconnect()
        }
        _state.value = SessionState.DISCONNECTED
    }

    override suspend fun dispatch(intent: EmbodimentIntent) {
        if (_state.value != SessionState.READY && executionMode != ExecutionMode.REPLAY) {
            // Cannot dispatch if not ready
            return
        }
        if (executionMode == ExecutionMode.REPLAY) {
            // In replay mode, the execution event is provided by the trace player
            return
        }

        val map = mutableMapOf<String, Any>()
        map["v"] = 1
        map["id"] = requestIdCounter++
        map["intentId"] = intent.intentId
        
        when (intent.kind) {
            IntentKind.MOVE -> {
                map["type"] = "move"
                val mode = when (intent.driveMode) {
                    DriveMode.FORWARD -> "forward"
                    DriveMode.REVERSE -> "reverse"
                    DriveMode.ROTATE_LEFT -> "turn_left"
                    DriveMode.ROTATE_RIGHT -> "turn_right"
                    else -> "stop"
                }
                map["mode"] = mode
                map["durationMs"] = if (intent.durationMs > 0) intent.durationMs else 1000
            }
            IntentKind.STOP -> {
                map["type"] = "stop"
            }
            IntentKind.ARM -> {
                map["type"] = "arm"
            }
            IntentKind.DISARM -> {
                map["type"] = "disarm"
            }
            IntentKind.ACTION -> {
                map["type"] = "action"
                val actionName = when (intent.action) {
                    ActionId.NOD_YES -> "wave"
                    ActionId.SHAKE_NO -> "wave" // map reasonably
                    ActionId.CELEBRATE -> "celebrate"
                    ActionId.LOOK_LEFT -> "look_left"
                    ActionId.LOOK_RIGHT -> "look_right"
                    ActionId.SHIVER -> "shiver"
                    else -> "none"
                }
                map["action"] = actionName
            }
            IntentKind.SET_MOOD -> {
                map["type"] = "mood"
                val moodName = when (intent.mood) {
                    Mood.HAPPY -> "happy"
                    Mood.CURIOUS -> "curious"
                    Mood.SLEEPY -> "sleepy"
                    Mood.SURPRISED -> "excited"
                    Mood.ANGRY -> "alert"
                    Mood.SCARED -> "alert"
                    Mood.IDLE -> "idle"
                    else -> "idle"
                }
                map["mood"] = moodName
            }
            IntentKind.NEXT_PERSONA -> {
                map["type"] = "persona_next"
            }
            IntentKind.ACCESSORY -> {
                map["type"] = "accessory"
                map["index"] = intent.accessoryIndex
                map["active"] = intent.accessoryActive
            }
            else -> return
        }

        val jsonStr = map.entries.joinToString(prefix = "{", postfix = "}") { (k, v) ->
            if (v is String) "\"$k\":\"$v\"" else "\"$k\":$v"
        }
        transport.send(jsonStr)
    }
}
