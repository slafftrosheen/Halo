package com.halo.app.ui.presence

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.airbnb.lottie.LottieCompositionFactory
import com.halo.app.ui.glass.material.HaloRuntimeState
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.state.toSignalPresentation
import com.halo.sdk.provider.CognitiveSessionState
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class PresenceArtworkContractTest {
    @Test fun bundledAnimationsParseWithoutExternalAssets() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val files = context.assets.list("halo")!!.filter { it.endsWith(".json") }
        assertEquals(60, files.size)
        files.forEach { file ->
            val result = LottieCompositionFactory.fromAssetSync(context, "halo/$file")
            assertNotNull("$file: ${result.exception}", result.value)
            assertTrue(file, result.value!!.images.isEmpty())
        }
    }
    @Test fun everyFullMotionStateHasItsOwnLightDarkAndKidsLottie() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        for (artwork in animatedArtwork) {
            for (prefix in listOf("halo_orb_", "halo_kids_orb_")) {
                for (tone in listOf("dark", "light")) {
                    val name = "halo/${prefix}${artwork.key}_${tone}.json"
                    val animation = LottieCompositionFactory.fromAssetSync(context, name)
                    assertNotNull(name + ": " + animation.exception, animation.value)
                    val composition = animation.value!!
                    assertEquals(name, 240, composition.bounds.width())
                    assertEquals(name, 240, composition.bounds.height())
                    assertEquals(name, 30f, composition.frameRate)
                    assertTrue(name, composition.duration > 0f)
                }
            }
        }
    }

    @Test fun staticArtworkExistsForEveryStateThemeAccentAndKidsVariant() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        PresenceArtwork.entries.forEach { artwork ->
            listOf(false, true).forEach { dark ->
                (0..3).forEach { accent ->
                    assertNotNull(context.getDrawable(artwork.staticResource(dark, false, accent)))
                }
                assertNotNull(context.getDrawable(artwork.staticResource(dark, true, 0)))
            }
            assertNotNull(context.getDrawable(artwork.microResource()))
        }
    }
    @Test fun streamingOnlyShowsSpeakingWhenPlaybackIsActive() {
        val stream = CognitiveSessionState.Streaming("Hello")
        assertEquals(PresenceArtwork.STREAMING_TEXT, stream.toSignalPresentation(HaloRuntimeState.Idle).presenceArtwork())
        assertEquals(PresenceArtwork.SPEAKING, stream.toSignalPresentation(HaloRuntimeState.Speaking).presenceArtwork())
        assertEquals(PresenceArtwork.CAPTURING_VOICE, HaloRuntimeState.Capturing.toSignalPresentation().presenceArtwork())
        assertEquals(HaloGlyph.LISTEN, HaloRuntimeState.Capturing.toSignalPresentation().glyph)
    }
    @Test fun motionStopsForReducedMotionCompactAndBackgroundAndTerminalStatesHold() {
        assertFalse(PresenceArtwork.IDLE.motionPolicy(80f, true, true).animate)
        assertFalse(PresenceArtwork.IDLE.motionPolicy(64f, false, true).animate)
        assertTrue(PresenceArtwork.IDLE.motionPolicy(64f, false, true).compact)
        assertTrue(PresenceArtwork.THINKING.motionPolicy(40f, false, true).compact)
        assertFalse(PresenceArtwork.IDLE.motionPolicy(39f, false, true).compact)
        assertFalse(PresenceArtwork.IDLE.motionPolicy(80f, false, true).compact)
        assertFalse(PresenceArtwork.IDLE.motionPolicy(64f, true, true).compact)
        assertFalse(PresenceArtwork.IDLE.motionPolicy(64f, false, false).compact)
        // Compact speech gets the static waveform, not misleading miniature ripples.
        assertFalse(PresenceArtwork.SPEAKING.motionPolicy(64f, false, true).compact)
        // One-shot success and warning must not turn into perpetual compact animation.
        assertFalse(PresenceArtwork.SUCCESS.motionPolicy(64f, false, true).compact)
        assertFalse(PresenceArtwork.WARNING.motionPolicy(64f, false, true).compact)
        assertFalse(PresenceArtwork.IDLE.motionPolicy(88f, false, false).animate)
        assertTrue(PresenceArtwork.IDLE.motionPolicy(88f, false, true).loop)
        assertFalse(PresenceArtwork.SUCCESS.motionPolicy(88f, false, true).loop)
        assertTrue(PresenceArtwork.WARNING.motionPolicy(88f, false, true).animate)
        assertFalse(PresenceArtwork.WARNING.motionPolicy(88f, false, true).loop)
        listOf(PresenceArtwork.ERROR, PresenceArtwork.OFFLINE, PresenceArtwork.STOPPED).forEach {
            assertFalse(it.motionPolicy(160f, false, true).animate)
        }
    }
}
