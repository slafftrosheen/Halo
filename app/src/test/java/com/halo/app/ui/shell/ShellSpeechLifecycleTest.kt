package com.halo.app.ui.shell

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class ShellSpeechLifecycleTest {
    private class Owner : LifecycleOwner { override val lifecycle = LifecycleRegistry(this) }

    @Test fun actualBackgroundReleasesCaptureOnce() {
        val owner = Owner(); var released = 0
        owner.lifecycle.currentState = Lifecycle.State.RESUMED
        val binding = bindShellSpeechLifecycle(owner.lifecycle, { false }) { released++ }
        owner.lifecycle.currentState = Lifecycle.State.CREATED
        assertEquals(1, released)
        binding.close()
        assertEquals(1, released)
    }

    @Test fun configurationChangeAndCompositionDisposalPreserveRetainedSpeechOwner() {
        val owner = Owner(); var released = 0
        owner.lifecycle.currentState = Lifecycle.State.RESUMED
        val binding = bindShellSpeechLifecycle(owner.lifecycle, { true }) { released++ }
        owner.lifecycle.currentState = Lifecycle.State.CREATED
        binding.close()
        assertEquals(0, released)
    }

    @Test fun replacingPresentationDoesNotCancelOrReplayAnInteraction() {
        val owner = Owner(); var released = 0
        owner.lifecycle.currentState = Lifecycle.State.RESUMED
        bindShellSpeechLifecycle(owner.lifecycle, { false }) { released++ }.close()
        val next = bindShellSpeechLifecycle(owner.lifecycle, { false }) { released++ }
        assertEquals(0, released)
        owner.lifecycle.currentState = Lifecycle.State.CREATED
        assertEquals(1, released)
        next.close()
    }

    @Test fun miniRouteStopDoesNotReleaseCaptureUntilItsNativeHostStops() {
        val host = Owner(); val route = Owner(); var released = 0
        host.lifecycle.currentState = Lifecycle.State.RESUMED
        route.lifecycle.currentState = Lifecycle.State.RESUMED
        val binding = bindShellSpeechLifecycle(shellSpeechHostLifecycle(host, route.lifecycle), { false }) { released++ }
        route.lifecycle.currentState = Lifecycle.State.CREATED
        assertEquals(0, released)
        host.lifecycle.currentState = Lifecycle.State.CREATED
        assertEquals(1, released)
        binding.close()
    }

    @Test fun successfulOwnAppHandoffSkipsOnlyTheFinishingHostStop() {
        val host = Owner(); val transfer = ShellSpeechHandoff(); var released = 0; var finishing = false
        host.lifecycle.currentState = Lifecycle.State.RESUMED
        val binding = bindShellSpeechLifecycle(host.lifecycle,
            { transfer.preservesSpeechOnStop(false, finishing) }) { released++ }
        val order = mutableListOf<String>()
        org.junit.Assert.assertTrue(transfer.launchAndFinish(
            { order += "launch"; org.junit.Assert.assertFalse(transfer.preservesSpeechOnStop(false, false)) },
            { order += "finish"; finishing = true }, { error("Unexpected failure") }))
        assertEquals(listOf("launch", "finish"), order)
        host.lifecycle.currentState = Lifecycle.State.CREATED
        assertEquals(0, released)
        binding.close()
        org.junit.Assert.assertFalse(ShellSpeechHandoff().preservesSpeechOnStop(false, true))
        org.junit.Assert.assertTrue(ShellSpeechHandoff().preservesSpeechOnStop(true, false))
    }

    @Test fun failedHandoffReleasesCaptureAndDoesNotExemptLaterBackgroundOrClose() {
        val transfer = ShellSpeechHandoff(); var released = 0; var finished = false
        org.junit.Assert.assertFalse(transfer.launchAndFinish(
            { throw android.content.ActivityNotFoundException("Unavailable") },
            { finished = true }, { released++ }))
        assertEquals(1, released)
        org.junit.Assert.assertFalse(finished)
        org.junit.Assert.assertFalse(transfer.preservesSpeechOnStop(false, false))
        org.junit.Assert.assertFalse(transfer.preservesSpeechOnStop(false, true))
        org.junit.Assert.assertTrue(transfer.preservesSpeechOnStop(true, false))
    }
}
