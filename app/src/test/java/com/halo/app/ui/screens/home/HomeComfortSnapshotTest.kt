package com.halo.app.ui.screens.home

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.halo.app.language.uiText
import com.halo.app.ui.theme.HaloTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class, qualifiers = "w320dp-h800dp")
class HomeComfortSnapshotTest {
    @Test fun emptyComposer() = captureRoboImage { HaloTheme(darkTheme = false) { Fixture(false) } }
    @Test fun busyComposer() = captureRoboImage { HaloTheme(darkTheme = true) { Fixture(true) } }
    @Test @Config(qualifiers = "ru-rRU-w320dp-h800dp")
    fun twoHundredPercentText() = captureRoboImage { HaloTheme(darkTheme = false, fontScale = 2f) { Fixture(true) } }
    @Test @Config(qualifiers = "ru-rRU-w320dp-h800dp")
    fun russianLargeText() = captureRoboImage { HaloTheme(darkTheme = false, fontScale = 1.5f) { Fixture(true) } }
    @Test @Config(qualifiers = "lv-rLV-w320dp-h800dp")
    fun latvianLargeText() = captureRoboImage { HaloTheme(darkTheme = false, fontScale = 1.5f) { Fixture(false) } }
}

@Composable
private fun Fixture(busy: Boolean) {
    Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
    Column(Modifier.width(360.dp).background(MaterialTheme.colorScheme.background).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(uiText("Home"), style = MaterialTheme.typography.headlineMedium)
        Text(uiText(if (busy) "Needs your approval" else "Ask Halo something, or tap Talk to start speaking."),
            style = MaterialTheme.typography.bodyLarge)
        HaloComposer("", {}, {}, {}, {}, !busy, !busy, false, uiText("Talk"), busy)
    }
    }
}
