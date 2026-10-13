package com.halo.app.nativeactions

import kotlinx.coroutines.CompletableDeferred
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Ephemeral ownership: after process death an old activity cannot replay an action. */
internal object NativeActivityRequests {
    data class Receipt(val completed: Boolean, val details: String)
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Receipt>>()
    private val receipts = ConcurrentHashMap<String, Receipt>()
    private val deadlines = ConcurrentHashMap<String, Long>()
    fun create(): Pair<String, CompletableDeferred<Receipt>> {
        val id = UUID.randomUUID().toString()
        val result = CompletableDeferred<Receipt>()
        check(pending.putIfAbsent(id, result) == null)
        deadlines[id] = System.currentTimeMillis() + 600_000
        if (pending.size > 32) pending.keys.filter { !active(it) }.forEach(::cancel)
        return id to result
    }
    fun active(id: String): Boolean = pending[id]?.isActive == true && System.currentTimeMillis() < (deadlines[id] ?: 0)
    fun complete(id: String, result: Receipt) {
        if (active(id)) {
            if (receipts.size >= 64) receipts.clear()
            receipts[id] = result
            pending.remove(id)?.complete(result)
        }
        deadlines.remove(id)
    }
    fun receipt(id: String): Receipt? = receipts[id]
    fun cancel(id: String) { deadlines.remove(id); pending.remove(id)?.cancel() }
    fun cancelAll() { pending.keys.toList().forEach(::cancel) }
}
