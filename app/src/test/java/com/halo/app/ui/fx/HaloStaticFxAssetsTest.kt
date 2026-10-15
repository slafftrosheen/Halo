package com.halo.app.ui.fx

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Fail early if assets move, themes diverge, or a motion asset has no static twin. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class HaloStaticFxAssetsTest {
    private val context: Application = ApplicationProvider.getApplicationContext()

    @Test fun everySuppliedMomentHasTwoStaticTwins() {
        HaloFxMoment.entries.forEach { moment ->
            assertSvgAsset(moment.asset, dark = true)
            assertSvgAsset(moment.asset, dark = false)
        }
    }

    @Test fun everyToolAndPairingFxHasTwoStaticTwins() {
        (HaloFxTool.entries.map { "tool_${it.asset}" } + "device_connect").forEach {
            assertSvgAsset(it, dark = true)
            assertSvgAsset(it, dark = false)
        }
    }

    private fun assertSvgAsset(name: String, dark: Boolean) {
        val path = "halo/fx/static/${haloStaticAssetName(name, dark)}"
        val bytes = context.assets.open(path).use { it.readBytes() }
        assertTrue("Invalid SVG $path", bytes.size > 100 && bytes.decodeToString().startsWith("<svg"))
    }
}
