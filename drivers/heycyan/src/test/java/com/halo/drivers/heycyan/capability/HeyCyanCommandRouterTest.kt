package com.halo.drivers.heycyan.capability

import com.halo.drivers.heycyan.protocol.HeyCyanOperation
import com.halo.drivers.heycyan.transport.HeyCyanTransportRoutes
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HeyCyanCommandRouterTest {

    private class FakeBleExecutor : HeyCyanBleExecutor {
        var executeResult: Result<Unit> = Result.success(Unit)
        var invocationCount = 0

        override suspend fun execute(operation: HeyCyanOperation): Result<Unit> {
            invocationCount++
            return executeResult
        }
    }

    private class FakeRfcommExecutor : HeyCyanRfcommExecutor {
        var executeResult: Result<Unit> = Result.success(Unit)
        var invocationCount = 0

        override suspend fun execute(operation: HeyCyanOperation): Result<Unit> {
            invocationCount++
            return executeResult
        }
    }

    private lateinit var bleExecutor: FakeBleExecutor
    private lateinit var rfcommExecutor: FakeRfcommExecutor
    private lateinit var router: HeyCyanCommandRouter

    @Before
    fun setUp() {
        bleExecutor = FakeBleExecutor()
        rfcommExecutor = FakeRfcommExecutor()
        router = HeyCyanCommandRouter(bleExecutor, rfcommExecutor, HeyCyanTransportRoutes.operationRoutes)
    }

    @Test
    fun batteryRoutesToBlePrimary() = runTest {
        val result = router.execute(HeyCyanOperation.GetBattery)
        assertTrue(result.isSuccess)
        assertEquals(1, bleExecutor.invocationCount)
        assertEquals(0, rfcommExecutor.invocationCount)
    }

    @Test
    fun batteryMayFallbackToRfcommWhenBleFails() = runTest {
        bleExecutor.executeResult = Result.failure(
            HeyCyanCapabilityException(HeyCyanOperationFailure.Transport("BLE socket timeout"))
        )
        rfcommExecutor.executeResult = Result.success(Unit)

        val result = router.execute(HeyCyanOperation.GetBattery)
        assertTrue(result.isSuccess)
        assertEquals(1, bleExecutor.invocationCount)
        assertEquals(1, rfcommExecutor.invocationCount)
    }

    @Test
    fun photoTimeoutDoesNotFallbackAndDuplicatePhoto() = runTest {
        // MANDATORY TEST: TakePhoto is NOT_IDEMPOTENT. If BLE times out, router MUST NOT fallback to RFCOMM!
        bleExecutor.executeResult = Result.failure(
            HeyCyanCapabilityException(HeyCyanOperationFailure.Transport("BLE photo response timeout"))
        )
        rfcommExecutor.executeResult = Result.success(Unit)

        val result = router.execute(HeyCyanOperation.TakePhoto)
        assertTrue("Result must be failure for non-idempotent timeout", result.isFailure)
        assertEquals(1, bleExecutor.invocationCount)
        assertEquals("RFCOMM fallback MUST NOT be invoked for non-idempotent operation", 0, rfcommExecutor.invocationCount)
    }

    @Test
    fun deviceRejectionDoesNotFallback() = runTest {
        // If device rejected command (e.g. video conflict), do not fallback
        bleExecutor.executeResult = Result.failure(
            HeyCyanCapabilityException(HeyCyanOperationFailure.DeviceRejected("Video active"))
        )

        val result = router.execute(HeyCyanOperation.StartRecording)
        assertTrue(result.isFailure)
        assertEquals(1, bleExecutor.invocationCount)
        assertEquals(0, rfcommExecutor.invocationCount)
    }
}
