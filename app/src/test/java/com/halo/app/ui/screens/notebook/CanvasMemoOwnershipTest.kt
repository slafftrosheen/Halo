package com.halo.app.ui.screens.notebook

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.halo.runtime.kernel.resource.DefaultResourceLeaseManager
import com.halo.sdk.runtime.resource.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class CanvasMemoOwnershipTest {
    @Test @Config(sdk = [26, 27])
    fun unsupportedPhoneRoutingDoesNotAcquireMicrophoneOrCreateStagingFiles() {
        val resources = DefaultResourceLeaseManager()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = java.io.File(context.filesDir, "workspace/owned/voice-inbox")
        val before = directory.listFiles()?.map { it.name }?.toSet().orEmpty()
        val recorder = CanvasVoiceMemoRecorder(context, resources)
        val failure = assertThrows(IllegalStateException::class.java) { recorder.start() }
        assertTrue(failure.message.orEmpty().contains("Android 9"))
        assertFalse(recorder.isRecording)
        assertTrue(resources.snapshot().active.isEmpty())
        assertEquals(before, directory.listFiles()?.map { it.name }?.toSet().orEmpty())
    }

    @Test @Config(sdk = [26, 27])
    fun unsupportedPlaybackRoutingDoesNotAcquireAudioOrStartPlayback() {
        val resources = DefaultResourceLeaseManager()
        val player = LocalMemoPlayer(ApplicationProvider.getApplicationContext<Context>(), resources)
        val failure = assertThrows(IllegalStateException::class.java) {
            player.toggle(android.net.Uri.parse("content://memo/test"), onError = {}, onEnd = {})
        }
        assertTrue(failure.message.orEmpty().contains("Android 9"))
        assertTrue(resources.snapshot().active.isEmpty())
        player.release()
    }

    @Test fun busyHaloMicrophoneIsNeverPreemptedAndCancelDoesNotReleaseItsLease() {
        val resources = DefaultResourceLeaseManager()
        val existing = resources.tryAcquire(ResourceLeaseRequest("halo", ResourceHolder("halo"), setOf(
            ResourceClaim(ResourceKey(HaloResourceIds.AudioInput, ResourceEndpointId("host:primary"))),
        ), preemption = ResourcePreemptionPolicy.NEVER)) as ResourceAcquireResult.Granted
        val recorder = CanvasVoiceMemoRecorder(ApplicationProvider.getApplicationContext<Context>(), resources)
        assertTrue(runCatching { recorder.start() }.isFailure)
        assertFalse(recorder.isRecording)
        recorder.cancel()
        assertFalse(existing.lease.isReleased)
        assertEquals(1, resources.snapshot().active.size)
        existing.lease.release()
    }
    @Test fun failedPhoneMicrophoneSetupReleasesOwnedLeaseAndDoesNotProduceSavedAudio() {
        val resources = DefaultResourceLeaseManager()
        val recorder = CanvasVoiceMemoRecorder(ApplicationProvider.getApplicationContext<Context>(), resources)
        // Robolectric has no physical built-in microphone; setup must fail closed.
        assertTrue(runCatching { recorder.start() }.isFailure)
        assertNull(recorder.stop())
        recorder.cancel()
        assertTrue(resources.snapshot().active.isEmpty())
    }
}
