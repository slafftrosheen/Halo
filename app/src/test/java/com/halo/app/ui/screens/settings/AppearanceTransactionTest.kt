package com.halo.app.ui.screens.settings

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.halo.app.ui.theme.*
import com.halo.app.ui.theme.HostlessHaloTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w412dp-h900dp")
class AppearanceTransactionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun modeAndBothPalettesStayLocalOnCancelAndRestoreAfterApply() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        context.getSharedPreferences("halo_palettes", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("halo_appearance", Context.MODE_PRIVATE).edit().clear().commit()
        val palettes = PalettePreferences(context)
        val appearance = AppearancePreferences(context)
        val assistant = AssistantPreferences(context)
        palettes.awaitReady()
        appearance.setModePersisted(AppearanceMode.LIGHT)
        compose.setContent { HostlessHaloTheme {
            val mode by appearance.mode.collectAsState()
            Column(Modifier.verticalScroll(rememberScrollState())) {
                AppearanceControls(mode, { draft -> appearance.applyAppearancePersisted(draft.savedMode, draft.mode,
                    palettes, draft.savedPalettes, draft.palettes) }, palettes, assistant)
            }
        } }
        fun editBoth() {
            compose.onNodeWithText("Blue").performScrollTo().performClick()
            compose.onNodeWithText("Dark").performScrollTo().performClick()
            compose.onNodeWithText("Rose").performScrollTo().performClick()
        }
        editBoth()
        assertEquals(AppearanceMode.LIGHT, appearance.mode.value)
        assertEquals(HaloPalettes(), palettes.state.value)
        compose.onNodeWithText("Cancel").performScrollTo().performClick()
        compose.onNode(hasText("Apply") and hasClickAction()).assertIsNotEnabled()
        assertEquals(AppearanceMode.LIGHT, appearance.mode.value)
        assertEquals(HaloPalettes(), palettes.state.value)
        editBoth()
        compose.onNode(hasText("Apply") and hasClickAction()).performScrollTo().performClick()
        compose.waitUntil(5_000) {
            appearance.mode.value == AppearanceMode.DARK &&
                palettes.state.value.light.accent == HaloAccent.BLUE && palettes.state.value.dark.accent == HaloAccent.ROSE
        }
        val restored = PalettePreferences(context)
        restored.awaitReady()
        val restoredAppearance = AppearancePreferences(context)
        restoredAppearance.awaitLoaded()
        assertEquals(HaloAccent.BLUE, restored.state.value.light.accent)
        assertEquals(HaloAccent.ROSE, restored.state.value.dark.accent)
        assertEquals(AppearanceMode.DARK, restoredAppearance.mode.value)
    }

    @Test fun uncertainRollbackSurvivesCancelAndUnrelatedSaveUntilSuccessfulReapply() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val palettes = PalettePreferences(context)
        val assistant = AssistantPreferences(context)
        runBlocking { palettes.selectPalettesPersisted(HaloPalettes()) }
        var fail = true
        val message = "Could not confirm that your previous appearance was restored. Saved choices may have changed. Try Apply again."
        compose.setContent { HostlessHaloTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                AppearanceControls(AppearanceMode.LIGHT, {
                    if (fail) throw AppearanceApplyException(true, IllegalStateException("synthetic rollback failure"))
                }, palettes, assistant)
            }
        } }
        // A known local draft keeps this UI failure case independent of other preference tests.
        compose.onNodeWithText("Coral").performScrollTo().performClick()
        compose.onNode(hasText("Apply") and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText(message).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Large").performScrollTo().performClick()
        compose.waitUntil(5_000) { assistant.state.value.fontScale == 1.25f }
        compose.onNodeWithText(message).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Cancel").performScrollTo().performClick()
        compose.onNodeWithText(message).performScrollTo().assertIsDisplayed()
        compose.onNode(hasText("Apply") and hasClickAction()).performScrollTo().assertIsEnabled()
        compose.runOnIdle { fail = false }
        compose.onNode(hasText("Apply") and hasClickAction()).performClick()
        compose.onNodeWithText(message).assertDoesNotExist()
    }

}
