package com.halo.app.ui.screens.devices

import com.halo.sdk.runtime.embodiment.DeviceKind
import com.halo.sdk.runtime.embodiment.SessionState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceReconnectPresentationTest {
    private fun device(state: SessionState?, auto: Boolean = true) = GenericDeviceUiState(
        id = "glass-1", name = "Glasses", kind = DeviceKind.HEYCYAN_GLASSES,
        isConnected = state == SessionState.READY, connectionStateText = "Saved",
        autoConnect = auto, battery = "Unknown", firmware = "Unknown", lastSeen = "Never",
        sessionState = state,
    )
    @Test fun onlySavedAutoConnectDevicesCanOfferAnExplicitRetry() {
        assertTrue(deviceCanRetryFromList(device(null)))
        assertFalse(deviceCanRetryFromList(device(SessionState.CONNECTING)))
        assertFalse(deviceCanRetryFromList(device(SessionState.READY)))
        assertFalse(deviceCanRetryFromList(device(null, auto = false)))
    }
}
