package com.halo.drivers.heycyan.capability

import com.halo.drivers.heycyan.mock.MockBleExecutor
import com.halo.drivers.heycyan.mock.MockRfcommExecutor
import com.halo.sdk.runtime.embodiment.heycyan.HeyCyanOperation
import com.halo.drivers.heycyan.transport.HeyCyanTransportRoutes
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * PR-030 Criteria: CR-01 through CR-07
 *
 * Verifies the [HeyCyanCommandRouter] transport routing rules using in-process mocks.
 * No Android SDK, no BLE hardware, no network required.
 */
class HeyCyanTransportRoutingTest {

    private lateinit var ble: MockBleExecutor
    private lateinit var rfcomm: MockRfcommExecutor
    private lateinit var router: HeyCyanCommandRouter

    @Before
    fun setUp() {
        ble = MockBleExecutor()
        rfcomm = MockRfcommExecutor()
        router = HeyCyanCommandRouter(
            bleExecutor = ble,
            rfcommExecutor = rfcomm,
        )
    }

    // --- CR-01 ---
    /**
     * Battery query has preferred=BLE_GATT in [HeyCyanTransportRoutes].
     * When BLE succeeds, only BLE is called.
     */
    @Test
    fun batteryRoutesToBle() = runTest {
        ble.behaviour = MockBleExecutor.Behaviour.Succeed

        val result = router.execute(HeyCyanOperation.GetBattery)

        assertTrue("Expected success", result.isSuccess)
        assertEquals("BLE should have received exactly 1 call", 1, ble.receivedOperations.size)
        assertTrue("RFCOMM should not have been called", rfcomm.receivedOperations.isEmpty())
    }

    // --- CR-02 ---
    /**
     * GetBattery is IDEMPOTENT with fallback=RFCOMM.
     * When BLE fails, router automatically falls back to RFCOMM.
     */
    @Test
    fun idempotentFallbackToRfcomm() = runTest {
        ble.behaviour = MockBleExecutor.Behaviour.Fail()
        rfcomm.behaviour = MockRfcommExecutor.Behaviour.Succeed

        val result = router.execute(HeyCyanOperation.GetBattery)

        assertTrue("Expected fallback success", result.isSuccess)
        assertEquals("BLE should have been attempted", 1, ble.receivedOperations.size)
        assertEquals("RFCOMM fallback should have been called", 1, rfcomm.receivedOperations.size)
    }

    // --- CR-03 ---
    /**
     * TakePhoto is NOT_IDEMPOTENT, fallback=null.
     * When BLE times out (Transport failure), router must NOT fall back to RFCOMM
     * to prevent duplicate physical photo actions.
     */
    @Test
    fun photoNoFallbackOnTimeout() = runTest {
        ble.behaviour = MockBleExecutor.Behaviour.Fail() // simulates timeout/ambiguous outcome
        rfcomm.behaviour = MockRfcommExecutor.Behaviour.Succeed

        val result = router.execute(HeyCyanOperation.TakePhoto)

        assertTrue("Expected failure — non-idempotent guard", result.isFailure)
        assertEquals("BLE should have been attempted", 1, ble.receivedOperations.size)
        assertTrue(
            "RFCOMM must NOT have been called (non-idempotent guard)",
            rfcomm.receivedOperations.isEmpty()
        )
    }

    // --- CR-04 ---
    /**
     * An operation with no route defined in [HeyCyanTransportRoutes.operationRoutes]
     * must return a failure immediately without attempting any transport.
     */
    @Test
    fun unsupportedOperationReturnsFailure() = runTest {
        // SynchronizeMedia is routed to WIFI transport, which the router classifies as Unsupported
        val result = router.execute(HeyCyanOperation.SynchronizeMedia)

        assertTrue("Expected failure for Wi-Fi-only operation in command router", result.isFailure)
        // Verify it's an Unsupported failure
        val ex = result.exceptionOrNull()
        assertNotNull(ex)
        assertTrue(
            "Expected HeyCyanCapabilityException with Unsupported failure",
            ex is HeyCyanCapabilityException &&
                ex.failure is HeyCyanOperationFailure.Unsupported
        )
    }

    // --- CR-05 ---
    /**
     * When BLE returns DeviceRejected, the router must NOT fall back to RFCOMM.
     * DeviceRejected means the glasses actively refused the command — retrying via
     * another transport cannot change the device's decision.
     */
    @Test
    fun noFallbackOnDeviceRejected() = runTest {
        ble.behaviour = MockBleExecutor.Behaviour.Reject
        rfcomm.behaviour = MockRfcommExecutor.Behaviour.Succeed

        val result = router.execute(HeyCyanOperation.GetBattery)

        assertTrue("Expected failure — device rejected", result.isFailure)
        assertTrue("RFCOMM must not be called on DeviceRejected", rfcomm.receivedOperations.isEmpty())
        val ex = result.exceptionOrNull()
        assertTrue(
            "Expected DeviceRejected failure",
            ex is HeyCyanCapabilityException &&
                ex.failure is HeyCyanOperationFailure.DeviceRejected
        )
    }

    // --- CR-06 ---
    /**
     * When BLE returns ProtocolNotEstablished, the router must NOT fall back to RFCOMM.
     * ProtocolNotEstablished means the command packet framing is unresolved — sending
     * it via an alternative transport would still transmit an unverified packet.
     */
    @Test
    fun noFallbackOnProtocolNotEstablished() = runTest {
        ble.behaviour = MockBleExecutor.Behaviour.Protocol
        rfcomm.behaviour = MockRfcommExecutor.Behaviour.Succeed

        val result = router.execute(HeyCyanOperation.GetBattery)

        assertTrue("Expected failure — protocol not established", result.isFailure)
        assertTrue("RFCOMM must not be called on ProtocolNotEstablished", rfcomm.receivedOperations.isEmpty())
        val ex = result.exceptionOrNull()
        assertTrue(
            "Expected ProtocolNotEstablished failure",
            ex is HeyCyanCapabilityException &&
                ex.failure is HeyCyanOperationFailure.ProtocolNotEstablished
        )
    }

    // --- CR-07 ---
    /**
     * Wi-Fi transport type returns Unsupported from the command router.
     * Media synchronization routes to Wi-Fi, which is outside the command router's scope.
     */
    @Test
    fun wifiTransportIsMediaOnly() = runTest {
        val result = router.execute(HeyCyanOperation.SynchronizeMedia)

        assertTrue("Expected failure — Wi-Fi is media-only", result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(
            "Expected Unsupported failure for Wi-Fi command routing",
            ex is HeyCyanCapabilityException &&
                ex.failure is HeyCyanOperationFailure.Unsupported
        )
        // BLE and RFCOMM must not have been called at all
        assertTrue(ble.receivedOperations.isEmpty())
        assertTrue(rfcomm.receivedOperations.isEmpty())
    }
}
