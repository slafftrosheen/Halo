package com.halo.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon
import com.halo.app.ui.theme.HaloTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class HaloIconSizingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun voiceButtonGlyphHasDefaultSizeAndHonorsExplicitKidsSize() {
        compose.setContent {
            HaloTheme {
                Column {
                    IconButton(onClick = {}) { HaloIcon(HaloGlyph.LISTEN, "Dictate") }
                    IconButton(onClick = {}, modifier = Modifier.size(72.dp)) {
                        HaloIcon(HaloGlyph.LISTEN, "Kids dictate", Modifier.size(32.dp))
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("Dictate", useUnmergedTree = true)
            .assertWidthIsEqualTo(24.dp).assertHeightIsEqualTo(24.dp)
        compose.onNodeWithContentDescription("Kids dictate", useUnmergedTree = true)
            .assertWidthIsEqualTo(32.dp).assertHeightIsEqualTo(32.dp)
    }
}
