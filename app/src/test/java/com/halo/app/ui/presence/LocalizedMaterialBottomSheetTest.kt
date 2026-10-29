package com.halo.app.ui.presence

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.halo.app.ui.theme.HostlessHaloTheme
import java.util.Locale
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "ru-rRU-w412dp-h800dp")
@OptIn(ExperimentalMaterial3Api::class)
class LocalizedMaterialBottomSheetTest {
    @get:Rule val compose = createComposeRule()
    @Test fun systemRussianAppEnglishLocalizesMaterialAndPreservesActivity() = check("en")
    @Test fun appRussianLocalizesMaterialAndPreservesActivity() = check("ru")
    @Test fun appLatvianLocalizesMaterialAndPreservesActivity() = check("lv")
    @Test fun appPolishLocalizesMaterialAndPreservesActivity() = check("pl")

    private fun check(tag: String) {
        var close = ""; var handle = ""; var systemClose = ""
        var base: Context? = null; var contentContext: Context? = null
        var registryMatches = false
        var registryPresent = false
        var inheritedFontScale = 0f
        var dialogClose = ""
        compose.setContent { HostlessHaloTheme(fontScale = 2f) {
            val original = LocalContext.current
            val registry = LocalActivityResultRegistryOwner.current
            val configuration = LocalConfiguration.current
            val resources = remember(original, tag, configuration) {
                original.createConfigurationContext(Configuration(configuration).apply {
                    setLocale(Locale.forLanguageTag(tag))
                }).resources
            }
            base = original
            close = resources.getString(androidx.compose.ui.R.string.close_sheet)
            handle = resources.getString(androidx.compose.material3.R.string.m3c_bottom_sheet_drag_handle_description)
            systemClose = original.resources.getString(androidx.compose.ui.R.string.close_sheet)
            CompositionLocalProvider(LocalConfiguration provides resources.configuration, LocalResources provides resources) {
                LocalizedMaterialBottomSheet({}, rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
                    contentContext = LocalContext.current
                    dialogClose = LocalView.current.context.resources.getString(androidx.compose.ui.R.string.close_sheet)
                    registryPresent = registry != null && LocalActivityResultRegistryOwner.current != null
                    registryMatches = LocalActivityResultRegistryOwner.current === registry
                    inheritedFontScale = LocalDensity.current.fontScale
                    Text("App sheet content")
                }
            }
        } }
        compose.onNodeWithText("App sheet content").assertIsDisplayed()
        compose.onNodeWithContentDescription(close).assertExists()
        compose.onNodeWithContentDescription(handle).assertExists()
        if (tag == "en") {
            assertNotEquals("The test must exercise a real system/app locale mismatch", close, systemClose)
            compose.onNodeWithContentDescription(systemClose).assertDoesNotExist()
        }
        assertEquals("Material dialog must actually use the app-locale resources", close, dialogClose)
        assertSame(base, contentContext)
        assertTrue("Activity-result registry must be present, not two null owners", registryPresent)
        assertEquals("App font scale must survive the attached Material host", 2f, inheritedFontScale, 0.001f)
        assertTrue("Activity-result registry owner must survive the Material boundary", registryMatches)
        var current = requireNotNull(base)
        while (current is ContextWrapper && current !is Activity) current = current.baseContext
        assertTrue("Original context must remain Activity-backed", current is Activity)
        val wrapper = MaterialLocaleContext(requireNotNull(base), requireNotNull(base).resources)
        assertSame(base, wrapper.baseContext)
        assertSame(base!!.getSystemService(Context.INPUT_METHOD_SERVICE), wrapper.getSystemService(Context.INPUT_METHOD_SERVICE))
    }
}
