package com.halo.app.ui.presence

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.halo.app.ui.theme.HaloTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class, qualifiers = "w320dp-h800dp")
class HaloIdentitySnapshotTest {
    @Test fun lightIdentity() = capture(false)
    @Test fun darkIdentity() = capture(true)

    @Test fun narrowLargeTextControls() = captureRoboImage {
        HaloTheme(darkTheme = false, fontScale = 2f) {
            Surface {
                Column(Modifier.width(320.dp).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    com.halo.app.ui.design.HaloScreenHeader("Halo settings",
                        primaryAction = com.halo.app.ui.design.HaloHeaderAction("Save changes") {})
                    com.halo.app.ui.glass.components.HaloGlassSettingRow(
                        title = "Response language", value = "Follow app language", onClick = {})
                    com.halo.app.ui.design.HaloFocusAction("Continue with Halo",
                        com.halo.app.ui.icons.HaloGlyph.HALO, enabled = true, onClick = {})
                }
            }
        }
    }

    private fun capture(dark: Boolean) = captureRoboImage {
        HaloTheme(darkTheme = dark) {
            Surface {
                Column(Modifier.width(320.dp).padding(16.dp)) {
                    listOf(HaloSignalState.IDLE, HaloSignalState.LISTENING,
                        HaloSignalState.WAITING, HaloSignalState.ERROR).forEach { state ->
                        Row {
                            listOf(32.dp, 72.dp, 144.dp).forEach { size ->
                                HaloSignalFace(HaloSignalPresentation(state = state, label = state.name,
                                    reducedMotion = true), size = size)
                            }
                        }
                    }
                }
            }
        }
    }
}
