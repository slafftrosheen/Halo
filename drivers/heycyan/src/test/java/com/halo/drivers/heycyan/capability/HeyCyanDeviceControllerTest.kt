package com.halo.drivers.heycyan.capability

import com.halo.drivers.heycyan.media.HeyCyanMediaManifest
import com.halo.drivers.heycyan.media.sync.HeyCyanMediaDiffer
import com.halo.drivers.heycyan.media.sync.HeyCyanMediaSyncResult
import com.halo.drivers.heycyan.media.sync.HeyCyanMediaSynchronizer
import com.halo.drivers.heycyan.media.sync.MediaSyncRequest
import com.halo.sdk.runtime.embodiment.heycyan.HeyCyanOperation
import com.halo.drivers.heycyan.wifi.HeyCyanMediaApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HeyCyanDeviceControllerTest {

    private class CountingBleExecutor : HeyCyanBleExecutor {
        var count = 0
        override suspend fun execute(operation: HeyCyanOperation): Result<Unit> {
            count++
            return Result.success(Unit)
        }
    }

    private class CountingRfcommExecutor : HeyCyanRfcommExecutor {
        var count = 0
        override suspend fun execute(operation: HeyCyanOperation): Result<Unit> {
            count++
            return Result.success(Unit)
        }
    }

    private class FakeMediaApi : HeyCyanMediaApi {
        override suspend fun getMediaConfig(): Result<HeyCyanMediaManifest> = Result.success(
            HeyCyanMediaManifest(deviceId = "dev", generatedAt = 1000L, items = emptyList())
        )

        override suspend fun download(
            media: com.halo.drivers.heycyan.media.HeyCyanMedia,
            destinationPath: String,
            progress: (com.halo.drivers.heycyan.media.MediaDownloadProgress) -> Unit
        ): Result<com.halo.drivers.heycyan.media.MediaDownloadResult> {
            throw UnsupportedOperationException("Not needed")
        }
    }

    private lateinit var bleExecutor: CountingBleExecutor
    private lateinit var rfcommExecutor: CountingRfcommExecutor
    private lateinit var commandRouter: HeyCyanCommandRouter
    private lateinit var controller: HeyCyanDeviceController

    @Before
    fun setUp() {
        bleExecutor = CountingBleExecutor()
        rfcommExecutor = CountingRfcommExecutor()
        commandRouter = HeyCyanCommandRouter(bleExecutor, rfcommExecutor)
        controller = HeyCyanDeviceController(commandRouter)
    }

    @Test
    fun executeCommandDispatchesToCommandRouter() = runTest {
        val result = controller.execute(HeyCyanOperation.GetBattery)
        assertTrue(result.isSuccess)
        assertEquals(1, bleExecutor.count)
        assertEquals(0, rfcommExecutor.count)
    }
}
