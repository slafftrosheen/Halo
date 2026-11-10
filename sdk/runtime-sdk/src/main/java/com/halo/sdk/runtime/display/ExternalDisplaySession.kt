package com.halo.sdk.runtime.display

import kotlinx.coroutines.flow.StateFlow

interface ExternalDisplayOutput {
    val state: StateFlow<ExternalDisplayState>
    suspend fun snapshot(): ExternalDisplayState
    suspend fun show(endpointToken: String, text: String, expectedRevision: Long? = null,
        requestAllowed: () -> Boolean = { true }): ExternalDisplayReceipt
    suspend fun page(endpointToken: String, page: Int, expectedRevision: Long,
        requestAllowed: () -> Boolean = { true }): ExternalDisplayReceipt
    suspend fun clear(expectedRevision: Long? = null, endpointToken: String? = null,
        requestAllowed: () -> Boolean = { true }): ExternalDisplayReceipt
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
    val currentPage: Int = 0,
    val pageCount: Int = 0,
)
data class ExternalDisplayReceipt(val status: String, val message: String, val sourceVersion: Long,
    val currentPage: Int = 0, val pageCount: Int = 0)

/** Host-thread confined. Connection-scoped tokens expire on detach, change or background. */
class ExternalDisplaySession(private val newToken: () -> String) {
    var state = ExternalDisplayState()
        private set
    private var connections = emptyMap<Int, Pair<ExternalDisplayConnection, ExternalDisplayEndpoint>>()
    // Content is deliberately absent from public snapshots and never survives the active connection.
    private var pages = emptyList<String>()

    fun refresh(foreground: Boolean, observed: List<ExternalDisplayConnection>, invalidated: Set<Int> = emptySet()) {
        val next = if (!foreground) emptyMap() else observed.take(MAX_DISPLAYS).associate { connection ->
            val previous = connections[connection.id]
            val endpoint = if (connection.id !in invalidated && previous?.first == connection) previous.second
                else ExternalDisplayEndpoint(newToken(), connection.name.take(120), connection.width, connection.height)
            connection.id to (connection to endpoint)
        }
        val endpoints = next.values.map { it.second }
        val active = state.activeToken?.takeIf { token -> endpoints.any { it.token == token } }
        if (active == null) pages = emptyList()
        if (foreground != state.foreground || next != connections) {
            state = state.copy(foreground = foreground, endpoints = endpoints, activeToken = active,
                message = if (state.activeToken != null && active == null) "External display output stopped." else null,
                revision = state.revision + 1, currentPage = if (active == null) 0 else state.currentPage,
                pageCount = if (active == null) 0 else state.pageCount)
        }
        connections = next
    }

    fun resolve(token: String, expectedRevision: Long? = null): Int? {
        if (!state.foreground || expectedRevision != null && expectedRevision != state.revision) return null
        return connections.entries.firstOrNull { it.value.second.token == token }?.key
    }

    fun presented(token: String, textPages: List<String>): ExternalDisplayReceipt {
        check(resolve(token) != null)
        require(textPages.isNotEmpty() && textPages.size <= MAX_PAGES && textPages.all { it.length in 1..MAX_PAGE_LENGTH } &&
            textPages.sumOf { it.length } <= MAX_TEXT_LENGTH && textPages.any { it.isNotBlank() })
        pages = textPages.toList()
        state = state.copy(activeToken = token, message = "Text submitted to Android. Check the external display.",
            revision = state.revision + 1, currentPage = 1, pageCount = pages.size)
        return receipt("DISPATCHED", state.message!!)
    }

    /** Exact active output and revision are required even if the numeric page still exists. */
    fun pageText(token: String, page: Int, expectedRevision: Long): String? =
        if (state.activeToken != token || resolve(token, expectedRevision) == null) null else pages.getOrNull(page - 1)

    fun pagePresented(token: String, page: Int, expectedRevision: Long): ExternalDisplayReceipt {
        check(pageText(token, page, expectedRevision) != null)
        state = state.copy(currentPage = page, message = "Page $page of ${pages.size} submitted to Android.", revision = state.revision + 1)
        return receipt("DISPATCHED", state.message!!)
    }

    fun cleared(message: String = "External display output stopped."): ExternalDisplayReceipt {
        pages = emptyList()
        state = state.copy(activeToken = null, message = message, revision = state.revision + 1, currentPage = 0, pageCount = 0)
        return receipt("CLEARED", message)
    }

    fun rejected(message: String): ExternalDisplayReceipt {
        state = state.copy(message = message)
        return receipt("REJECTED", message)
    }

    private fun receipt(status: String, message: String) = ExternalDisplayReceipt(status, message, state.revision,
        state.currentPage, state.pageCount)
    companion object {
        const val MAX_DISPLAYS = 8
        const val MAX_TEXT_LENGTH = 8000
        const val MAX_PAGE_LENGTH = 400
        const val MAX_PAGE_LINES = 12
        const val MAX_PAGES = 80

        /** Preserve all content (except canonicalizing line endings); never split a UTF-16 surrogate pair. */
        fun paginate(text: String): List<String>? {
            if (text.isBlank() || text.length > MAX_TEXT_LENGTH) return null
            val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
            val result = mutableListOf<String>()
            var start = 0
            while (start < normalized.length) {
                var end = (start + MAX_PAGE_LENGTH).coerceAtMost(normalized.length)
                var lineBreaks = 0
                for (index in start until end) {
                    if (normalized[index] == '\n' && ++lineBreaks == MAX_PAGE_LINES - 1) { end = index + 1; break }
                }
                if (end < normalized.length) {
                    if (Character.isHighSurrogate(normalized[end - 1]) && Character.isLowSurrogate(normalized[end])) end--
                    val minimum = start + MAX_PAGE_LENGTH / 2
                    val newline = (end - 1 downTo minimum).firstOrNull { normalized[it] == '\n' }
                    val whitespace = newline ?: (end - 1 downTo minimum).firstOrNull { normalized[it].isWhitespace() }
                    if (whitespace != null) end = whitespace + 1
                }
                result += normalized.substring(start, end)
                if (result.size > MAX_PAGES) return null
                start = end
            }
            return result
        }
    }
}
