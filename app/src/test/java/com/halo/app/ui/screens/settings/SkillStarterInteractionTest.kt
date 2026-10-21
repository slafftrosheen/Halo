package com.halo.app.ui.screens.settings

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.halo.app.skills.SkillMarkdownPackageParser
import com.halo.app.ui.theme.HostlessHaloTheme as HaloTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.test.core.app.ApplicationProvider
import com.halo.app.di.BuiltInSkillManifestLoader
import com.halo.host.android.AndroidStorage
import com.halo.runtime.cognition.skills.DefaultSkillRegistry
import com.github.takahirom.roborazzi.captureRoboImage

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class, qualifiers = "w400dp-h900dp")
class SkillStarterInteractionTest {
    @get:Rule val compose = createComposeRule()
    @Test fun editingAnAppStarterOnlyProducesAReviewablePackage() {
        var bytes: ByteArray? = null
        val context: android.content.Context = ApplicationProvider.getApplicationContext()
        val registry = DefaultSkillRegistry(AndroidStorage(context, "starter-ui-test"), BuiltInSkillManifestLoader(context).load())
        compose.setContent { HaloTheme(darkTheme = false) {
            SkillsScreen(registry, {}, onInstallCustomSkill = { _, value -> bytes = value; Result.success(Unit) }, technical = true)
        } }
        compose.onNodeWithText("Create a skill").performScrollTo().performClick()
        compose.onRoot().captureRoboImage()
        assertNull(bytes)
        compose.onNodeWithText("Phone app helper").performClick()
        compose.onNodeWithText("Skill name").performScrollTo().performTextClearance()
        compose.onNodeWithText("Skill name").performTextInput("My app helper")
        compose.onNodeWithText("Instructions").performScrollTo().performTextClearance()
        compose.onNodeWithText("Instructions").performTextInput("Discover the installed app before opening it.")
        compose.onNodeWithText("Review skill").performClick()
        assertNull(bytes)
        compose.onNodeWithText("Install My app helper?").assertIsDisplayed()
        compose.onNodeWithText("Install").performClick()
        compose.waitUntil(5_000) { bytes != null }
        val parsed = SkillMarkdownPackageParser.parse("review", bytes!!)
        assertEquals("My app helper", parsed.descriptor.name)
        assertEquals("Discover the installed app before opening it.", parsed.instruction.body)
        assertTrue("halo_open_app" in parsed.descriptor.tools)
        assertFalse(parsed.descriptor.enabled)
    }
}
