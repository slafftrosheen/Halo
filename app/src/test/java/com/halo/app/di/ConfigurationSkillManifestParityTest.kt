package com.halo.app.di

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.halo.runtime.cognition.skills.DefaultSkillRegistry
import com.halo.runtime.cognition.tools.PersonalConfigurationToolAccess
import com.halo.runtime.cognition.tools.WorkspaceToolAccess
import com.halo.sdk.runtime.skills.SkillCategory
import com.halo.sdk.runtime.skills.SkillRisk
import com.halo.sdk.runtime.skills.SkillSource
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ConfigurationSkillManifestParityTest {
    @Test fun everyNewConfigurationSkillShipsItsCompleteRuntimeDeclaration() {
        val loader = BuiltInSkillManifestLoader(ApplicationProvider.getApplicationContext())
        val defaults = DefaultSkillRegistry.defaultBuiltInSkills()
        val bundled = loader.loadBundledManifests()
        val newSkills = setOf("halo.screen.layout", "halo.saved_devices", "halo.notification_settings",
            "halo.voice.configuration", "halo.learning.configuration", "halo.provider.configuration", "halo.speech.inventory")
        newSkills.forEach { id ->
            val expected = defaults.single { it.id.value == id }
            val actual = bundled.single { it.id.value == id }
            assertEquals(expected.schemaVersion, actual.schemaVersion)
            assertEquals(expected.name, actual.name)
            assertEquals(expected.description, actual.description)
            assertEquals(expected.category, actual.category)
            assertEquals(expected.risk, actual.risk)
            assertEquals(expected.tools, actual.tools)
            assertEquals(expected.requiredPermissions, actual.requiredPermissions)
            assertEquals(expected.toolDefinitions.toSet(), actual.toolDefinitions.toSet())
        }
        val workspace = defaults.single { it.id.value == "halo.notebook" }
        val bundledWorkspace = bundled.single { it.id == workspace.id }
        assertEquals(workspace.tools, bundledWorkspace.tools)
        assertEquals(workspace.tools, bundledWorkspace.toolDefinitions.map { it.toolId }.toSet())
        workspace.toolDefinitions.forEach { definition ->
            assertEquals(definition.risk, bundledWorkspace.toolDefinitions.single { it.toolId == definition.toolId }.risk)
        }
    }

    @Test fun configurationToolsKeepTheirUniqueOwnerRiskPermissionsAndAssetProvenance() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val loader = BuiltInSkillManifestLoader(context)
        val ownership = linkedMapOf(
            "halo.assistant" to PersonalConfigurationToolAccess.preferences,
            "halo.screen.layout" to PersonalConfigurationToolAccess.screenLayout,
            "halo.saved_devices" to PersonalConfigurationToolAccess.savedDevices,
            "halo.notification_settings" to PersonalConfigurationToolAccess.notificationSettings,
            "halo.voice.configuration" to PersonalConfigurationToolAccess.voice,
            "halo.learning.configuration" to PersonalConfigurationToolAccess.learning,
            "halo.provider.configuration" to PersonalConfigurationToolAccess.providers,
            "halo.automation" to PersonalConfigurationToolAccess.routines,
            "halo.study" to PersonalConfigurationToolAccess.activeStudy,
            "halo.speech.inventory" to PersonalConfigurationToolAccess.speechInventory,
            "halo.notebook" to WorkspaceToolAccess.canvasWorkflowTools + "halo_canvas_current",
        )
        val names = ownership.values.flatten()
        assertEquals("Review the manifest contract when adding configuration operations", 52, names.size)
        assertEquals(names.size, names.toSet().size)
        val readOnly = setOf(
            "halo_get_preferences", "halo_get_mode_profile", "halo_screen_state", "halo_screen_preview",
            "halo_saved_devices", "halo_saved_device_get", "halo_device_preferences",
            "halo_notification_settings_apps", "halo_notification_policy_get", "halo_digest_delivery_get",
            "halo_get_voice_configuration", "halo_get_learning_configuration", "halo_provider_catalog", "halo_provider_state",
            "halo_routines_list", "halo_routine_get", "halo_routine_run_status", "halo_study_current", "halo_speech_inventory",
            "halo_canvas_current", "halo_canvas_workflow_status",
        )
        val reversible = setOf(
            "halo_set_preferences", "halo_screen_apply", "halo_screen_undo", "halo_screen_save_preset", "halo_screen_remove_preset",
            "halo_device_thumbnail_quality", "halo_saved_device_autoconnect",
        )
        val destructive = setOf("halo_saved_device_forget", "halo_routine_delete")
        val hashes = ownership.keys.associateWith { owner ->
            val bytes = context.assets.open("skills/$owner.json").use { it.readBytes() }
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }
        }
        val phases = listOf(DefaultSkillRegistry.defaultBuiltInSkills(), loader.loadBundledManifests(), loader.load())
        phases.forEachIndexed { phase, skills ->
            ownership.forEach { (owner, tools) ->
                tools.forEach { name ->
                    val owners = skills.filter { name in it.tools }
                    assertEquals("phase=$phase tool=$name must have one owner", listOf(owner), owners.map { it.id.value })
                    val skill = owners.single()
                    assertEquals(SkillSource.BUILT_IN, skill.source)
                    assertEquals(when (owner) {
                        "halo.assistant" -> SkillCategory.DEVICE
                        "halo.automation" -> SkillCategory.AUTOMATION
                        else -> SkillCategory.WORKSPACE
                    }, skill.category)
                    val definition = skill.toolDefinitions.single { it.toolId == name }
                    val risk = when (name) {
                        in readOnly -> SkillRisk.READ_ONLY
                        in reversible -> SkillRisk.REVERSIBLE
                        in destructive -> SkillRisk.DESTRUCTIVE
                        "halo_routine_run_now" -> SkillRisk.EXTERNAL_SIDE_EFFECT
                        else -> SkillRisk.SENSITIVE
                    }
                    assertEquals("phase=$phase tool=$name", risk, definition.risk)
                    assertTrue("Configuration must not imply Android permission grants", skill.requiredPermissions.isEmpty())
                    assertTrue(definition.requiredPermissions.isEmpty())
                    if (phase > 0) {
                        val provenance = requireNotNull(skill.provenance)
                        assertEquals("asset:skills/$owner.json", provenance.sourceId)
                        assertEquals(hashes.getValue(owner), provenance.contentSha256)
                        assertTrue(provenance.loadedAtMs > 0)
                    }
                }
            }
        }
    }
}
