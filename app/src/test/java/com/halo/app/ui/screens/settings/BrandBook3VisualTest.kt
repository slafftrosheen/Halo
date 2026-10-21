package com.halo.app.ui.screens.settings

import android.app.Application
import android.os.PowerManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.halo.app.language.AppLanguage
import com.halo.app.ui.theme.HostlessHaloTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowPowerManager
import java.util.concurrent.Executor

/** Full ST01/ST02 renders, rather than a surrogate card assembled only for a test. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h780dp", shadows = [BrandBook3PowerManager::class])
class BrandBook3VisualTest {
    @Test fun settingsDark360() = capture(true)
    @Test fun settingsLight360() = capture(false)
    @Test fun advancedDark360() = capture(true, advanced = true)
    @Test fun advancedLight360() = capture(false, advanced = true)
    @Test fun settingsLargeType() = capture(false, scale = 2f)
    @Test fun advancedLargeType() = capture(true, scale = 2f, advanced = true)
    @Test fun configuredCloudSettings() = capture(true, configured = true)
    @Test fun configuredCloudLargeType() = capture(false, scale = 2f, configured = true)

    @Test
    @Config(qualifiers = "w412dp-h892dp")
    fun settingsDark412() = capture(true, width = 412, height = 892)

    @Test
    @Config(qualifiers = "w412dp-h892dp")
    fun settingsLight412() = capture(false, width = 412, height = 892)

    private fun capture(dark: Boolean, width: Int = 360, height: Int = 780,
        scale: Float = 1f, advanced: Boolean = false, configured: Boolean = false) = captureRoboImage {
        HostlessHaloTheme(darkTheme = dark, fontScale = scale) {
            Surface(Modifier.width(width.dp).height(height.dp), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize()) {
                    SettingsContent(if (configured) SettingsUiState(
                        providerLocation = com.halo.sdk.provider.AiExecutionLocation.REMOTE,
                        voiceStatus = "Ready") else SettingsUiState(),
                        AppLanguage.ENGLISH, {}, {}, {}, initialAdvanced = advanced)
                }
            }
        }
    }
}

/** Robolectric has no thermal binder service; these captures use a static presence. */
@Implements(PowerManager::class)
class BrandBook3PowerManager : ShadowPowerManager() {
    @Implementation(minSdk = 29)
    fun addThermalStatusListener(executor: Executor, listener: PowerManager.OnThermalStatusChangedListener) = Unit

    @Implementation(minSdk = 29)
    fun removeThermalStatusListener(listener: PowerManager.OnThermalStatusChangedListener) = Unit
}
