package com.halo.app.ui.screens.devices

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.halo.app.audio.HeyCyanAudioPairingState
import com.halo.app.capture.WearableCaptureStatus
import com.halo.app.ui.screens.settings.BrandBook3PowerManager
import com.halo.app.ui.theme.HostlessHaloTheme
import com.halo.app.ui.workspace.HaloWorkspaceHeader
import com.halo.sdk.runtime.embodiment.DeviceKind
import com.halo.sdk.runtime.embodiment.SessionState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class, qualifiers = "en-w360dp-h780dp",
    shadows = [BrandBook3PowerManager::class])
class GlassesBrandBookVisualTest {
    @Test fun savedLight360() = capture(false, false)
    @Test fun readyDark360() = capture(true, true)
    @Test fun savedLargeText360() = capture(false, false, scale = 2f)
    @Test @Config(qualifiers = "en-w412dp-h892dp")
    fun readyLight412() = capture(true, false, width = 412, height = 892)

    private fun capture(ready: Boolean, dark: Boolean, scale: Float = 1f,
        width: Int = 360, height: Int = 780) = captureRoboImage {
        HostlessHaloTheme(darkTheme = dark, fontScale = scale) {
            Column(Modifier.width(width.dp).height(height.dp).background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                HaloWorkspaceHeader("My glasses", onBack = {})
                GlassesControlsContent(GenericDeviceUiState("glasses", "My glasses", DeviceKind.HEYCYAN_GLASSES,
                    ready, "Ready", false, if (ready) "72%" else "Unknown", firmware = "Unknown", lastSeen = "Never",
                    wearStateText = "WORN", sessionState = if (ready) SessionState.READY else SessionState.SAVED,
                    mediaPhotoCount = if (ready) 12 else null, hasLiveGlasses = ready),
                    HeyCyanAudioPairingState.NotAssociated, WearableCaptureStatus(),
                    onConnect = {}, onPhoto = {}, onVideo = {}, onAudio = {}, onReviewPhoto = {},
                    onCopy = {}, onRefresh = {}, onConnectAudio = {}, onAutoConnect = {}, onForget = {})
            }
        }
    }
}
