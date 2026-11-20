package com.halo.app.capture

import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.halo.app.MainActivity
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.ui.shell.HaloScreenCatalog
import com.halo.drivers.heycyan.protocol.HeyCyanConnectionState
import com.halo.drivers.heycyan.protocol.HeyCyanEvent
import com.halo.sdk.runtime.embodiment.heycyan.HeyCyanOperation
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Opt-in hardware probe. Uses the existing selected-device command owner.
 * Read-only unless a2Record explicitly requests a five-second video/audio clip.
 * No pairing, download, deletion, reset or provider operation.
 * Local raw responses are evidence, not synthetic protocol qualification. */
@RunWith(AndroidJUnit4::class)
class A2MediaInventoryPhysicalTest {
    @Test fun readFreshUnsyncedInventory() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Requires an explicitly authorized physical A2", args.getString("a2MediaProbe") == "true")
        val expected = requireNotNull(args.getString("a2Address"))
        require(expected.matches(Regex("[0-9A-F]{2}(:[0-9A-F]{2}){5}")))
        val label = requireNotNull(args.getString("a2Label"))
        require(label.matches(Regex("[a-z0-9-]{1,48}")))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assertFalse(context.getSystemService(KeyguardManager::class.java).isDeviceLocked)
        context.startActivity(Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(HaloScreenCatalog.EXTRA_SCREEN, "devices"))
        withTimeout(60_000) {
            Graph.kidsPreferences.loadedState.first { it }
            while (!com.halo.app.ui.mini.HaloHostVisibility.active.value) delay(100)
        }
        assertFalse(Graph.kidsPreferences.enabled.value)
        val driver = Graph.hostGraph.heyCyanDriver
        withTimeout(60_000) {
            driver.deviceState.first { it.connection == HeyCyanConnectionState.READY && it.transportIdentity?.controlBleAddress == expected }
        }
        assertEquals(expected, driver.activeDeviceId)
        val capture = File(context.cacheDir, "a2-inventory-$label-${System.currentTimeMillis()}.txt")
        capture.writeText("Existing GetMediaInventory; fresh raw RX observations\n")
        val raw = launch(start = CoroutineStart.UNDISPATCHED) {
            driver.rawNotificationFrames.collect { frame ->
                capture.appendText("${System.currentTimeMillis()} ${frame.joinToString("") { "%02x".format(it) }}\n")
            }
        }
        try {
            suspend fun inventory(stage: String) {
              val response = withTimeout(8_000) {
                val next = async(start = CoroutineStart.UNDISPATCHED) {
                    driver.heyCyanEvents.filterIsInstance<HeyCyanEvent.MediaCountsUpdated>().first { it.mediaType == 4 }
                }
                driver.capabilityController.execute(HeyCyanOperation.GetMediaInventory).getOrThrow()
                next.await()
              }
              assertEquals(expected, driver.activeDeviceId)
              assertEquals(expected, driver.deviceState.value.transportIdentity?.controlBleAddress)
              val result = "A2_INVENTORY label=$label stage=$stage photos=${response.photoCount} videos=${response.videoCount} audio=${response.audioCount} type=${response.mediaType}"
              capture.appendText("$result\n")
              instrumentation.sendStatus(0, Bundle().apply { putString("stream", "$result\n") })
            }
            inventory("before")
            val recording = args.getString("a2Record")
            if (recording != null) {
                require(recording in setOf("video", "audio"))
                val start = if (recording == "video") HeyCyanOperation.StartVideo else HeyCyanOperation.StartRecording
                val stop = if (recording == "video") HeyCyanOperation.StopVideo else HeyCyanOperation.StopRecording
                driver.capabilityController.execute(start).getOrThrow()
                try {
                    withTimeout(1_500) { driver.deviceState.first {
                        if (recording == "video") it.videoRecordingState == com.halo.drivers.heycyan.protocol.VideoRecordingState.RECORDING
                        else it.audioRecordingState == com.halo.drivers.heycyan.protocol.AudioRecordingState.RECORDING
                    } }
                    delay(5_000)
                } finally {
                    withContext(NonCancellable) { withTimeout(5_000) {
                        assertEquals(expected, driver.activeDeviceId)
                        driver.capabilityController.execute(stop).getOrThrow()
                    } }
                }
                withTimeout(1_500) { driver.deviceState.first {
                    if (recording == "video") it.videoRecordingState == com.halo.drivers.heycyan.protocol.VideoRecordingState.IDLE
                    else it.audioRecordingState == com.halo.drivers.heycyan.protocol.AudioRecordingState.IDLE
                } }
                delay(2_000)
                inventory("after")
            }
        } finally { raw.cancelAndJoin() }
    }
}
