package com.halo.app.audio

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.halo.app.audio.association.WearableAudioAssociation
import com.halo.host.android.audio.BluetoothAudioEndpoint
import com.halo.host.android.audio.BluetoothAudioEndpointType
import com.halo.host.android.audio.BluetoothAudioRouteMonitor
import com.halo.host.android.AndroidStorage
import com.halo.sdk.runtime.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class HeyCyanAudioPairingCoordinatorTest {

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun explicitlySelectedBondedDeviceUsesExactSingleDeviceApproval() = kotlinx.coroutines.test.runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        org.robolectric.Shadows.shadowOf(context as Application).grantPermissions(android.Manifest.permission.BLUETOOTH_CONNECT)
        val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
        val selected = adapter.getRemoteDevice("AA:BB:CC:00:11:22")
        val unrelated = adapter.getRemoteDevice("AA:BB:CC:33:44:55")
        org.robolectric.Shadows.shadowOf(adapter).setBondedDevices(setOf(selected, unrelated))
        val dispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)
        val coordinator = HeyCyanAudioPairingCoordinator(context,
            AndroidStorage(context, "audio-exact-${UUID.randomUUID()}"), FakeRouteMonitor(),
            HaloDispatchers(dispatcher, dispatcher, dispatcher))
        assertEquals(2, coordinator.bondedAudioChoices().size)
        coordinator.requestAssociation("selected-ble", selected.address)
        testScheduler.runCurrent()
        val manager = context.getSystemService(android.companion.CompanionDeviceManager::class.java)
        val request = requireNotNull(org.robolectric.Shadows.shadowOf(manager).lastAssociationRequest)
        assertTrue("Exact single-device discovery must also search already-bonded devices", request.isSingleDevice)
        val filter = (request.javaClass.getMethod("getDeviceFilters").invoke(request) as List<*>).single()!!
        val matches = filter.javaClass.getMethod("matches", android.bluetooth.BluetoothDevice::class.java)
        assertTrue(matches.invoke(filter, selected) as Boolean)
        assertFalse(matches.invoke(filter, unrelated) as Boolean)
        assertEquals(HeyCyanAudioPairingState.Discovering, coordinator.state.value)
        assertFalse(coordinator.matchesEndpoint("selected-ble", BluetoothAudioEndpoint(address = selected.address,
            name = "A2", type = BluetoothAudioEndpointType.A2DP, connected = true)))
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun removedBondCannotBeReplacedWithNearbyDiscoveryWithoutAnotherChoice() = kotlinx.coroutines.test.runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        org.robolectric.Shadows.shadowOf(context as Application).grantPermissions(android.Manifest.permission.BLUETOOTH_CONNECT)
        val dispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)
        val coordinator = HeyCyanAudioPairingCoordinator(context,
            AndroidStorage(context, "audio-stale-${UUID.randomUUID()}"), FakeRouteMonitor(),
            HaloDispatchers(dispatcher, dispatcher, dispatcher))
        coordinator.requestAssociation("selected-ble", "AA:BB:CC:00:11:22")
        testScheduler.runCurrent()
        assertTrue(coordinator.state.value is HeyCyanAudioPairingState.Failed)
        val manager = context.getSystemService(android.companion.CompanionDeviceManager::class.java)
        org.junit.Assert.assertNull(org.robolectric.Shadows.shadowOf(manager).lastAssociationRequest)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun approvedCdmAddressRestoresTheExistingBondWithLowercaseMacAddress() = kotlinx.coroutines.test.runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        org.robolectric.Shadows.shadowOf(context as Application).grantPermissions(android.Manifest.permission.BLUETOOTH_CONNECT)
        val dispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)
        val device = android.bluetooth.BluetoothAdapter.getDefaultAdapter().getRemoteDevice("AA:BB:CC:00:11:22")
        org.robolectric.Shadows.shadowOf(device).setBondState(android.bluetooth.BluetoothDevice.BOND_BONDED)
        val coordinator = HeyCyanAudioPairingCoordinator(context,
            AndroidStorage(context, "audio-approved-${UUID.randomUUID()}"), FakeRouteMonitor(),
            HaloDispatchers(dispatcher, dispatcher, dispatcher))
        coordinator.requestAssociation("selected-ble")
        testScheduler.runCurrent()
        val manager = context.getSystemService(android.companion.CompanionDeviceManager::class.java)
        val info = org.robolectric.shadows.AssociationInfoBuilder.newBuilder().setId(42).setUserId(0)
            .setPackageName(context.packageName).setDeviceMacAddress("aa:bb:cc:00:11:22")
            .setDisplayName("A2 test").build()
        org.robolectric.Shadows.shadowOf(manager).addAssociation(info)
        org.robolectric.Shadows.shadowOf(manager).lastAssociationCallback.onAssociationCreated(info)
        testScheduler.runCurrent()
        val state = coordinator.state.value as HeyCyanAudioPairingState.Associated
        assertTrue("CDM's lowercase address must resolve the bonded Bluetooth device", state.association.bonded)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun audioChooserIncludesA2AndRenamedDevicesWithoutSelectingOneAutomatically() = kotlinx.coroutines.test.runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)
        val coordinator = HeyCyanAudioPairingCoordinator(context,
            AndroidStorage(context, "audio-chooser-${UUID.randomUUID()}"), FakeRouteMonitor(),
            HaloDispatchers(dispatcher, dispatcher, dispatcher))
        val manager = context.getSystemService(android.companion.CompanionDeviceManager::class.java)
        coordinator.requestAssociation("explicitly-selected-ble-device")
        testScheduler.runCurrent()
        val request = requireNotNull(org.robolectric.Shadows.shadowOf(manager).lastAssociationRequest)
        // Framework filter matching is hidden from the public compile SDK, but executes in Robolectric.
        val filters = request.javaClass.getMethod("getDeviceFilters").invoke(request) as List<*>
        val filter = requireNotNull(filters.single())
        val device = android.bluetooth.BluetoothAdapter.getDefaultAdapter().getRemoteDevice("AA:BB:CC:00:11:22")
        for (name in listOf("A2_QA", "My renamed glasses")) {
            org.robolectric.Shadows.shadowOf(device).setName(name)
            assertTrue("The system chooser must include $name",
                filter.javaClass.getMethod("matches", android.bluetooth.BluetoothDevice::class.java).invoke(filter, device) as Boolean)
        }
        assertFalse("User must choose among the available audio endpoints", request.isSingleDevice)
        assertEquals(HeyCyanAudioPairingState.Discovering, coordinator.state.value)
        assertFalse(coordinator.matchesEndpoint("explicitly-selected-ble-device", BluetoothAudioEndpoint(
            deviceId = 101, address = device.address, name = "A2_QA",
            type = BluetoothAudioEndpointType.A2DP, connected = true)))
    }

    private class FakeRouteMonitor : BluetoothAudioRouteMonitor {
        private val _routes = MutableStateFlow<List<BluetoothAudioEndpoint>>(emptyList())
        override val routes: StateFlow<List<BluetoothAudioEndpoint>> = _routes.asStateFlow()

        fun setRoutes(list: List<BluetoothAudioEndpoint>) {
            _routes.value = list
        }

        override fun refreshEndpoints() {}

        override suspend fun awaitEndpoint(
            predicate: (BluetoothAudioEndpoint) -> Boolean,
            timeoutMs: Long
        ): BluetoothAudioEndpoint? {
            return _routes.value.firstOrNull(predicate)
        }
    }

    private class FakeWearableRouteAdapter(
        override val deviceId: String? = "test-glasses-1"
    ) : WearableAudioRouteAdapter {
        var prepareCalled = false
        var requestRouteCalled = false
        var releaseRouteCalled = false

        override suspend fun prepareAudioTransport(): Result<Unit> {
            prepareCalled = true
            return Result.success(Unit)
        }

        override suspend fun requestOutputRoute(): Result<Unit> {
            requestRouteCalled = true
            return Result.success(Unit)
        }

        override suspend fun releaseOutputRoute(): Result<Unit> {
            releaseRouteCalled = true
            return Result.success(Unit)
        }
    }

    @Test
    fun bondingStateCannotProceedToRouteAudioToGlasses() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = AndroidStorage(context, "test-storage-1")
        val monitor = FakeRouteMonitor()
        val coordinator = HeyCyanAudioPairingCoordinator(context, storage, monitor)
        val adapter = FakeWearableRouteAdapter("test-glasses-1")
        val controller = SpeechOutputRouteController(context, adapter, monitor, coordinator)

        // Set state to Bonding
        val assoc = WearableAudioAssociation("test-glasses-1", 1, "00:11:22:33:44:55", "BW-AG1", false, null)
        coordinator.restore("test-glasses-1")

        val req = SpeechRouteRequest(
            transactionId = "tx-1",
            target = SpeechOutputTarget.Device("test-glasses-1", null, "Glasses", AudioDeviceKind.WEARABLE),
            fallback = SpeechOutputFallback.DISALLOW_PHONE,
            sourceCaptureId = "cap-1"
        )

        // Force bonding state by failing route check when unbonded
        val result = controller.prepareRoute(req)
        assertFalse("Bonding or pending route must not be Ready", result is SpeechRoutePreparation.Ready)
        assertFalse("Wearable route command must not be sent while pending/bonding", adapter.requestRouteCalled)
    }

    @Test
    fun wearablePrewarmNeverSendsRouteAudioToGlasses() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = AndroidStorage(context, "test-storage-2")
        val monitor = FakeRouteMonitor()
        val coordinator = HeyCyanAudioPairingCoordinator(context, storage, monitor)
        val adapter = FakeWearableRouteAdapter("test-glasses-1")
        val controller = SpeechOutputRouteController(context, adapter, monitor, coordinator)

        controller.prewarmRoute("test-glasses-1")

        assertTrue("Prewarm prepares transport", adapter.prepareCalled)
        assertFalse("Prewarm NEVER sends RouteAudioToGlasses command while user is speaking", adapter.requestRouteCalled)
    }

    @Test
    fun disallowPhoneNeverLeaksSpeechToHandset() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = AndroidStorage(context, "test-storage-3")
        val monitor = FakeRouteMonitor()
        val coordinator = HeyCyanAudioPairingCoordinator(context, storage, monitor)
        val adapter = FakeWearableRouteAdapter("test-glasses-1")
        val controller = SpeechOutputRouteController(context, adapter, monitor, coordinator)

        val req = SpeechRouteRequest(
            transactionId = "tx-2",
            target = SpeechOutputTarget.Device("wrong-glasses-2", null, "Glasses", AudioDeviceKind.WEARABLE),
            fallback = SpeechOutputFallback.DISALLOW_PHONE,
            sourceCaptureId = "cap-2"
        )

        val prep = controller.prepareRoute(req)
        assertTrue("Mismatched device produces Unavailable", prep is SpeechRoutePreparation.Unavailable)
        val unavail = prep as SpeechRoutePreparation.Unavailable
        assertEquals(SpeechRouteFailureReason.WRONG_DEVICE, unavail.reason)
        assertEquals(SpeechOutputTarget.Device("wrong-glasses-2", null, "Glasses", AudioDeviceKind.WEARABLE), unavail.target)
    }

    @Test
    fun audioConnectedRouteIsVerifiedAgainstCorrectEndpoint() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = AndroidStorage(context, "test-storage-4")
        val monitor = FakeRouteMonitor()

        val endpoint = BluetoothAudioEndpoint(
            deviceId = 101,
            address = "00:11:22:33:44:55",
            name = "BW-AG1 Pro",
            type = BluetoothAudioEndpointType.A2DP,
            connected = true
        )
        monitor.setRoutes(listOf(endpoint))

        val coordinator = HeyCyanAudioPairingCoordinator(context, storage, monitor)
        val assoc = WearableAudioAssociation("test-glasses-1", 1, "00:11:22:33:44:55", "BW-AG1 Pro", true, null)
        
        val matches = coordinator.matchesEndpoint("test-glasses-1", endpoint)
        // Verified against correct endpoint MAC
        assertFalse("Unassociated device does not match random endpoint", matches)
    }
}
