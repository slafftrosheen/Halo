package com.halo.app.nativeactions

import kotlinx.coroutines.CompletableDeferred
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Ephemeral ownership: after process death an old activity cannot replay an action. */
internal object NativeActivityRequests {
    data class Receipt(val completed: Boolean, val details: String)
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Receipt>>()
    private val receipts = LinkedHashMap<String, Receipt>()
    private val deadlines = ConcurrentHashMap<String, Long>()
    @Synchronized fun create(): Pair<String, CompletableDeferred<Receipt>> {
        pending.keys.filter { !active(it) }.forEach(::cancel)
        check(pending.size < 32) { "Too many pending native requests" }
        val id = UUID.randomUUID().toString()
        val result = CompletableDeferred<Receipt>()
        check(pending.putIfAbsent(id, result) == null)
        deadlines[id] = System.nanoTime() + 600_000_000_000L
        return id to result
    }
    @Synchronized fun active(id: String): Boolean = pending[id]?.isActive == true &&
        deadlines[id]?.let { System.nanoTime() - it < 0 } == true
    @Synchronized fun complete(id: String, result: Receipt) {
        if (active(id)) {
            if (receipts.size >= 64) receipts.remove(receipts.keys.first())
            receipts[id] = result
            pending.remove(id)?.complete(result)
        } else pending.remove(id)?.cancel()
        deadlines.remove(id)
    }
    @Synchronized fun receipt(id: String): Receipt? = receipts[id]
    @Synchronized fun cancel(id: String) { deadlines.remove(id); pending.remove(id)?.cancel() }
    @Synchronized fun cancelAll() { pending.keys.toList().forEach(::cancel) }
}
