package com.halo.app.audio

import android.content.Context
import com.halo.sdk.host.AudioSource
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Keep an actual capture foreground only for its lifetime. */
class ForegroundAudioSource(private val context: Context, private val source: AudioSource,
    private val kind: HaloAudioForegroundService.Kind) : AudioSource by source {
    private val mutex = Mutex()
    private var lease: HaloAudioForegroundService.Lease? = null
    override suspend fun start() = mutex.withLock {
        if (lease != null) return@withLock
        val acquired = HaloAudioForegroundService.acquire(context, kind)
        try { source.start(); lease = acquired }
        catch (error: Exception) { acquired.release(); throw error }
    }
    override suspend fun stop() = withContext(NonCancellable) {
        mutex.withLock {
            try { source.stop() } finally { lease?.release(); lease = null }
        }
    }
    override suspend fun close() { stop(); source.close() }
}
