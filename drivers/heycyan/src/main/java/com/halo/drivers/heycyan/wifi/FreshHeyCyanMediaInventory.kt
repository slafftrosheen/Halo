package com.halo.drivers.heycyan.wifi

import com.halo.drivers.heycyan.protocol.HeyCyanEvent
import com.halo.drivers.heycyan.protocol.MediaCounts
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull

/** Fresh vendor-documented unsynced-media count; never reads cached telemetry.
 * Supply the driver's non-replaying live event stream. A timeout is unknown, not zero. */
class FreshHeyCyanMediaInventory(
    private val events: Flow<HeyCyanEvent>,
    private val request: suspend () -> Unit,
    private val deviceIsCurrent: (String) -> Boolean,
) {
    suspend fun read(deviceId: String, timeoutMs: Long = 3_000): MediaCounts? {
        check(deviceIsCurrent(deviceId)) { "Selected glasses changed" }
        val response = withTimeoutOrNull(timeoutMs) {
            val next = async(start = CoroutineStart.UNDISPATCHED) {
                events.filterIsInstance<HeyCyanEvent.MediaCountsUpdated>().first {
                    it.mediaType == 4 && it.photoCount >= 0 && it.videoCount >= 0 && it.audioCount >= 0
                }
            }
            try { request(); next.await() } finally { next.cancel() }
        }
        check(deviceIsCurrent(deviceId)) { "Selected glasses changed" }
        return response?.let { MediaCounts(it.photoCount, it.videoCount, it.audioCount, it.mediaType) }
    }
}
