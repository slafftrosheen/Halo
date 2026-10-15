package com.halo.app.ui.screens.discovery

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.halo.app.ui.theme.HaloTheme
import com.halo.app.ui.screens.devices.buddyBotMotorLabel
import com.halo.sdk.host.ConnectionState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class PairingGuidanceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun openingPairingDoesNothingAndPermissionActionOnlyRequestsAccess() {
        val calls = mutableListOf<String>()
        compose.setContent {
            HaloTheme(darkTheme = false, fontScale = 2f) {
                Box(Modifier.width(320.dp).verticalScroll(rememberScrollState())) {
                    PairingGuidance(PairingUiState(bluetoothPrerequisite = BluetoothPrerequisite.PERMISSION_REQUIRED),
                        { calls += "access" }, { calls += "enable" }, { calls += "scan" }, { calls += "stop" }, { calls += "connect" })
                }
            }
        }
        assertTrue(calls.isEmpty())
        compose.onNodeWithText("Allow nearby devices").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(listOf("access"), calls)
    }

    @Test fun connectionInProgressCannotOfferAnotherConnectOrClaimReadiness() {
        val state = mutableStateOf(PairingUiState(selectedAddress = "selected", connectionState = ConnectionState.Connected, attemptedAddress = "selected"))
        var connects = 0
        compose.setContent { HaloTheme(darkTheme = true) { PairingGuidance(state.value, {}, {}, {}, {}, { connects++ }) } }
        compose.onNodeWithText("Connect selected glasses").assertDoesNotExist()
        compose.onNodeWithText("Glasses ready").assertDoesNotExist()
        assertEquals(0, connects)
        state.value = state.value.copy(connectionState = ConnectionState.Ready, completedAddress = "selected")
        compose.onAllNodesWithText("Glasses ready").assertCountEquals(2)
        assertEquals(0, connects)
    }

    @Test fun pairingSuccessRequiresIdentityMatchedDriverAndHostReadiness() {
        val ready = com.halo.drivers.heycyan.protocol.HeyCyanConnectionState.READY
        val connecting = com.halo.drivers.heycyan.protocol.HeyCyanConnectionState.CONNECTING
        assertFalse(pairingTransportReadyForTarget("new", "old", ready, ConnectionState.Ready))
        assertFalse(pairingTransportReadyForTarget("new", "new", connecting, ConnectionState.Ready))
        assertFalse(pairingTransportReadyForTarget("new", "new", ready, ConnectionState.Connected))
        assertTrue(pairingTransportReadyForTarget("AA:bb", "aa:BB", ready, ConnectionState.Ready))
    }

    @Test fun priorHostReadyCannotApproveAnewGlassesSelection() {
        val oldHostReady = PairingUiState(
            connectionState = ConnectionState.Ready,
            selectedAddress = "new-device",
            attemptedAddress = "old-device",
            completedAddress = "old-device"
        )
        assertEquals(PairingNextAction.CONNECT, pairingNextAction(oldHostReady))
        assertEquals(PairingNextAction.SCAN,
            pairingNextAction(oldHostReady.copy(selectedAddress = null)))
        assertEquals(PairingNextAction.WAIT,
            pairingNextAction(oldHostReady.copy(attemptedAddress = "new-device")))
        assertEquals(PairingNextAction.DONE,
            pairingNextAction(oldHostReady.copy(attemptedAddress = "new-device",
                completedAddress = "new-device")))
    }

    @Test fun explicitSelectionAndPrerequisitesDetermineTheNextStep() {
        val selected = PairingUiState(selectedAddress = "selected")
        assertEquals(PairingNextAction.CONNECT, pairingNextAction(selected))
        assertEquals(PairingNextAction.CONNECT, pairingNextAction(selected.copy(connectionState = ConnectionState.Failed("test"))))
        assertEquals(PairingNextAction.TURN_ON, pairingNextAction(selected.copy(bluetoothPrerequisite = BluetoothPrerequisite.BLUETOOTH_OFF)))
        assertEquals(PairingNextAction.SCAN, pairingNextAction(PairingUiState()))
        assertEquals(PairingNextAction.UNAVAILABLE, pairingNextAction(selected.copy(bluetoothPrerequisite = BluetoothPrerequisite.UNSUPPORTED)))
    }

    @Test fun newlyScannedDevicesAndConnectionDetermineThreeVisibleStages() {
        assertEquals(1, pairingStage(PairingUiState()))
        assertEquals(2, pairingStage(PairingUiState(isScanning = true)))
        assertEquals(3, pairingStage(PairingUiState(selectedAddress = "AA:BB:CC:11:22:33")))
        assertEquals(1, pairingStage(PairingUiState(connectionState = ConnectionState.Ready)))
        assertEquals(3, pairingStage(PairingUiState(
            selectedAddress = "AA:BB:CC:11:22:33",
            attemptedAddress = "AA:BB:CC:11:22:33",
            completedAddress = "AA:BB:CC:11:22:33",
            connectionState = ConnectionState.Ready)))
        assertEquals(1, pairingStage(PairingUiState(
            bluetoothPrerequisite = BluetoothPrerequisite.PERMISSION_REQUIRED
        )))
    }

    @Test fun expiredConnectionOffersRetryInsteadOfEndlessSpinner() {
        val expired = PairingUiState(
            selectedAddress = "AA:BB:CC:11:22:33",
            connectionState = ConnectionState.Connecting,
            statusMessage = "Connection did not reach READY · Connecting"
        )
        assertEquals(PairingNextAction.CONNECT, pairingNextAction(expired))
        assertEquals(PairingNextAction.SCAN,
            pairingNextAction(expired.copy(selectedAddress = null, isScanning = true)))
        assertEquals(PairingNextAction.SCAN,
            pairingNextAction(expired.copy(selectedAddress = null, isScanning = false)))
    }

    @Test fun deviceListUsesCoarseSignalAndMaskedAddress() {
        assertEquals(PairingSignalBand.STRONG, pairingSignalBand(-50))
        assertEquals(PairingSignalBand.GOOD, pairingSignalBand(-72))
        assertEquals(PairingSignalBand.WEAK, pairingSignalBand(-99))
        assertEquals("2233", pairingShortAddress("AA:BB:CC:11:22:33"))
        assertEquals("", pairingShortAddress(""))
    }

    @Test fun disconnectedRobotCannotDisplayStaleArmingAsCurrentAuthority() {
        assertEquals("Not confirmed", buddyBotMotorLabel(false, true))
        assertEquals("Not confirmed", buddyBotMotorLabel(false, false))
        assertEquals("Off", buddyBotMotorLabel(true, false))
        assertEquals("Armed", buddyBotMotorLabel(true, true))
    }
}
