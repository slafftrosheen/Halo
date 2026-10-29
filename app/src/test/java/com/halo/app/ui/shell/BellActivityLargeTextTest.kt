package com.halo.app.ui.shell

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.halo.app.language.*
import com.halo.app.ui.screens.activity.*
import com.halo.app.ui.theme.HostlessHaloTheme
import com.halo.sdk.runtime.Storage
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h800dp")
class BellActivityLargeTextTest {
    @get:Rule val compose = createComposeRule()

    @Test fun nestedRussianUtilityKeepsCloseBoundedAndSelectedHistoryVisible() {
        val selected = mutableStateOf(ActivityFilter.HISTORY)
        val routes = mutableListOf<String>()
        var closes = 0
        compose.setContent {
            AppLanguageProvider(AppLanguagePreferences(remember { MemoryStorage() }, "ru")) {
                HostlessHaloTheme(fontScale = 2f) {
                    Column(Modifier.width(360.dp)) {
                        HaloZone("home", 800.dp, compact = false, actions = emptyList(), onAction = {},
                            onNavigate = {}, attentionCount = 0, profile = {}, backdrop = {},
                            orb = { Box(Modifier.size(it)) }, utility = {
                                Column(Modifier.testTag("bell-header")) {
                                    BellSubviewControls("activity", { routes += it }, { closes++ })
                                }
                                ActivitySegmentedPill(selected.value, emptyMap()) { selected.value = it }
                            })
                    }
                }
            }
        }
        val header = compose.onNodeWithTag("bell-header").fetchSemanticsNode().boundsInRoot
        val density = compose.density.density
        assertTrue("Header grew to $header", header.height <= 96 * density)
        compose.onNodeWithContentDescription("Закрыть").assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, closes)
        compose.onNodeWithText("Завершено").assertIsDisplayed()
        val selectedBounds = compose.onNodeWithText("Завершено").getUnclippedBoundsInRoot()
        assertTrue("Selected filter clipped: $selectedBounds", selectedBounds.left >= 0.dp && selectedBounds.right <= 360.dp)
        listOf("Требует внимания", "Выполняется", "Завершено").forEach { label ->
            compose.onNodeWithText(label).performScrollTo().assertIsDisplayed().performClick()
        }
        assertEquals(ActivityFilter.HISTORY, selected.value)
        listOf("Уведомления", "Действия").forEach { label ->
            val button = compose.onNodeWithText(label).performScrollTo().assertIsDisplayed()
            val bounds = button.getUnclippedBoundsInRoot()
            assertTrue("Subview clipped: $label $bounds", bounds.left >= 8.dp && bounds.right <= 304.dp)
            button.performClick()
        }
        assertEquals(listOf("notifications", "activity"), routes)
    }

    private class MemoryStorage : Storage {
        private val values = mutableMapOf<String, Any>()
        override fun getString(key: String, defaultValue: String?) = values[key] as? String ?: defaultValue
        override fun putString(key: String, value: String?) { if (value == null) values.remove(key) else values[key] = value }
        override fun getInt(key: String, defaultValue: Int) = values[key] as? Int ?: defaultValue
        override fun putInt(key: String, value: Int) { values[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean) = values[key] as? Boolean ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
        override fun clear() { values.clear() }
    }
}
