package com.halo.app.ui.screens.settings

import android.app.Application
import androidx.compose.material3.Surface
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.halo.app.language.AppLanguage
import com.halo.app.ui.theme.*
import com.halo.app.ui.theme.HostlessHaloTheme as HaloTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class, qualifiers = "w320dp-h800dp")
class SettingsAppearanceVisualTest {
    @get:Rule val compose = createComposeRule()

    private fun capture(dark: Boolean, scale: Float = 0f, language: AppLanguage = AppLanguage.ENGLISH) {
        val preferences = PalettePreferences(ApplicationProvider.getApplicationContext<Application>())
        compose.setContent { HaloTheme(darkTheme = dark, fontScale = scale) { Surface {
            SettingsContent(SettingsUiState(appearanceMode = if (dark) AppearanceMode.DARK else AppearanceMode.LIGHT),
                language, {}, {}, {}, palettePreferences = preferences,
                assistantPreferences = AssistantPreferences(ApplicationProvider.getApplicationContext<Application>()))
        } } }
        compose.onNodeWithText(if (language == AppLanguage.RUSSIAN) "Оформление" else "Appearance")
            .performScrollTo().performClick()
        if (scale >= 2f) compose.onNodeWithText("Оформление")
            .assertHeightIsAtLeast(40.dp)
        if (scale >= 2f && language == AppLanguage.RUSSIAN) {
            val modeOption = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
            listOf("Светлая", "Тёмная", "Как на телефоне").forEach { label ->
                val layouts = mutableListOf<TextLayoutResult>()
                val node = compose.onNode(hasText(label) and hasAnyAncestor(modeOption), useUnmergedTree = true).assertIsDisplayed()
                node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertFalse("Mode label must not overflow: $label", layouts.single().hasVisualOverflow)
                assertEquals("A full-width mode must avoid midword wrapping: $label", 1, layouts.single().lineCount)
                compose.onNode(modeOption and hasText(label)).assertHasClickAction().assertHeightIsAtLeast(48.dp)
            }
        }
        compose.onNode(isDialog()).assertDoesNotExist()
        compose.onNode(isRoot()).captureRoboImage()
    }
    @Test fun lightAppearance() = capture(false)
    @Test fun darkAppearance() = capture(true)
    @Test @Config(qualifiers = "ru-rRU-w320dp-h800dp")
    fun russianLargeAppearance() = capture(false, 2f, AppLanguage.RUSSIAN)
}
