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
