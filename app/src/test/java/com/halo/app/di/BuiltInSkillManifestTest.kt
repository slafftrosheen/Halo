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
}
