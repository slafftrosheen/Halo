package com.halo.app.ui.screens.settings

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.halo.app.language.AppLanguagePreferences
import com.halo.app.language.AppLanguageProvider
import com.halo.app.language.resolveUiText
import com.halo.app.ui.presence.*
import com.halo.app.ui.theme.HaloTheme
import com.halo.sdk.provider.AiModelRole
import com.halo.sdk.runtime.Storage
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Config
import androidx.test.core.app.ApplicationProvider
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class ModelBrowserRoleContextTest {
    @get:Rule val compose = createComposeRule()

    @Test fun liveRoleContextWrapsInRussianAndInvalidatesOnlyThePreviousPageLease() {
        val filter = mutableStateOf(ModelBrowserFilter())
        val actions = mutableStateOf<ShellPageProjection?>(null)
        val utility = mutableStateOf<ShellUtilityProjection?>(null)
        val language = AppLanguagePreferences(MemoryStorage(), "ru")
        var backs = 0
        var referenceLayout: TextLayoutResult? = null
        compose.setContent {
            AppLanguageProvider(language) {
                HaloTheme(darkTheme = false, fontScale = 2f) {
                    CompositionLocalProvider(LocalShellHaloOrb provides true, LocalShellPageActions provides actions,
                        LocalShellUtilityBand provides utility) {
                        Column(Modifier.width(240.dp).testTag("role-context-host")) {
                            val navigation = rememberNavController()
                            Box(Modifier.height(1.dp)) {
                                NavHost(navigation, startDestination = "models") {
                                    composable("models") {
                                        SettingsChildZone("Models", { backs++ }, contextLabel = filter.value.role?.let(::modelRoleLabel))
                                    }
                                }
                            }
                            utility.value?.let { RenderShellUtilityBand(it) }
                            // Same width/style without a relocated page: distinguish actual
                            // clipped glyphs from paragraph-canvas metric rounding.
                            Text("Модели · Действия и инструменты", Modifier.width(216.dp).clearAndSetSemantics {},
                                style = MaterialTheme.typography.titleSmall, onTextLayout = { referenceLayout = it })
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Модели").assertIsDisplayed()
        val generic = compose.runOnIdle { assertEquals(0, backs); requireNotNull(actions.value) }
        compose.runOnIdle { filter.value = filter.value.copy(role = AiModelRole.AGENTIC) }
        compose.onNodeWithText("Модели · Действия и инструменты").assertIsDisplayed()
        compose.onAllNodesWithText("Модели · Действия и инструменты").assertCountEquals(1)
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithText("Модели · Действия и инструменты")
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.isNotEmpty())
        val layout = layouts.single()
        val nodeBounds = compose.onNodeWithText("Модели · Действия и инструменты").fetchSemanticsNode().boundsInRoot
        val parentBounds = compose.onNodeWithTag("role-context-host").fetchSemanticsNode().boundsInRoot
        val control = requireNotNull(referenceLayout)
        val diagnostic = "labelSize=${layout.size} constraints=${layout.layoutInput.constraints} " +
            "paragraph=${layout.multiParagraph.width}x${layout.multiParagraph.height} " +
            "overflow=${layout.didOverflowWidth}/${layout.didOverflowHeight} lines=${layout.lineCount} " +
            "node=$nodeBounds parent=$parentBounds controlSize=${control.size} " +
            "controlParagraph=${control.multiParagraph.width}x${control.multiParagraph.height} " +
            "controlOverflow=${control.didOverflowWidth}/${control.didOverflowHeight} " +
            (0 until layout.lineCount).joinToString { line ->
                "line$line=[${layout.getLineLeft(line)},${layout.getLineRight(line)},${layout.getLineBottom(line)},end=${layout.getLineEnd(line, true)}]"
            }
        println(diagnostic)
        assertTrue(diagnostic, layout.lineCount > 0)
        // Complete glyph/line containment is the acceptance gate. A paragraph
        // canvas flag may also overflow on the same unconstrained text control.
        // The control has fixed width; the relocated label is intrinsic width.
        // v12 measured all31characters within32x35px while its paragraph canvas
        // was216px wide. Flag parity across these width contracts is invalid.
        assertEquals(diagnostic, layout.size.width.toFloat(), nodeBounds.width, 1f)
        assertEquals(diagnostic, layout.size.height.toFloat(), nodeBounds.height, 1f)
        assertTrue(diagnostic, nodeBounds.left >= parentBounds.left && nodeBounds.right <= parentBounds.right)
        assertTrue(diagnostic, nodeBounds.top >= parentBounds.top && nodeBounds.bottom <= parentBounds.bottom)
        for (line in 0 until layout.lineCount) {
            assertFalse(diagnostic, layout.isLineEllipsized(line))
            assertTrue(diagnostic, layout.getLineLeft(line) >= -1f)
            assertTrue(diagnostic, layout.getLineRight(line) <= layout.size.width + 1f)
            assertTrue(diagnostic, layout.getLineBottom(line) <= layout.size.height + 1f)
        }
        assertEquals(diagnostic, layout.layoutInput.text.length, layout.getLineEnd(layout.lineCount - 1, true))
        compose.runOnIdle {
            val current = requireNotNull(actions.value)
            assertEquals(generic.owner.navigationEntryId, current.owner.navigationEntryId)
            assertNotEquals(generic.owner.sourceIdentity, current.owner.sourceIdentity)
            assertFalse(generic.invoke("settings.child.back"))
            assertTrue(current.invoke("settings.child.back"))
            assertEquals(1, backs)
            filter.value = filter.value.copy(role = null)
        }
        compose.onNodeWithText("Модели").assertIsDisplayed()
        compose.runOnIdle { assertEquals(generic.owner, actions.value?.owner) }
    }

    @Test fun inheritedMarkerHasExplicitCopyInAllFourLocales() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val expected = mapOf("en" to "Uses conversation default", "ru" to "Используется модель разговоров по умолчанию",
            "lv" to "Izmanto noklusējuma sarunu modeli", "pl" to "Używa domyślnego modelu rozmowy")
        for ((tag, text) in expected) {
            val resources = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(tag))
            }).resources
            assertEquals(text, resolveUiText(resources, "Uses conversation default"))
        }
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
