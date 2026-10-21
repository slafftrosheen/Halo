package com.halo.app.ui.screens.devices

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.halo.app.audio.HeyCyanAudioPairingState
import com.halo.app.capture.WearableCaptureStatus
import com.halo.app.ui.screens.settings.BrandBook3PowerManager
import com.halo.app.ui.theme.HostlessHaloTheme
import com.halo.sdk.runtime.embodiment.DeviceKind
import com.halo.sdk.runtime.embodiment.SessionState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class, qualifiers = "en-w360dp-h780dp",
    shadows = [BrandBook3PowerManager::class])
class GlassesBrandBookFlowTest {
    @get:Rule val compose = createComposeRule()

    private fun device(state: SessionState) = GenericDeviceUiState(
        "glasses", "My glasses", DeviceKind.HEYCYAN_GLASSES,
        state == SessionState.READY, "Ready", false, "Unknown", firmware = "Unknown", lastSeen = "Never",
        sessionState = state, hasLiveGlasses = state == SessionState.READY)

    private fun show(state: SessionState, busy: Boolean = false, scale: Float = 1f,
        capture: () -> Unit = {}, photo: () -> Unit = {}, forget: () -> Unit = {}) {
        compose.setContent { HostlessHaloTheme(fontScale = scale) {
            Column(Modifier.width(360.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
                GlassesControlsContent(device(state), HeyCyanAudioPairingState.NotAssociated,
                    WearableCaptureStatus(busy = busy, message = "raw endpoint token"),
                    onConnect = {}, onPhoto = photo, onVideo = {}, onAudio = {},
                    onReviewPhoto = capture, onCopy = {}, onRefresh = {},
                    onConnectAudio = {}, onAutoConnect = {}, onForget = forget)
            }
        } }
    }

    @Test fun transportConnectionCannotEnableCaptureEvenWithReadyDisplayText() {
        show(SessionState.CONNECTED, photo = { error("Premature capture") })
        compose.onNodeWithText("Connected · getting ready").assertExists()
        compose.onNodeWithText("Take photo").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Review a photo with halo").performScrollTo().assertIsNotEnabled()
    }

    @Test fun photoReviewUsesCaptureOwnerAndNeverTriggersNormalPhoto() {
        var reviews = 0; var photos = 0
        show(SessionState.READY, capture = { reviews++ }, photo = { photos++ })
        compose.onNodeWithText("Review a photo with halo").performScrollTo().performClick()
        assertEquals(1, reviews); assertEquals(0, photos)
        compose.onNodeWithText("raw endpoint token").assertDoesNotExist()
        compose.onNodeWithText("Available").assertDoesNotExist()
    }

    @Test fun copyingBlocksDuplicateCaptureAndCopyRequests() {
        show(SessionState.READY, busy = true)
        compose.onNodeWithText("Take photo").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Review a photo with halo").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Copying…").performScrollTo().assertIsNotEnabled()
    }

    @Test fun largeTextForgetRequiresConfirmationAndCancelPreservesDevice() {
        var forgotten = 0
        show(SessionState.READY, scale = 2f, forget = { forgotten++ })
        compose.onNodeWithText("Forget these glasses…").performScrollTo().performClick()
        assertEquals(0, forgotten)
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(0, forgotten)
        compose.onNodeWithText("Forget these glasses…").performScrollTo().performClick()
        compose.onNodeWithText("Forget device", useUnmergedTree = true).performClick()
        assertEquals(1, forgotten)
    }

    @Test fun selectedDeviceMustOwnReadyDriverBeforeCapture() {
        assertFalse(glassesOwnsReadyCapture("saved", "other", SessionState.READY))
        assertFalse(glassesOwnsReadyCapture("saved", "saved", SessionState.CONNECTED))
        assertFalse(glassesOwnsReadyCapture("saved", null, SessionState.READY))
        assertTrue(glassesOwnsReadyCapture("saved", "saved", SessionState.READY))
    }

    @Test fun audioFailureCannotExposeProviderOrBluetoothDetails() {
        assertEquals("Sound could not connect. Try connecting audio again.",
            glassesAudioLabel("glasses", HeyCyanAudioPairingState.Failed("private endpoint token")))
    }

    @Test fun associatedAudioForAnotherPairIsNeverClaimedAsConnected() {
        val association = com.halo.app.audio.association.WearableAudioAssociation(
            "another-pair", null, "private address", "private device", true, null)
        assertEquals("Sound is not connected for these glasses.",
            glassesAudioLabel("glasses", HeyCyanAudioPairingState.Associated(association)))
    }
}
