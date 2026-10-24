package com.halo.app.ui.fx

import android.app.Application
import android.util.Xml
import androidx.test.core.app.ApplicationProvider
import com.airbnb.lottie.LottieCompositionFactory
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

/** Parse the shipped files through the playback parser, including every clip API uses. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class HaloMotionAssetContractTest {
    private val context: Application = ApplicationProvider.getApplicationContext()

    @Test fun everyMarkerUsedByStagedAndPackageEffectsExistsInBothThemes() {
        val staged = HaloFxTool.entries.flatMap { tool ->
            HaloFxToolPhase.entries.map { "tool_${tool.asset}" to it.marker }
        } + HaloFxPairingPhase.entries.map { "device_connect" to it.marker } + listOf(
            "read_aloud" to "reading", "learn_branch" to "grow",
            "learn_branch" to "choose", "explain_back" to "compare",
        )
        val clips = staged + HaloPackageMoment.entries.map { it.asset to it.marker }
        clips.distinct().forEach { (asset, markerName) ->
            listOf("dark", "light").forEach { theme ->
                val path = "halo/fx/halo_fx_${asset}_$theme.json"
                val result = LottieCompositionFactory.fromAssetSync(context, path)
                assertNotNull("$path: ${result.exception}", result.value)
                val composition = result.value!!
                val marker = composition.getMarker(markerName)
                assertNotNull("Missing $markerName in $path", marker)
                assertTrue("Empty $markerName in $path", marker!!.durationFrames > 0f)
                assertTrue("Early $markerName in $path", marker.startFrame >= composition.startFrame)
                // Lottie uses an exclusive end frame, adjusted by a small epsilon.
                assertTrue("Late $markerName in $path",
                    marker.startFrame + marker.durationFrames <= composition.endFrame + 1f)
                assertTrue("External image in $path", composition.images.isEmpty())
            }
        }
    }

    @Test fun everyLoadSiteHasWellFormedStaticArtworkInBothThemes() {
        val stills = HaloFxMoment.entries.map { it.asset } +
            HaloFxTool.entries.map { "tool_${it.asset}" } +
            listOf("device_connect", "read_aloud", "learn_branch", "explain_back") +
            HaloPackageMoment.entries.map { it.still }
        stills.distinct().forEach { asset ->
            listOf("dark", "light").forEach { theme ->
                val path = "halo/fx/static/halo_fx_${asset}_$theme.svg"
                context.assets.open(path).bufferedReader().use { input ->
                    val parser = Xml.newPullParser()
                    parser.setInput(input)
                    assertEquals(path, XmlPullParser.START_TAG, parser.nextTag())
                    assertEquals(path, "svg", parser.name)
                    assertFalse("Missing viewBox: $path", parser.getAttributeValue(null, "viewBox").isNullOrBlank())
                    var drawableTags = 0
                    while (parser.next() != XmlPullParser.END_DOCUMENT) {
                        if (parser.eventType == XmlPullParser.START_TAG) {
                            assertFalse("External image: $path", parser.name == "image")
                            if (parser.name in setOf("path", "circle", "rect", "ellipse", "line", "polyline", "polygon")) drawableTags++
                        }
                    }
                    assertTrue("Empty static artwork: $path", drawableTags > 0)
                }
            }
        }
    }
}
