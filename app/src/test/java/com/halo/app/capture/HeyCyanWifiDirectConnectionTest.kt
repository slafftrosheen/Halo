package com.halo.app.capture

import android.Manifest
import android.app.Application
import android.content.Context
import android.net.wifi.p2p.*
import androidx.test.core.app.ApplicationProvider
import com.halo.drivers.heycyan.protocol.HeyCyanEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.*
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowWifiP2pManager
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class,
    shadows = [DirectManagerShadow::class, DirectChannelShadow::class])
@OptIn(ExperimentalCoroutinesApi::class)
class HeyCyanWifiDirectConnectionTest {
    @Test fun disabledWifiStopsBeforeDiscoveryOrADeviceTransferCommand() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val context = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(context).grantPermissions(Manifest.permission.NEARBY_WIFI_DEVICES)
        context.getSystemService(android.net.wifi.WifiManager::class.java).isWifiEnabled = false
        val manager = Shadow.extract<DirectManagerShadow>(context.getSystemService(WifiP2pManager::class.java))
        val connection = HeyCyanWifiDirectConnection(context, MutableSharedFlow<HeyCyanEvent>(), { "selected" }, backgroundScope)
        try {
            assertTrue(runCatching { connection.prepare("selected") }.exceptionOrNull() is HeyCyanWifiDisabledException)
            assertFalse(manager.negotiating)
            assertNull(connection.peerSelection.value.deviceId)
        } finally { connection.disconnect(); Dispatchers.resetMain() }
    }
    @Test fun timedOutNegotiationIsCancelledBeforeAnotherAttempt() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val context = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(context).grantPermissions(Manifest.permission.NEARBY_WIFI_DEVICES)
        context.getSystemService(android.net.wifi.WifiManager::class.java).isWifiEnabled = true
        val manager = Shadow.extract<DirectManagerShadow>(context.getSystemService(WifiP2pManager::class.java))
        val connection = HeyCyanWifiDirectConnection(context, MutableSharedFlow<HeyCyanEvent>(), { "selected" }, backgroundScope)
        try {
            connection.prepare("selected")
            connection.selectPeer("selected", manager.peer.deviceAddress)
            val attempt = async { connection.connectDirect("selected") }
            runCurrent()
            assertTrue(manager.negotiating)
            advanceTimeBy(35_001)
            runCurrent()
            assertTrue(attempt.await().isFailure)
            assertFalse("A timed-out Android negotiation must be cancelled before retry, otherwise Android returns BUSY", manager.negotiating)
            assertEquals(1, manager.cancelCount)
            assertEquals(0, manager.removeCount)
        } finally { connection.disconnect(); Dispatchers.resetMain() }
    }

    @Test fun rejectedRequestDoesNotCancelAnotherAppsNegotiation() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val context = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(context).grantPermissions(Manifest.permission.NEARBY_WIFI_DEVICES)
        context.getSystemService(android.net.wifi.WifiManager::class.java).isWifiEnabled = true
        val manager = Shadow.extract<DirectManagerShadow>(context.getSystemService(WifiP2pManager::class.java))
        manager.negotiating = true
        val connection = HeyCyanWifiDirectConnection(context, MutableSharedFlow<HeyCyanEvent>(), { "selected" }, backgroundScope)
        try {
            connection.prepare("selected")
            connection.selectPeer("selected", manager.peer.deviceAddress)
            assertTrue(connection.connectDirect("selected").isFailure)
            connection.disconnect()
            assertTrue(manager.negotiating)
            assertEquals(0, manager.cancelCount)
            assertEquals(0, manager.removeCount)
        } finally { connection.disconnect(); Dispatchers.resetMain() }
    }

    @Test fun preexistingGroupIsRejectedAndNeverRemoved() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val context = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(context).grantPermissions(Manifest.permission.NEARBY_WIFI_DEVICES)
        context.getSystemService(android.net.wifi.WifiManager::class.java).isWifiEnabled = true
        val manager = Shadow.extract<DirectManagerShadow>(context.getSystemService(WifiP2pManager::class.java))
        manager.existingGroup = WifiP2pGroup()
        val connection = HeyCyanWifiDirectConnection(context, MutableSharedFlow<HeyCyanEvent>(), { "selected" }, backgroundScope)
        try {
            val result = runCatching { connection.prepare("selected") }
            assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("Another Wi-Fi Direct"))
            connection.disconnect()
            assertEquals(0, manager.removeCount)
            assertEquals(0, manager.cancelCount)
            assertNotNull(manager.existingGroup)
        } finally { connection.disconnect(); Dispatchers.resetMain() }
    }
}

/** Android callback boundary: dispatch can succeed while group formation never completes. */
@Implements(WifiP2pManager::class)
class DirectManagerShadow : ShadowWifiP2pManager() {
    val peer = WifiP2pDevice().apply { deviceAddress = "aa:bb:cc:11:22:33"; deviceName = "Selected test peer" }
    var negotiating = false
    var cancelCount = 0
    var removeCount = 0
    var existingGroup: WifiP2pGroup? = null

    @Implementation protected fun discoverPeers(channel: WifiP2pManager.Channel, listener: WifiP2pManager.ActionListener) = listener.onSuccess()
    @Implementation protected fun stopPeerDiscovery(channel: WifiP2pManager.Channel, listener: WifiP2pManager.ActionListener) = listener.onSuccess()
    @Implementation protected fun requestPeers(channel: WifiP2pManager.Channel, listener: WifiP2pManager.PeerListListener) {
        val peers = WifiP2pDeviceList()
        ReflectionHelpers.callInstanceMethod<Any>(peers, "update",
            ReflectionHelpers.ClassParameter.from(WifiP2pDevice::class.java, peer))
        listener.onPeersAvailable(peers)
    }
    @Implementation override fun requestGroupInfo(channel: WifiP2pManager.Channel, listener: WifiP2pManager.GroupInfoListener) =
        listener.onGroupInfoAvailable(existingGroup)
    @Implementation protected fun connect(channel: WifiP2pManager.Channel, config: WifiP2pConfig, listener: WifiP2pManager.ActionListener) {
        if (negotiating) listener.onFailure(WifiP2pManager.BUSY)
        else { negotiating = true; listener.onSuccess() }
    }
    @Implementation protected fun cancelConnect(channel: WifiP2pManager.Channel, listener: WifiP2pManager.ActionListener) {
        cancelCount++; negotiating = false; listener.onSuccess()
    }
    @Implementation override fun removeGroup(channel: WifiP2pManager.Channel, listener: WifiP2pManager.ActionListener) {
        removeCount++; existingGroup = null; listener.onSuccess()
    }
}

@Implements(WifiP2pManager.Channel::class)
class DirectChannelShadow {
    @Implementation fun close() = Unit
}
