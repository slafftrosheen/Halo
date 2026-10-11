package com.halo.app.ui.screens.settings

import android.app.Application
import com.github.takahirom.roborazzi.captureRoboImage
import com.halo.app.language.AppLanguage
import com.halo.app.ui.theme.HaloTheme
import com.halo.sdk.provider.AiContextPolicy
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class, qualifiers = "w320dp-h800dp")
class SettingsComfortSnapshotTest {
    @Test fun everydaySettingsLight() = captureRoboImage {
        HaloTheme(darkTheme = false) { SettingsContent(SettingsUiState(), AppLanguage.ENGLISH, {}, {}, {}) }
    }
    @Test fun everydaySettingsDark() = captureRoboImage {
        HaloTheme(darkTheme = true) { SettingsContent(SettingsUiState(), AppLanguage.ENGLISH, {}, {}, {}) }
    }
    @Test @Config(qualifiers = "ru-rRU-w320dp-h800dp")
    fun russianSettingsLargeText() = captureRoboImage {
        HaloTheme(darkTheme = false, fontScale = 2f) { SettingsContent(SettingsUiState(), AppLanguage.RUSSIAN, {}, {}, {}) }
    }
    @Test fun privacyHasOneHome() = captureRoboImage {
        HaloTheme(darkTheme = false) { ContextPrivacyContent(AiContextPolicy(), {}, {}, {}) }
    }
}
