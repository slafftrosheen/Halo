package com.halo.app.ui.components

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.halo.app.R
import com.halo.app.ai.PrivacyMode
import com.halo.app.ui.theme.HostlessHaloTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h220dp")
class CompactPrivacyModeButtonTest {
    @get:Rule val compose = createComposeRule()
    @Test fun englishPrivateAt200PercentKeepsOneLabelLineAndFullAccessibleMode() = check(PrivacyMode.PRIVATE)
    @Test @Config(qualifiers = "ru-w360dp-h220dp")
    fun russianPowerAt200PercentDoesNotBecomeAFourLinePill() = check(PrivacyMode.CLOUD)
    @Test @Config(qualifiers = "pl-w360dp-h220dp")
    fun polishPrivateAt200PercentKeepsItsExistingActionReachable() = check(PrivacyMode.PRIVATE)

    private fun check(mode: PrivacyMode) {
        var clicked = 0
        val context = ApplicationProvider.getApplicationContext<Context>()
        val label = context.getString(if (mode == PrivacyMode.PRIVATE) R.string.interface4_private_short else R.string.interface4_power_short)
        val description = "Full authoritative mode: ${mode.name}"
        compose.setContent { HostlessHaloTheme(fontScale = 2f) {
            Surface(Modifier.fillMaxSize()) {
                Box(Modifier.width(112.dp).height(180.dp)) {
                    CompactPrivacyModeButton(mode, description, true, { clicked++ }, Modifier.width(112.dp))
                }
            }
        } }
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(label, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        assertEquals(1, results.single().lineCount)
        val button = compose.onNodeWithContentDescription(description).assertIsDisplayed().assertHasClickAction()
        assertTrue("The privacy slot must not consume a tall text column", button.fetchSemanticsNode().boundsInRoot.height <= 88f * context.resources.displayMetrics.density)
        button.performClick()
        assertEquals(1, clicked)
    }
}
