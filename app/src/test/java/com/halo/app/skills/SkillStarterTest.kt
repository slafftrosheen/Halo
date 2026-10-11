package com.halo.app.skills

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.halo.app.di.BuiltInSkillManifestLoader
import com.halo.sdk.runtime.skills.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class SkillStarterTest {
    private fun source(starter: SkillStarter) = BuiltInSkillManifestLoader(ApplicationProvider.getApplicationContext()).load()
        .first { it.id.value == starter.builtInId }
    @Test fun startersProduceReviewedDisabledPackagesUsingExistingTools() {
        SkillStarter.entries.forEach { starter ->
            val source = source(starter)
            val parsed = SkillMarkdownPackageParser.parse("starter", starter.packageBytes("My helper", "My exact instructions", source, "custom.myhelper"))
            assertFalse(parsed.descriptor.enabled)
            assertEquals(source.tools, parsed.descriptor.tools)
            assertTrue(parsed.descriptor.risk.ordinal >= source.risk.ordinal)
            assertTrue(parsed.descriptor.requiredPermissions.containsAll(source.requiredPermissions + source.toolDefinitions.flatMap { it.requiredPermissions }))
            assertEquals(source.requirements, parsed.descriptor.requirements)
            assertEquals("My exact instructions", parsed.instruction.body)
            assertTrue(starter.instructions.isNotBlank())
        }
    }
    @Test fun namesCannotInjectManifestFieldsAndEmptyInstructionsAreRejected() {
        val source = source(SkillStarter.APPS)
        assertTrue(runCatching { SkillStarter.APPS.packageBytes("name\ntools: unexpected_tool", "Instructions", source) }.isFailure)
        assertTrue(runCatching { SkillStarter.APPS.packageBytes("My helper", " ", source) }.isFailure)
        assertTrue(runCatching { SkillStarter.APPS.packageBytes("My helper", "Instructions", source.copy(source = SkillSource.CUSTOM)) }.isFailure)
    }
}
