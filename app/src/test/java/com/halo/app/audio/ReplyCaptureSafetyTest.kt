package com.halo.app.audio

import android.media.AudioDeviceInfo
import com.halo.app.ui.screens.home.VoiceInputCommandQueue
import com.halo.sdk.runtime.SpeechOutputTarget
import com.halo.sdk.runtime.SpeechRouteEvidence
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ReplyCaptureSafetyTest {
    private fun speaking(type: Int, id: Int? = 42) = HaloAudioSessionState.Speaking(
        SpeechOutputTarget.HostDefault, "reply", SpeechRouteEvidence(false, type, null, null, androidDeviceId = id))

    @Test fun verifiedHeadsetAllowsCaptureWithoutClaimingPhoneEchoControl() {
        assertTrue(speechReplyCaptureSafe(speaking(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP), "reply", false))
        assertTrue(speechReplyCaptureSafe(speaking(AudioDeviceInfo.TYPE_WIRED_HEADPHONES), "reply", false))
    }

    @Test fun speakerNeedsActiveNativeEchoControlAndUnknownRoutesRemainBlocked() {
        assertFalse(speechReplyCaptureSafe(speaking(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER), "reply", false))
        assertTrue(speechReplyCaptureSafe(speaking(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER), "reply", true))
        assertFalse(speechReplyCaptureSafe(speaking(AudioDeviceInfo.TYPE_HDMI), "reply", true))
        assertFalse(speechReplyCaptureSafe(speaking(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, null), "reply", true))
    }

    @Test fun preparationAndOtherRepliesCannotOpenAnInterruptionWindow() {
        assertFalse(speechReplyCaptureSafe(HaloAudioSessionState.Idle, "reply", true))
        assertFalse(speechReplyCaptureSafe(HaloAudioSessionState.PreparingOutput(SpeechOutputTarget.Phone, "reply"), "reply", true))
        assertFalse(speechReplyCaptureSafe(speaking(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP), "stale", true))
    }

    @Test fun queuedCaptureRechecksPlaybackAfterItEnds() = runTest {
        val queue = VoiceInputCommandQueue()
        val held = CompletableDeferred<Unit>()
        val entered = CompletableDeferred<Unit>()
        val ticket = queue.current()
        var state: HaloAudioSessionState = speaking(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
        var captures = 0
        val holding = launch { queue.runIfCurrent(ticket) { entered.complete(Unit); held.await() } }
        entered.await()
        val pending = async { queue.runIfCurrent(ticket, allowed = { speechReplyCaptureSafe(state, "reply", false) }) { captures++ } }
        runCurrent()
        state = HaloAudioSessionState.Idle
        held.complete(Unit)
        holding.join()
        assertFalse(pending.await())
        assertEquals(0, captures)
    }
}
