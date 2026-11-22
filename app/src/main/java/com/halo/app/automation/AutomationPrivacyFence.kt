package com.halo.app.automation

import kotlinx.coroutines.sync.Mutex

/** Serialize dispatch and withdrawal of the same canonical automation on this process. */
internal object AutomationPrivacyFence {
    private class Entry(val gate: Mutex = Mutex(), var users: Int = 0)
    private val entries = mutableMapOf<String, Entry>()

    fun routine(id: String) = "routine:$id"
    fun watch(id: String) = "watch:$id"

    suspend fun <T> withOwner(id: String, action: suspend () -> T): T = withOwners(setOf(id), action)

    suspend fun <T> withOwners(ids: Set<String>, action: suspend () -> T): T {
        val held = synchronized(entries) {
            ids.sorted().map { id -> id to entries.getOrPut(id) { Entry() }.also { it.users++ } }
        }
        var locked = 0
        try {
            for ((_, entry) in held) {
                entry.gate.lock()
                locked++
            }
            return action()
        } finally {
            for (index in locked - 1 downTo 0) held[index].second.gate.unlock()
            synchronized(entries) {
                for ((id, entry) in held) if (--entry.users == 0) entries.remove(id)
            }
        }
    }
}
