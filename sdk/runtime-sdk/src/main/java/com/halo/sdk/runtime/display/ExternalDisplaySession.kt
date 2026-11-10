package com.halo.sdk.runtime.display

import kotlinx.coroutines.flow.StateFlow

interface ExternalDisplayOutput {
    val state: StateFlow<ExternalDisplayState>
    suspend fun snapshot(): ExternalDisplayState
    suspend fun show(endpointToken: String, text: String, expectedRevision: Long? = null): ExternalDisplayReceipt
    suspend fun clear(expectedRevision: Long? = null, endpointToken: String? = null): ExternalDisplayReceipt
}

/** Observed host display, never an inferred brand, USB identity or tracking capability. */
data class ExternalDisplayConnection(val id: Int, val name: String, val width: Int, val height: Int)
data class ExternalDisplayEndpoint(val token: String, val name: String, val width: Int, val height: Int)
data class ExternalDisplayState(
    val foreground: Boolean = false,
    val endpoints: List<ExternalDisplayEndpoint> = emptyList(),
    val activeToken: String? = null,
    val message: String? = null,
    val revision: Long = 0,
)
data class ExternalDisplayReceipt(val status: String, val message: String, val sourceVersion: Long)

/** Host-thread confined. Connection-scoped tokens expire on detach, change or background. */
class ExternalDisplaySession(private val newToken: () -> String) {
    var state = ExternalDisplayState()
        private set
    private var connections = emptyMap<Int, Pair<ExternalDisplayConnection, ExternalDisplayEndpoint>>()

    fun refresh(foreground: Boolean, observed: List<ExternalDisplayConnection>, invalidated: Set<Int> = emptySet()) {
        val next = if (!foreground) emptyMap() else observed.take(MAX_DISPLAYS).associate { connection ->
            val previous = connections[connection.id]
            val endpoint = if (connection.id !in invalidated && previous?.first == connection) previous.second
                else ExternalDisplayEndpoint(newToken(), connection.name.take(120), connection.width, connection.height)
            connection.id to (connection to endpoint)
        }
        val endpoints = next.values.map { it.second }
        val active = state.activeToken?.takeIf { token -> endpoints.any { it.token == token } }
        if (foreground != state.foreground || next != connections) {
            state = state.copy(foreground = foreground, endpoints = endpoints, activeToken = active,
                message = if (state.activeToken != null && active == null) "External display output stopped." else null,
                revision = state.revision + 1)
        }
        connections = next
    }

    fun resolve(token: String, expectedRevision: Long? = null): Int? {
        if (!state.foreground || expectedRevision != null && expectedRevision != state.revision) return null
        return connections.entries.firstOrNull { it.value.second.token == token }?.key
    }

    fun presented(token: String): ExternalDisplayReceipt {
        check(resolve(token) != null)
        state = state.copy(activeToken = token, message = "Card submitted to Android. Check the external display.", revision = state.revision + 1)
        return receipt("DISPATCHED", state.message!!)
    }

    fun cleared(message: String = "External display output stopped."): ExternalDisplayReceipt {
        state = state.copy(activeToken = null, message = message, revision = state.revision + 1)
        return receipt("CLEARED", message)
    }

    fun rejected(message: String): ExternalDisplayReceipt {
        state = state.copy(message = message)
        return receipt("REJECTED", message)
    }

    private fun receipt(status: String, message: String) = ExternalDisplayReceipt(status, message, state.revision)
    companion object { const val MAX_DISPLAYS = 8; const val MAX_TEXT_LENGTH = 400 }
}
