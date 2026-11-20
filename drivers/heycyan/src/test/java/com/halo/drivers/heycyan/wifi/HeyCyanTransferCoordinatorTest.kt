package com.halo.drivers.heycyan.wifi

import com.halo.drivers.heycyan.capability.HeyCyanCapabilityControllerTest
import com.halo.drivers.heycyan.capability.HeyCyanCapabilityController
import com.halo.drivers.heycyan.media.DefaultHeyCyanMediaConfigParser
import com.halo.drivers.heycyan.media.HeyCyanMedia
import com.halo.drivers.heycyan.media.HeyCyanMediaManifest
import com.halo.drivers.heycyan.media.MediaDownloadProgress
import com.halo.drivers.heycyan.media.MediaDownloadResult
import com.halo.drivers.heycyan.media.MediaIntegrity
import com.halo.drivers.heycyan.media.MediaLocation
import com.halo.drivers.heycyan.protocol.HeyCyanCommand
import com.halo.drivers.heycyan.protocol.HeyCyanDeviceState
import com.halo.drivers.heycyan.protocol.HeyCyanDeviceStateStore
import com.halo.drivers.heycyan.protocol.HeyCyanMode
import com.halo.drivers.heycyan.protocol.WiFiCredentials
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetAddress

class HeyCyanTransferCoordinatorTest {

    private lateinit var fakeExecutor: HeyCyanCapabilityControllerTest.FakeCommandExecutor
    private lateinit var stateStore: HeyCyanDeviceStateStore
    private lateinit var controller: HeyCyanCapabilityController
    private lateinit var wifiConnection: FakeHeyCyanWifiConnection
    private lateinit var credentialProvider: DeviceStateWifiCredentialProvider
    private lateinit var coordinator: HeyCyanTransferCoordinator
    private lateinit var fakeApi: FakeHeyCyanMediaApi

    class FakeHeyCyanMediaApi : HeyCyanMediaApi {
        var manifestToReturn: Result<HeyCyanMediaManifest> = Result.success(
            HeyCyanMediaManifest(
                deviceId = "test-device",
                generatedAt = 1000L,
                items = listOf(
                    HeyCyanMedia.Photo("photo1.jpg", "photo1.jpg", 100L, 1000L, null, null)
                )
            )
        )

        override suspend fun getMediaConfig(): Result<HeyCyanMediaManifest> = manifestToReturn

        override suspend fun download(
            media: HeyCyanMedia,
            destinationPath: String,
            progress: (MediaDownloadProgress) -> Unit
        ): Result<MediaDownloadResult> {
            progress(MediaDownloadProgress(100L, 100L))
            return Result.success(
                MediaDownloadResult(
                    media = media,
                    bytesWritten = 100L,
                    destination = MediaLocation(destinationPath),
                    integrity = MediaIntegrity(100L, 100L, "fakehash")
                )
            )
        }
    }

    @Before
    fun setUp() {
        fakeExecutor = HeyCyanCapabilityControllerTest.FakeCommandExecutor()
        stateStore = HeyCyanDeviceStateStore()
        controller = HeyCyanCapabilityController(fakeExecutor, stateStore)
        wifiConnection = FakeHeyCyanWifiConnection()
        wifiConnection.addressToReturn = InetAddress.getByName("127.0.0.1")
        credentialProvider = DeviceStateWifiCredentialProvider(stateStore.state)
        fakeApi = FakeHeyCyanMediaApi()

        val apiFactory = HeyCyanMediaApiFactory { fakeApi }
        coordinator = HeyCyanTransferCoordinator(
            commandController = controller,
            credentialsProvider = credentialProvider,
            wifiConnection = wifiConnection,
            mediaApiFactory = apiFactory
        )
    }

    @Test
    fun transferRequiresCredentials() = runTest {
        // State has no Wi-Fi credentials set
        val result = coordinator.synchronizeManifest("device")
        assertTrue(result.isFailure)
        val state = coordinator.state.value
        assertTrue(state is HeyCyanTransferState.Failed)
    }

    @Test fun freshZeroInventoryAvoidsWifiAndHttpAndReleasesOwnership() = runTest {
        val arbiter = com.halo.drivers.heycyan.arbiter.HeyCyanResourceArbiter()
        var queries = 0
        val guarded = HeyCyanTransferCoordinator(controller, credentialProvider,
            object : HeyCyanWifiConnection by wifiConnection {
                override suspend fun prepare(deviceId: String) { error("Empty inventory must not start Wi-Fi") }
            }, HeyCyanMediaApiFactory { error("Empty inventory must not open HTTP") }, arbiter,
            pendingInventory = { queries++; com.halo.drivers.heycyan.protocol.MediaCounts(0, 0, 0, 4) })
        val manifest = guarded.synchronizeManifest("chosen").getOrThrow()
        assertTrue(manifest.items.isEmpty())
        assertEquals("chosen", manifest.deviceId)
        assertEquals(1, queries)
        assertTrue(guarded.state.value is HeyCyanTransferState.Ready)
        guarded.closeSession().getOrThrow()
        assertTrue(arbiter.activeLeases().isEmpty())
        assertTrue(fakeExecutor.executedCommands.isEmpty())
    }

    @Test fun unknownPositiveOrWrongTypeInventoryStillUsesHttp() = runTest {
        stateStore.apply(com.halo.drivers.heycyan.protocol.HeyCyanEvent.WiFiHotspotReady("ssid", "secret"))
        for (counts in listOf(null, com.halo.drivers.heycyan.protocol.MediaCounts(0, 1, 0, 4),
                com.halo.drivers.heycyan.protocol.MediaCounts(0, 0, 0, 1))) {
            val guarded = HeyCyanTransferCoordinator(controller, credentialProvider, wifiConnection,
                HeyCyanMediaApiFactory { fakeApi }, pendingInventory = { counts })
            assertEquals(1, guarded.synchronizeManifest("chosen").getOrThrow().items.size)
            guarded.closeSession().getOrThrow()
        }
    }

    @Test fun inventoryFromChangedSelectionCannotReturnEmpty() = runTest {
        var current = true
        val guarded = HeyCyanTransferCoordinator(controller, credentialProvider, wifiConnection,
            HeyCyanMediaApiFactory { fakeApi }, deviceIsCurrent = { current },
            pendingInventory = { current = false; com.halo.drivers.heycyan.protocol.MediaCounts(0, 0, 0, 4) })
        assertTrue(guarded.synchronizeManifest("chosen").isFailure)
        assertTrue(fakeExecutor.executedCommands.isEmpty())
    }

    @Test fun directTransferDiscoversBeforeBleAndDoesNotWaitForHotspotCredentials() = runTest {
        var prepared = false
        val direct = object : HeyCyanWifiConnection by wifiConnection {
            override val requiresCredentials = false
            override suspend fun prepare(deviceId: String) {
                assertTrue("Discovery must precede the transfer-mode command", fakeExecutor.executedCommands.isEmpty())
                prepared = true
            }
            override suspend fun connectDirect(deviceId: String): Result<Unit> {
                assertTrue(prepared)
                assertEquals(HeyCyanCommand.EnterWifiTransferMode, fakeExecutor.executedCommands.first())
                return Result.success(Unit)
            }
        }
        val directCoordinator = HeyCyanTransferCoordinator(controller, credentialProvider, direct,
            HeyCyanMediaApiFactory { fakeApi })
        val result = directCoordinator.synchronizeManifest("selected-device")
        assertTrue("Wi-Fi Direct must reach the manifest without a hotspot credential event", result.isSuccess)
        assertTrue(directCoordinator.state.value is HeyCyanTransferState.Ready)
        directCoordinator.closeSession()
    }

    @Test
    fun transferEntersTransferModeBeforeWifi() = runTest {
        // Set Wi-Fi credentials in device state
        stateStore.apply(
            com.halo.drivers.heycyan.protocol.HeyCyanEvent.WiFiHotspotReady("TestSSID", "TestPass")
        )

        val result = coordinator.synchronizeManifest("device")
        assertTrue(result.isSuccess)

        // Verify BLE command issued to enter transfer mode (SetMode(TRANSFER))
        val executedCmd = fakeExecutor.executedCommands.firstOrNull()
        assertEquals(HeyCyanCommand.EnterWifiTransferMode, executedCmd)
        assertEquals(WifiConnectionState.CONNECTED, wifiConnection.state.value)
    }

    @Test
    fun wifiFailureStopsTransfer() = runTest {
        stateStore.apply(
            com.halo.drivers.heycyan.protocol.HeyCyanEvent.WiFiHotspotReady("TestSSID", "TestPass")
        )
        wifiConnection.shouldFailConnect = true

        val result = coordinator.synchronizeManifest("device")
        assertTrue(result.isFailure)
        val state = coordinator.state.value
        assertTrue(state is HeyCyanTransferState.Failed)
        assertEquals("Wi-Fi connection failed", (state as HeyCyanTransferState.Failed).message)
    }

    @Test
    fun manifestFailureIsReported() = runTest {
        stateStore.apply(
            com.halo.drivers.heycyan.protocol.HeyCyanEvent.WiFiHotspotReady("TestSSID", "TestPass")
        )
        fakeApi.manifestToReturn = Result.failure(RuntimeException("HTTP 500 Server Error"))

        val result = coordinator.synchronizeManifest("device")
        assertTrue(result.isFailure)
        val state = coordinator.state.value
        assertTrue(state is HeyCyanTransferState.Failed)
        assertEquals("Failed to fetch media manifest", (state as HeyCyanTransferState.Failed).message)
    }

    @Test
    fun credentialsNeverAppearInLogs() {
        val creds = HeyCyanWifiCredentials("SecretSSID", "SuperSecretPassword123")
        val str = creds.toString()
        assertFalse(str.contains("SuperSecretPassword123"))
        assertTrue(str.contains("<redacted>"))
    }

    @Test
    fun arbiterConflictFailsTransfer() = runTest {
        val arbiter = com.halo.drivers.heycyan.arbiter.HeyCyanResourceArbiter()
        // Hold conflicting resource: BLE_BULK_TRANSFER
        val lease = arbiter.tryAcquire("ble", setOf(com.halo.drivers.heycyan.arbiter.HeyCyanResource.BLE_BULK_TRANSFER))
        org.junit.Assert.assertNotNull(lease)

        val coordinatorWithArbiter = HeyCyanTransferCoordinator(
            commandController = controller,
            credentialsProvider = credentialProvider,
            wifiConnection = wifiConnection,
            mediaApiFactory = HeyCyanMediaApiFactory { fakeApi },
            resourceArbiter = arbiter,
        )

        val result = coordinatorWithArbiter.synchronizeManifest("device")
        assertTrue(result.isFailure)
        assertTrue(coordinatorWithArbiter.state.value is HeyCyanTransferState.Failed)
        lease!!.release()
    }

    @Test
    fun closeWithoutOwnershipDoesNotSendExitToAnotherOperation() = runTest {
        val closeResult = coordinator.closeSession()
        assertTrue(closeResult.isSuccess)
        assertTrue(fakeExecutor.executedCommands.isEmpty())
        assertEquals(HeyCyanTransferState.Idle, coordinator.state.value)
    }

    @Test fun successfulSessionReleasesItsWifiConnectionOnClose() = runTest {
        stateStore.apply(com.halo.drivers.heycyan.protocol.HeyCyanEvent.WiFiHotspotReady("TestSSID", "TestPass"))
        coordinator.synchronizeManifest("device").getOrThrow()
        coordinator.closeSession().getOrThrow()
        assertEquals(WifiConnectionState.DISCONNECTED, wifiConnection.state.value)
    }

    @Test
    fun downloadPreservesOriginalRecordingBytes() = runTest {
        stateStore.apply(
            com.halo.drivers.heycyan.protocol.HeyCyanEvent.WiFiHotspotReady("TestSSID", "TestPass")
        )
        coordinator.synchronizeManifest("device")

        // Create a temporary file containing 200 raw bytes (5x 40-byte raw Opus frames)
        val tempFile = java.io.File.createTempFile("test_audio", ".opus")
        try {
            tempFile.writeBytes(ByteArray(200) { 0x42 })
            val audioMedia = HeyCyanMedia.Audio(
                id = "audio1.opus",
                filename = "audio1.opus",
                sizeBytes = 200L,
                createdAt = 1000L,
                durationMs = 5000L,
            )

            val downloadResult = coordinator.download(audioMedia, tempFile.absolutePath)
            assertTrue(downloadResult.isSuccess)

            org.junit.Assert.assertArrayEquals(ByteArray(200) { 0x42 }, tempFile.readBytes())
        } finally {
            tempFile.delete()
        }
    }
    @Test fun losingForegroundAccessStillClosesTheExactOwnedConnection() = runTest {
        stateStore.apply(com.halo.drivers.heycyan.protocol.HeyCyanEvent.WiFiHotspotReady("TestSSID", "TestPass"))
        var foreground = true
        var sameConnection = true
        val guarded = HeyCyanTransferCoordinator(controller, credentialProvider, wifiConnection,
            HeyCyanMediaApiFactory { fakeApi }, deviceIsCurrent = { foreground && sameConnection },
            cleanupDeviceIsCurrent = { sameConnection })
        guarded.synchronizeManifest("device").getOrThrow()
        foreground = false
        guarded.closeSession().getOrThrow()
        assertEquals(1, fakeExecutor.executedCommands.count { it == HeyCyanCommand.ExitWifiTransferMode })
        foreground = true
        guarded.synchronizeManifest("device").getOrThrow()
        sameConnection = false
        guarded.closeSession().getOrThrow()
        assertEquals(1, fakeExecutor.executedCommands.count { it == HeyCyanCommand.ExitWifiTransferMode })
    }

    @Test fun ownedSessionClosesOnceAndCannotExitANewSelectedDevice() = runTest {
        stateStore.apply(com.halo.drivers.heycyan.protocol.HeyCyanEvent.WiFiHotspotReady("TestSSID", "TestPass"))
        coordinator.synchronizeManifest("device").getOrThrow()
        coordinator.closeSession().getOrThrow()
        coordinator.closeSession().getOrThrow()
        assertEquals(1, fakeExecutor.executedCommands.count { it == HeyCyanCommand.ExitWifiTransferMode })
        var current = true
        val guarded = HeyCyanTransferCoordinator(controller, credentialProvider, wifiConnection,
            HeyCyanMediaApiFactory { fakeApi }, deviceIsCurrent = { current })
        guarded.synchronizeManifest("device").getOrThrow()
        current = false
        guarded.closeSession().getOrThrow()
        assertEquals(1, fakeExecutor.executedCommands.count { it == HeyCyanCommand.ExitWifiTransferMode })
    }

}
