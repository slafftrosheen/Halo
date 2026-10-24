package com.halo.app.ui.screens.settings

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.halo.app.language.AppLanguage
import com.halo.app.ui.theme.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The real language screen rendered in every supported appearance combination. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h800dp")
class Round2ThemeMatrixVisualTest {
    @org.junit.Before fun useStillMotionForCapture() {
        android.provider.Settings.Global.putFloat(org.robolectric.RuntimeEnvironment.getApplication().contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }

    private fun capture(dark: Boolean, tone: HaloTone, accent: HaloAccent) = captureRoboImage {
        HaloTheme(darkTheme = dark, palette = HaloPalette(accent, tone)) {
            Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
                Box(Modifier.size(360.dp, 740.dp)) {
                    LanguagePickerPage(AppLanguage.ENGLISH, {}, {})
                }
            }
        }
    }
    @Test fun light_natural_mint() = capture(false, HaloTone.NATURAL, HaloAccent.MINT)
    @Test fun light_natural_blue() = capture(false, HaloTone.NATURAL, HaloAccent.BLUE)
    @Test fun light_natural_lilac() = capture(false, HaloTone.NATURAL, HaloAccent.LILAC)
    @Test fun light_natural_coral() = capture(false, HaloTone.NATURAL, HaloAccent.CORAL)
    @Test fun light_warm_mint() = capture(false, HaloTone.WARM, HaloAccent.MINT)
    @Test fun light_warm_blue() = capture(false, HaloTone.WARM, HaloAccent.BLUE)
    @Test fun light_warm_lilac() = capture(false, HaloTone.WARM, HaloAccent.LILAC)
    @Test fun light_warm_coral() = capture(false, HaloTone.WARM, HaloAccent.CORAL)
    @Test fun light_cool_mint() = capture(false, HaloTone.COOL, HaloAccent.MINT)
    @Test fun light_cool_blue() = capture(false, HaloTone.COOL, HaloAccent.BLUE)
    @Test fun light_cool_lilac() = capture(false, HaloTone.COOL, HaloAccent.LILAC)
    @Test fun light_cool_coral() = capture(false, HaloTone.COOL, HaloAccent.CORAL)
    @Test fun dark_natural_mint() = capture(true, HaloTone.NATURAL, HaloAccent.MINT)
    @Test fun dark_natural_blue() = capture(true, HaloTone.NATURAL, HaloAccent.BLUE)
    @Test fun dark_natural_lilac() = capture(true, HaloTone.NATURAL, HaloAccent.LILAC)
    @Test fun dark_natural_coral() = capture(true, HaloTone.NATURAL, HaloAccent.CORAL)
    @Test fun dark_warm_mint() = capture(true, HaloTone.WARM, HaloAccent.MINT)
    @Test fun dark_warm_blue() = capture(true, HaloTone.WARM, HaloAccent.BLUE)
    @Test fun dark_warm_lilac() = capture(true, HaloTone.WARM, HaloAccent.LILAC)
    @Test fun dark_warm_coral() = capture(true, HaloTone.WARM, HaloAccent.CORAL)
    @Test fun dark_cool_mint() = capture(true, HaloTone.COOL, HaloAccent.MINT)
    @Test fun dark_cool_blue() = capture(true, HaloTone.COOL, HaloAccent.BLUE)
    @Test fun dark_cool_lilac() = capture(true, HaloTone.COOL, HaloAccent.LILAC)
    @Test fun dark_cool_coral() = capture(true, HaloTone.COOL, HaloAccent.CORAL)
}
