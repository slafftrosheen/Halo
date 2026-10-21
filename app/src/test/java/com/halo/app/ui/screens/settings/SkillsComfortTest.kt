package com.halo.app.ui.screens.settings

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.halo.app.ui.theme.HostlessHaloTheme as HaloTheme
import com.halo.runtime.cognition.skills.*
import com.halo.sdk.provider.ToolDefinition
import com.halo.sdk.runtime.skills.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private class PreviewSkills : SkillRegistry {
    override val skills = MutableStateFlow(DefaultSkillRegistry.defaultBuiltInSkills())
    override fun get(id: SkillId) = skills.value.firstOrNull { it.id == id }
    override fun enabledTools(allTools: List<ToolDefinition>) = allTools
    override suspend fun setEnabled(id: SkillId, enabled: Boolean) = Unit
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class, qualifiers = "w320dp-h800dp")
class SkillsComfortSnapshotTest {
    @Test fun skillsStartWithSearchAndModelSetup() = captureRoboImage {
        HaloTheme(darkTheme = false) { SkillsScreen(PreviewSkills(), {}) }
    }
    @Test @Config(qualifiers = "ru-rRU-w320dp-h800dp")
    fun russianSkillsAtLargeText() = captureRoboImage {
        HaloTheme(darkTheme = false, fontScale = 2f) { SkillsScreen(PreviewSkills(), {}) }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w320dp-h800dp")
class SkillsComfortInteractionTest {
    @get:Rule val compose = createComposeRule()
    @Test fun slowModelChecksDoNotHoldHostReadinessInCheckingState() {
        val pending = kotlinx.coroutines.CompletableDeferred<Unit>()
        compose.setContent { HaloTheme(darkTheme = false) {
            SkillsScreen(PreviewSkills(), {}, providerRoleCheck = { pending.await(); null })
        } }
        compose.onNodeWithText("Search skills").performTextInput("halo_open_app")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Available").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Checking").assertDoesNotExist()
    }
    @Test fun modelSetupLinksWorkAndSearchFindsTheActualAppSkill() {
        val registry = PreviewSkills()
        val opened = mutableListOf<String>()
        compose.setContent { HaloTheme(darkTheme = false) {
            SkillsScreen(registry, {}, onLocalModels = { opened += "local" }, onModelRoles = { opened += "roles" }, technical = true)
        } }
        compose.onNodeWithText("Allow all tools without asking").assertDoesNotExist()
        compose.onNodeWithText("On-device models").performScrollTo().performClick()
        compose.onNodeWithText("Model roles").performScrollTo().performClick()
        assertEquals(listOf("local", "roles"), opened)
        compose.onNodeWithText("Search skills").performTextInput("halo_open_app")
        compose.onNodeWithText("Models for your skills").assertDoesNotExist()
        compose.onNodeWithText(registry.get(SkillId("halo.apps"))!!.name).assertIsDisplayed()
    }
}
