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
        val result = coordinator.synchronizeManifest()
        assertTrue(result.isFailure)
        val state = coordinator.state.value
        assertTrue(state is HeyCyanTransferState.Failed)
        assertEquals("Wi-Fi credentials unavailable", (state as HeyCyanTransferState.Failed).message)
    }

    @Test
    fun transferEntersTransferModeBeforeWifi() = runTest {
        // Set Wi-Fi credentials in device state
        stateStore.apply(
            com.halo.drivers.heycyan.protocol.HeyCyanEvent.WiFiCredentials("TestSSID", "TestPass")
        )

        val result = coordinator.synchronizeManifest()
        assertTrue(result.isSuccess)

        // Verify BLE command issued to enter transfer mode (SetMode(TRANSFER))
        val executedCmd = fakeExecutor.executedCommands.firstOrNull()
        assertEquals(HeyCyanCommand.SetMode(HeyCyanMode.TRANSFER), executedCmd)
        assertEquals(WifiConnectionState.CONNECTED, wifiConnection.state.value)
    }

    @Test
    fun wifiFailureStopsTransfer() = runTest {
        stateStore.apply(
            com.halo.drivers.heycyan.protocol.HeyCyanEvent.WiFiCredentials("TestSSID", "TestPass")
        )
        wifiConnection.shouldFailConnect = true

        val result = coordinator.synchronizeManifest()
        assertTrue(result.isFailure)
        val state = coordinator.state.value
        assertTrue(state is HeyCyanTransferState.Failed)
        assertEquals("Wi-Fi connection failed", (state as HeyCyanTransferState.Failed).message)
    }

    @Test
    fun manifestFailureIsReported() = runTest {
        stateStore.apply(
            com.halo.drivers.heycyan.protocol.HeyCyanEvent.WiFiCredentials("TestSSID", "TestPass")
        )
        fakeApi.manifestToReturn = Result.failure(RuntimeException("HTTP 500 Server Error"))

        val result = coordinator.synchronizeManifest()
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
}
