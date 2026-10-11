package com.halo.app.skills

import android.content.ContextWrapper
import com.halo.host.android.AndroidStorage
import com.halo.runtime.cognition.skills.DefaultSkillRegistry
import androidx.test.core.app.ApplicationProvider
import com.halo.sdk.runtime.skills.SkillId
import com.halo.sdk.runtime.skills.SkillManifestPolicy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
// API 28's backup-based AtomicFile works on Windows; newer Android rename-over-existing assumes POSIX.
@Config(sdk = [28])
class CustomSkillPackageRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun repository(): Pair<CustomSkillPackageRepository, File> {
        val root = temporary.newFolder()
        val context = object : ContextWrapper(ApplicationProvider.getApplicationContext()) {
            override fun getFilesDir() = root
        }
        return CustomSkillPackageRepository(context) to File(root, "skills")
    }
    private fun packageBytes(id: String = "demo.notes", schema: String = "2", body: String = "Read the chosen note.") = """
        ---
        id: $id
        name: Note helper
        description: Read a local note
        category: WORKSPACE
        risk: READ_ONLY
        tools: halo_get_workspace_object
        schemaVersion: $schema
        ---
        $body
    """.trimIndent().toByteArray()

    @Test fun importedPackagesStayDisabledAndInvalidReplacementPreservesPreviousContent() = runBlocking {
        val (repository, _) = repository()
        assertFalse(repository.importPackage("SKILL.md", packageBytes()).descriptor.enabled)
        assertTrue(runCatching { repository.importPackage("SKILL.md", packageBytes(schema = "invalid")) }.isFailure)
        repository.refresh()
        assertEquals("Read the chosen note.", repository.load(SkillId("demo.notes"))?.body)
    }
    @Test fun customPackageCannotShadowBuiltInSkill() {
        assertTrue(runCatching { SkillMarkdownPackageParser.parse("import", packageBytes(id = "halo.notebook")) }.isFailure)
    }
    @Test fun replacingEnabledPackageRequiresFreshEnablementAndSurvivesRestart() = runBlocking {
        val (repository, _) = repository()
        val storage = AndroidStorage(ApplicationProvider.getApplicationContext(), "skill-import-${temporary.root.name}")
        val registry = DefaultSkillRegistry(storage, initialBuiltIns = emptyList())
        val id = SkillId("demo.notes")
        assertTrue(installCustomSkillPackage(repository, storage, registry, "SKILL.md", packageBytes()).isSuccess)
        registry.setEnabled(id, true)
        assertTrue(registry.get(id)!!.enabled)
        assertTrue(installCustomSkillPackage(repository, storage, registry, "SKILL.md", packageBytes(schema = "invalid")).isFailure)
        assertTrue(registry.get(id)!!.enabled)
        assertTrue(installCustomSkillPackage(repository, storage, registry, "SKILL.md", packageBytes(body = "Updated instructions.")).isSuccess)
        assertFalse(registry.get(id)!!.enabled)
        assertEquals("Updated instructions.", repository.load(id)?.body)
        assertFalse(DefaultSkillRegistry(storage, initialBuiltIns = repository.refresh()).get(id)!!.enabled)
    }
    @Test fun oversizedOnDiskPackageIsRejectedWithoutPublishingInstructions() = runBlocking {
        val (repository, directory) = repository()
        directory.mkdirs()
        File(directory, "large.md").writeBytes(ByteArray(SkillManifestPolicy.MAX_MANIFEST_BYTES + 1))
        assertTrue(repository.refresh().isEmpty())
        assertEquals(1, repository.issues.value.size)
        assertNull(repository.load(SkillId("demo.notes")))
    }
    @Test fun duplicateIdsAreReportedInsteadOfSilentlyOverriding() {
        val (repository, directory) = repository()
        directory.mkdirs()
        File(directory, "one.md").writeBytes(packageBytes())
        File(directory, "two.md").writeBytes(packageBytes(body = "Different instructions."))
        assertTrue(repository.refresh().isEmpty())
        assertTrue(repository.issues.value.single().message.contains("Duplicate"))
    }
    @Test fun interruptedLegacyWriteRecoversTheAtomicBackup() = runBlocking {
        val (repository, directory) = repository()
        repository.importPackage("SKILL.md", packageBytes())
        val base = File(directory, "demo.notes.md")
        assertTrue(base.renameTo(File(directory, "demo.notes.md.bak")))
        assertEquals(1, repository.refresh().size)
        assertEquals("Read the chosen note.", repository.load(SkillId("demo.notes"))?.body)
    }
}
