package com.halo.app.di

import androidx.test.core.app.ApplicationProvider
import com.halo.sdk.runtime.skills.SkillManifestPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BuiltInSkillManifestTest {
    @Test fun remoteInkPerToolRisksAgreeAcrossRuntimeBundledAndMergedManifests() {
        val loader = BuiltInSkillManifestLoader(ApplicationProvider.getApplicationContext())
        for (skills in listOf(com.halo.runtime.cognition.skills.DefaultSkillRegistry.defaultBuiltInSkills(), loader.loadBundledManifests(), loader.load())) {
            for (name in com.halo.runtime.cognition.tools.WorkspaceToolAccess.remoteInkTools) {
                val skill = skills.single { name in it.tools }
                assertEquals("halo.notebook", skill.id.value)
                assertEquals(when (name) {
                    "halo_ink_start" -> com.halo.sdk.runtime.skills.SkillRisk.SENSITIVE
                    "halo_ink_stop" -> com.halo.sdk.runtime.skills.SkillRisk.REVERSIBLE
                    else -> com.halo.sdk.runtime.skills.SkillRisk.READ_ONLY
                }, skill.toolDefinitions.single { it.toolId == name }.risk)
            }
        }
    }
    @Test fun canvasEditingParityKeepsPerToolRisksAcrossAllManifestPhases() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val loader = BuiltInSkillManifestLoader(context)
        val destructive = setOf("halo_canvas_delete", "halo_canvas_ungroup", "halo_canvas_disconnect")
        val names = destructive + setOf("halo_canvas_rename", "halo_canvas_add_group", "halo_canvas_tag", "halo_canvas_duplicate", "halo_canvas_arrange")
        for (skills in listOf(com.halo.runtime.cognition.skills.DefaultSkillRegistry.defaultBuiltInSkills(), loader.loadBundledManifests(), loader.load())) {
            for (name in names) {
                val skill = skills.single { name in it.tools }
                assertEquals("halo.notebook", skill.id.value)
                val definition = skill.toolDefinitions.single { it.toolId == name }
                assertEquals(if (name in destructive) com.halo.sdk.runtime.skills.SkillRisk.DESTRUCTIVE else com.halo.sdk.runtime.skills.SkillRisk.REVERSIBLE, definition.risk)
                assertTrue(definition.requiredPermissions.isEmpty())
            }
        }
    }
    @Test fun canvasClearHasDestructiveRiskInRuntimeBundledAndMergedWorkspaceSkill() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val loader = BuiltInSkillManifestLoader(context)
        val defaults = com.halo.runtime.cognition.skills.DefaultSkillRegistry.defaultBuiltInSkills()
        for (skills in listOf(defaults, loader.loadBundledManifests(), loader.load())) {
            val skill = skills.single { "halo_canvas_clear_sketch" in it.tools }
            assertEquals("halo.notebook", skill.id.value)
            assertEquals(com.halo.sdk.runtime.skills.SkillRisk.DESTRUCTIVE, skill.toolDefinitions.single { it.toolId == "halo_canvas_clear_sketch" }.risk)
            assertTrue(skill.toolDefinitions.single { it.toolId == "halo_canvas_clear_sketch" }.requiredPermissions.isEmpty())
        }
    }
    @Test
    fun modelSetupManifestPreservesEveryRegisteredOperationAndItsRisk() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val defaults = com.halo.runtime.cognition.skills.DefaultSkillRegistry.defaultBuiltInSkills()
            .single { it.id.value == "halo.models" }
        val bundled = BuiltInSkillManifestLoader(context).loadBundledManifests()
            .single { it.id == defaults.id }
        val merged = BuiltInSkillManifestLoader(context).load().single { it.id == defaults.id }
        val expectedRisks = mapOf(
            "halo_local_setup_status" to com.halo.sdk.runtime.skills.SkillRisk.READ_ONLY,
            "halo_local_setup_start" to com.halo.sdk.runtime.skills.SkillRisk.SENSITIVE,
            "halo_local_setup_pause" to com.halo.sdk.runtime.skills.SkillRisk.REVERSIBLE,
            "halo_local_setup_rollback" to com.halo.sdk.runtime.skills.SkillRisk.DESTRUCTIVE,
            "halo_local_setup_keep_files" to com.halo.sdk.runtime.skills.SkillRisk.REVERSIBLE,
        )
        for (skill in listOf(defaults, bundled, merged)) {
            assertEquals(expectedRisks.keys, skill.tools)
            assertEquals(expectedRisks, skill.toolDefinitions.associate { it.toolId to it.risk })
            assertTrue(skill.requiredPermissions.isEmpty())
            assertTrue(skill.toolDefinitions.all { it.requiredPermissions.isEmpty() })
        }
    }
    @Test
    fun everyBundledSkillManifestLoadsUnderTheApkPolicy() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val assetNames = context.assets.list("skills").orEmpty().filter { it.endsWith(".json") }.sorted()
        val manifests = BuiltInSkillManifestLoader(context).loadBundledManifests()

        assertTrue("The APK must bundle at least one skill manifest", assetNames.isNotEmpty())
        assertEquals(assetNames.size, manifests.size)
        manifests.forEach { manifest ->
            assertEquals(SkillManifestPolicy.CURRENT_SCHEMA_VERSION, manifest.schemaVersion)
            assertTrue(SkillManifestPolicy.validate(manifest).isEmpty())
        }

        val manifestIds = manifests.map { it.id }.toSet()
        val defaultIds = com.halo.runtime.cognition.skills.DefaultSkillRegistry.defaultBuiltInSkills()
            .map { it.id }
            .toSet()
        assertEquals("Every Runtime built-in skill must ship a manifest", defaultIds, manifestIds)
    }
    @Test
    fun mergingBuiltInsPreservesPerToolPermissions() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val loaded = BuiltInSkillManifestLoader(context).load()
        val calendar = loaded.single { it.id.value == "halo.calendar" }
        assertEquals(setOf(com.halo.sdk.runtime.skills.HostPermission.CALENDAR_READ),
            calendar.toolDefinitions.single { it.toolId == "halo_calendar_upcoming" }.requiredPermissions)
        assertEquals(setOf(com.halo.sdk.runtime.skills.HostPermission.CALENDAR_WRITE),
            calendar.toolDefinitions.single { it.toolId == "halo_calendar_create" }.requiredPermissions)
        assertEquals(emptySet<com.halo.sdk.runtime.skills.HostPermission>(),
            calendar.toolDefinitions.single { it.toolId == "halo_calendar_editor" }.requiredPermissions)
        val contacts = loaded.single { it.id.value == "halo.contacts" }
        assertEquals(setOf(com.halo.sdk.runtime.skills.HostPermission.CONTACTS),
            contacts.toolDefinitions.single { it.toolId == "halo_find_contact" }.requiredPermissions)
        assertEquals(emptySet<com.halo.sdk.runtime.skills.HostPermission>(),
            contacts.toolDefinitions.single { it.toolId == "halo_contact_editor" }.requiredPermissions)
        val alarms = loaded.single { it.id.value == "halo.alarms" }
        assertTrue(alarms.toolDefinitions.any { it.toolId == "halo_clock_view" })
        assertTrue(alarms.toolDefinitions.any { it.toolId == "halo_clock_modify" })
        val notifications = loaded.single { it.id.value == "halo.notifications" }
        assertEquals(setOf(com.halo.sdk.runtime.skills.HostPermission.NOTIFICATION_ACCESS),
            notifications.toolDefinitions.single { it.toolId == "halo_dismiss_notification" }.requiredPermissions)

        val device = loaded.single { it.id.value == "halo.device" }
        assertEquals(setOf(com.halo.sdk.runtime.skills.HostPermission.CAMERA),
            device.toolDefinitions.single { it.toolId == "halo_flashlight" }.requiredPermissions)
        assertEquals(setOf(com.halo.sdk.runtime.skills.HostPermission.WRITE_SETTINGS),
            device.toolDefinitions.single { it.toolId == "halo_brightness" }.requiredPermissions)

        val phoneActions = loaded.single { it.id.value == "halo.phone.actions" }
        assertEquals(setOf(com.halo.sdk.runtime.skills.HostPermission.CONTACTS),
            phoneActions.toolDefinitions.single { it.toolId == "halo_find_contact" }.requiredPermissions)
        assertEquals(setOf(com.halo.sdk.runtime.skills.HostPermission.WRITE_SETTINGS),
            phoneActions.toolDefinitions.single { it.toolId == "halo_brightness" }.requiredPermissions)
    }

    @Test
    fun permissionPlannerCoversToolOnlyAndSpecialAndroidAccess() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val checker = AndroidSkillPermissionChecker(context)
        val planned = checker.accessPlan(
            declaredPermissions = emptySet(),
            toolIds = setOf("halo_brightness", "halo_ringer", "halo_read_screen", "halo_create_watch"),
        )
        assertTrue(com.halo.sdk.runtime.skills.HostPermission.WRITE_SETTINGS in planned)
        assertTrue(com.halo.sdk.runtime.skills.HostPermission.NOTIFICATION_POLICY in planned)
        assertTrue(com.halo.sdk.runtime.skills.HostPermission.ACCESSIBILITY in planned)
        assertTrue(com.halo.sdk.runtime.skills.HostPermission.NOTIFICATIONS in planned)
    }

}
