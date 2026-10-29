package com.halo.app.ui.screens.settings

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
import org.robolectric.annotation.Config
import androidx.test.core.app.ApplicationProvider
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ModelBrowserRoleContextTest {
    @get:Rule val compose = createComposeRule()

    @Test fun liveRoleContextWrapsInRussianAndInvalidatesOnlyThePreviousPageLease() {
        val filter = mutableStateOf(ModelBrowserFilter())
        val actions = mutableStateOf<ShellPageProjection?>(null)
        val utility = mutableStateOf<ShellUtilityProjection?>(null)
        val language = AppLanguagePreferences(MemoryStorage(), "ru")
        var backs = 0
        compose.setContent {
            AppLanguageProvider(language) {
                HaloTheme(darkTheme = false, fontScale = 2f) {
                    CompositionLocalProvider(LocalShellHaloOrb provides true, LocalShellPageActions provides actions,
                        LocalShellUtilityBand provides utility) {
                        Column(Modifier.width(240.dp)) {
                            val navigation = rememberNavController()
                            Box(Modifier.height(1.dp)) {
                                NavHost(navigation, startDestination = "models") {
                                    composable("models") {
                                        SettingsChildZone("Models", { backs++ }, contextLabel = filter.value.role?.let(::modelRoleLabel))
                                    }
                                }
                            }
                            utility.value?.let { RenderShellUtilityBand(it) }
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
        assertFalse(layouts.single().hasVisualOverflow)
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
