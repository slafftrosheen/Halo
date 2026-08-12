package com.halo.drivers.heycyan.debug

import com.halo.drivers.heycyan.RealHeyCyanDriver
import com.halo.drivers.heycyan.protocol.HeyCyanCommand
import com.halo.drivers.heycyan.protocol.HeyCyanMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Debug hooks to trigger HeyCyan operations for protocol capture.
 *
 * These hooks use the existing driver implementation to perform actions,
 * allowing the capture infrastructure to record the exact wire traffic.
 */
class HeyCyanCaptureHooks(
    private val driver: RealHeyCyanDriver,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    fun triggerPhoto() = scope.launch {
        driver.capabilityProvider.execute("heycyan.camera.take_photo")
    }

    fun triggerVideoStart() = scope.launch {
        driver.capabilityProvider.execute("heycyan.camera.video_start")
    }

    fun triggerVideoStop() = scope.launch {
        driver.capabilityProvider.execute("heycyan.camera.video_stop")
    }

    fun triggerRecordStart() = scope.launch {
        driver.capabilityProvider.execute("heycyan.audio.record_start")
    }

    fun triggerRecordStop() = scope.launch {
        driver.capabilityProvider.execute("heycyan.audio.record_stop")
    }

    fun triggerMediaCount() = scope.launch {
        // We might need to expose executeCommand or similar on the driver if 
        // there is no high-level capability for it yet.
        // For now, these are the primary targets.
    }
}
