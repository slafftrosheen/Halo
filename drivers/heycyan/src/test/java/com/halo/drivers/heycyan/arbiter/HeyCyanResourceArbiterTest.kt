package com.halo.drivers.heycyan.arbiter

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HeyCyanResourceArbiterTest {

    private val arbiter = HeyCyanResourceArbiter()

    @Test
    fun `non conflicting resources can be held concurrently`() {
        val lease1 = arbiter.tryAcquire("control", setOf(HeyCyanResource.CONTROL_CHANNEL))
        val lease2 = arbiter.tryAcquire("audio", setOf(HeyCyanResource.AUDIO_RECORDER))

        assertNotNull(lease1)
        assertNotNull(lease2)
        assertTrue(lease1!!.isActive)
        assertTrue(lease2!!.isActive)

        lease1.release()
        lease2.release()
        assertFalse(lease1.isActive)
        assertFalse(lease2.isActive)
    }

    @Test
    fun `camera and video recorder conflict`() {
        val cameraLease = arbiter.tryAcquire("camera", setOf(HeyCyanResource.CAMERA))
        assertNotNull(cameraLease)

        val videoLease = arbiter.tryAcquire("video", setOf(HeyCyanResource.VIDEO_RECORDER))
        assertNull("Video recorder should conflict with active camera lease", videoLease)

        cameraLease!!.release()
        val videoLeaseAfter = arbiter.tryAcquire("video", setOf(HeyCyanResource.VIDEO_RECORDER))
        assertNotNull("Video recorder should acquire after camera lease released", videoLeaseAfter)
        videoLeaseAfter!!.release()
    }

    @Test
    fun `ble bulk transfer and wifi transfer conflict`() {
        val bleLease = arbiter.tryAcquire("ble_thumbnail", setOf(HeyCyanResource.BLE_BULK_TRANSFER))
        assertNotNull(bleLease)

        val wifiLease = arbiter.tryAcquire("wifi_sync", setOf(HeyCyanResource.WIFI_TRANSFER))
        assertNull("Wi-Fi transfer should conflict with BLE bulk transfer", wifiLease)

        bleLease!!.release()
        val wifiLeaseAfter = arbiter.tryAcquire("wifi_sync", setOf(HeyCyanResource.WIFI_TRANSFER))
        assertNotNull(wifiLeaseAfter)
        wifiLeaseAfter!!.release()
    }

    @Test
    fun `ota conflicts with all resources`() {
        val otaLease = arbiter.tryAcquire("ota", setOf(HeyCyanResource.OTA))
        assertNotNull(otaLease)

        assertNull(arbiter.tryAcquire("control", setOf(HeyCyanResource.CONTROL_CHANNEL)))
        assertNull(arbiter.tryAcquire("camera", setOf(HeyCyanResource.CAMERA)))
        assertNull(arbiter.tryAcquire("audio", setOf(HeyCyanResource.AUDIO_RECORDER)))

        otaLease!!.release()
        assertNotNull(arbiter.tryAcquire("control", setOf(HeyCyanResource.CONTROL_CHANNEL)))
    }

    @Test
    fun `acquire suspends until conflicting lease is released`() = runTest {
        val lease1 = arbiter.tryAcquire("camera", setOf(HeyCyanResource.CAMERA))
        assertNotNull(lease1)

        val deferredVideo = async {
            arbiter.acquire("video", setOf(HeyCyanResource.VIDEO_RECORDER), timeoutMs = 1_000L)
        }

        delay(100)
        assertFalse(deferredVideo.isCompleted)

        lease1!!.release()
        val videoLease = deferredVideo.await()
        assertNotNull(videoLease)
        assertTrue(videoLease.isActive)
        videoLease.release()
    }

    @Test(expected = TimeoutCancellationException::class)
    fun `acquire times out when conflicting lease is not released`() = runTest {
        val lease1 = arbiter.tryAcquire("camera", setOf(HeyCyanResource.CAMERA))
        assertNotNull(lease1)

        arbiter.acquire("video", setOf(HeyCyanResource.VIDEO_RECORDER), timeoutMs = 100L)
    }

    @Test
    fun `withLease auto releases on exception`() = runTest {
        try {
            arbiter.withLease("camera", setOf(HeyCyanResource.CAMERA)) {
                throw RuntimeException("Simulated failure")
            }
        } catch (_: RuntimeException) {}

        assertFalse(arbiter.hasActiveLease(HeyCyanResource.CAMERA))
        val leaseAfter = arbiter.tryAcquire("camera", setOf(HeyCyanResource.CAMERA))
        assertNotNull(leaseAfter)
        leaseAfter!!.release()
    }
}
