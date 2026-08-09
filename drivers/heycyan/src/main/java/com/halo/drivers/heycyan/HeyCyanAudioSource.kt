package com.halo.drivers.heycyan

import com.halo.sdk.driver.Driver
import com.halo.sdk.host.AudioFrame
import com.halo.sdk.host.AudioSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Boundary adapter for HeyCyan audio.
 *
 * The current driver SDK exposes text Input events only. Until it exposes PCM,
 * this source deliberately emits no frames rather than pretending text is audio.
 */
class HeyCyanAudioSource(private val driver: Driver) : AudioSource {
    override val frames: Flow<AudioFrame> = emptyFlow()

    override suspend fun start() = Unit

    override suspend fun stop() = Unit
}
