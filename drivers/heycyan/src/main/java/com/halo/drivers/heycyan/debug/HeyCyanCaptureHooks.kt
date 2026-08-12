package com.halo.drivers.heycyan.debug

import com.halo.drivers.heycyan.RealHeyCyanDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Debug hooks to trigger HeyCyan operations for protocol capture.
 */
class HeyCyanCaptureHooks(
    private val driver: RealHeyCyanDriver,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    private var videoToggleCount = 0
    private var audioToggleCount = 0

    fun triggerPhoto() = scope.launch {
        HeyCyanLogger.i("HALO-TX-TRACE", "Hook: triggerPhoto called")
        driver.capabilityProvider.execute("heycyan.camera.photo")
    }

    fun triggerVideoToggle() = scope.launch {
        videoToggleCount++
        HeyCyanLogger.i("HALO-TX-TRACE", "Hook: triggerVideoToggle called (ACTION_$videoToggleCount)")
        driver.capabilityProvider.execute("heycyan.camera.video")
    }

    fun triggerAudioToggle() = scope.launch {
        audioToggleCount++
        HeyCyanLogger.i("HALO-TX-TRACE", "Hook: triggerAudioToggle called (ACTION_$audioToggleCount)")
        driver.capabilityProvider.execute("heycyan.audio.recording")
    }

    fun triggerMediaCount() = scope.launch {
        HeyCyanLogger.i("HALO-TX-TRACE", "Hook: triggerMediaCount called")
        driver.capabilityProvider.execute("heycyan.media_inventory")
    }
}
