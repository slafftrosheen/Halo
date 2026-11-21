package com.halo.app.ui.toolkit

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Main-thread handoff from the registered tool to the resumed Album entry. */
internal object HaloAlbumFindRequests {
    const val MAX_PHRASE = 180
    private const val TTL_MS = 10_000L
    internal data class Request(
        val id: String,
        val phrase: String,
        val adultRevision: Long,
        val expiresAt: Long,
    )
    private val requests = MutableStateFlow<Request?>(null)
    val pending = requests.asStateFlow()
    private val main = Handler(Looper.getMainLooper())

    fun dispatch(phrase: String, adultRevision: Long, destinations: MutableStateFlow<String?>): String? {
        val now = SystemClock.elapsedRealtime()
        val previous = requests.value
        if (previous != null && previous.expiresAt > now) return null
        if (!destinations.compareAndSet(null, "album")) return null
        val id = UUID.randomUUID().toString()
        requests.value = Request(id, phrase, adultRevision, now + TTL_MS)
        main.postDelayed({
            requests.value?.takeIf { it.id == id }?.let { requests.compareAndSet(it, null) }
        }, TTL_MS)
        return id
    }

    fun consume(request: Request, verifyAdult: (Long) -> Boolean): String? {
        if (!requests.compareAndSet(request, null)) return null
        return request.phrase.takeIf { SystemClock.elapsedRealtime() <= request.expiresAt && verifyAdult(request.adultRevision) }
    }
}
