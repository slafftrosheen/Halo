package com.halo.app.data.workspace

import com.halo.app.data.notebook.*
import com.halo.app.di.tools.*
import com.halo.runtime.cognition.skills.DefaultSkillRegistry
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class NotebookAttachmentControlsTest {
    private class Fixture(val f: WorkspaceFileToolsFixture) {
        val assets = NotebookAssetRepository(f.context, f.database)
        val actions = WorkspaceNoteAttachmentActions(assets)
        suspend fun note() = f.notes.createNote("Saved source").id
        suspend fun image(noteId: String, key: String = UUID.randomUUID().toString(), type: String = "IMAGE", storage: String = "MANAGED_FILE"): NoteAttachment {
            val file = File(f.root, key).also { it.writeText("Fixture") }
            f.database.workspaceAssetDao().insertAsset(WorkspaceAssetEntity(key, noteId, type, "image/png", storage, file.path, true, "Image", 7, 1, 1, null, 1))
            f.database.workspaceAssetDao().insertAttachment(NoteAttachmentEntity("link-$key", noteId, key, 0, null, 1))
            return requireNotNull(f.database.workspaceAssetDao().attachment(noteId, "link-$key"))
        }
        suspend fun call(name: String, args: Map<String, Any>) = actions.execute(ToolCall(UUID.randomUUID().toString(), name, args))
        suspend fun state(id: String) = json(call("halo_note_attachments", mapOf("noteId" to id)))
        suspend fun version(id: String) = state(id).getString("version")
        suspend fun reuse(target: String, source: String, attachment: String, targetVersion: String? = null, sourceVersion: String? = null) =
            call("halo_note_reuse_image", mapOf("noteId" to target, "version" to (targetVersion ?: version(target)), "sourceNoteId" to source, "sourceVersion" to (sourceVersion ?: version(source)), "attachmentId" to attachment))
    }
    private fun fixture(block: suspend Fixture.() -> Unit) = runBlocking { WorkspaceFileToolsFixture().use { Fixture(it).block() } }
    companion object {
        private fun json(result: ToolExecutionResult): JSONObject {
            assertTrue(result.toString(), result is ToolExecutionResult.Success)
            return JSONObject((result as ToolExecutionResult.Success).output)
        }
    }

    @Test fun registrationContractsRisksAndPrivacyAreOwnedByNotebookAssets() = fixture {
        val executor = WorkspaceToolExecutor(f.notes, f.workspace, f.contextOwner(), noteAttachmentActions = actions)
        val definitions = actions.definitions(); assertEquals(3, definitions.size); assertTrue(executor.definitions().containsAll(definitions))
        val descriptors = definitions.map { requireNotNull(HaloToolDescriptors.workspace(it)) }
        assertTrue(ToolRegistry(listOf(executor), descriptors).operabilityReport(listOf(ToolFeatureOperability("attachments", descriptors.map { it.name }.toSet()))).isEmpty())
        val skill = DefaultSkillRegistry.defaultBuiltInSkills().single { it.id.value == "halo.notebook" }
        val bundle = JSONObject(f.context.assets.open("skills/halo.notebook.json").bufferedReader().use { it.readText() }).getJSONArray("toolDefinitions")
        for (descriptor in descriptors) {
            assertEquals("NotebookAssetRepository", descriptor.operability!!.owner)
            assertEquals(false, descriptor.definition.parameters["additionalProperties"])
            assertEquals(false, descriptor.operability!!.resultSchema["additionalProperties"])
            assertTrue(skill.tools.contains(descriptor.name))
            val risk = skill.toolDefinitions.single { it.toolId == descriptor.name }.risk.name
            assertEquals(risk, (0 until bundle.length()).map { bundle.getJSONObject(it) }.single { it.getString("toolId") == descriptor.name }.getString("risk"))
            val policy = PolicyToolAuthorizer(defaultPolicyProvider = { ToolApprovalPolicy.ALWAYS_ALLOW }, descriptorProvider = { descriptor })
            val authorization = policy.authorize(ToolCall("policy", descriptor.name, emptyMap()))
            if (descriptor.name == "halo_note_unlink_attachment") assertTrue(authorization is ToolAuthorization.Rejected)
            else assertTrue(authorization is ToolAuthorization.Authorized)
            assertEquals(if (descriptor.name == "halo_note_attachments") ReplaySafety.SAFE else ReplaySafety.NEVER_REPLAY, descriptor.security.replaySafety)
        }
    }

    @Test fun reuseSavesAnActualTargetAttachmentAndDuplicateIsAnUnchangedReceipt() = fixture {
        val source = note(); val target = note(); val image = image(source)
        val first = json(reuse(target, source, image.id))
        assertTrue(first.getBoolean("changed")); assertTrue(first.getBoolean("saved"))
        val attachment = requireNotNull(f.database.workspaceAssetDao().attachment(target, first.getString("attachmentId")))
        assertEquals(image.pathOrUri, attachment.pathOrUri); assertNotEquals(image.assetId, attachment.assetId)
        assertEquals(first.getString("version"), version(target)); assertEquals(1, assets.observe(target).first().size)
        val duplicate = json(reuse(target, source, image.id))
        assertFalse(duplicate.getBoolean("changed")); assertEquals(attachment.id, duplicate.getString("attachmentId"))
        assertEquals(first.getString("version"), duplicate.getString("version")); assertEquals(1, assets.observe(target).first().size)
    }

    @Test fun changedTargetOrSourceCannotConsumeAnOldApproval() = fixture {
        val source = note(); val target = note(); val image = image(source)
        val beforeSource = version(source); val beforeTarget = version(target)
        f.notes.renameNote(target, "New title")
        assertTrue(reuse(target, source, image.id, beforeTarget, beforeSource) is ToolExecutionResult.Rejected)
        f.database.openHelper.writableDatabase.execSQL("UPDATE workspace_assets SET displayName='Changed' WHERE id=?", arrayOf(image.assetId))
        assertTrue(reuse(target, source, image.id, version(target), beforeSource) is ToolExecutionResult.Rejected)
        assertTrue(assets.observe(target).first().isEmpty())
    }

    @Test fun unlinkRetainsOwnershipForLaterPurgeAndCannotReplayAsSuccess() = fixture {
        val id = note(); val attached = image(id); val old = version(id)
        val result = json(call("halo_note_unlink_attachment", mapOf("noteId" to id, "version" to old, "attachmentId" to attached.id)))
        assertTrue(result.getBoolean("changed")); assertFalse(result.getBoolean("fileCleanupPerformed"))
        assertTrue(assets.observe(id).first().isEmpty()); assertTrue(File(attached.pathOrUri).exists())
        assertEquals(1, f.database.notebookDao().getAssetsForObject(id).size)
        assertTrue(call("halo_note_unlink_attachment", mapOf("noteId" to id, "version" to old, "attachmentId" to attached.id)) is ToolExecutionResult.Rejected)
        f.notes.setLifecycle(id, WorkspaceLifecycle.TRASHED)
        val owner = NotebookPurgeOwner(f.database, f.root)
        assertEquals("COMPLETE", (owner.purge(id) as NotebookPurgeResult.Saved).receipt.record.state)
        assertFalse(File(attached.pathOrUri).exists())
    }

    @Test fun editorRemovalUsesTheSameOwnershipRetentionAndSharedReadersStayReadable() = fixture {
        val source = note(); val target = note(); val attached = image(source)
        json(reuse(target, source, attached.id))
        assertTrue(assets.remove(source, attached.id))
        assertEquals(1, f.database.notebookDao().getAssetsForObject(source).size)
        assertFalse(assets.remove(source, attached.id))
        f.notes.setLifecycle(source, WorkspaceLifecycle.TRASHED)
        val receipt = (NotebookPurgeOwner(f.database, f.root).purge(source) as NotebookPurgeResult.Saved).receipt
        assertEquals(1, receipt.record.sharedRetained); assertTrue(File(attached.pathOrUri).exists())
        assertEquals(1, assets.observe(target).first().size)
        assertTrue(runCatching { assets.attachExistingImage(note(), attached) }.isFailure)
    }

    @Test fun recoveryDraftRefusesAssistantWritesWhileExistingEditorRemovalStillWorks() = fixture {
        val source = note(); val target = note(); val attached = image(source); val own = image(target)
        f.notes.saveDraft(target, "Private recovery")
        assertTrue(state(target).getBoolean("hasRecoveryDraft"))
        assertTrue(reuse(target, source, attached.id) is ToolExecutionResult.Rejected)
        assertTrue(call("halo_note_unlink_attachment", mapOf("noteId" to target, "version" to version(target), "attachmentId" to own.id)) is ToolExecutionResult.Rejected)
        assertTrue(assets.remove(target, own.id)); assertEquals("Private recovery", f.notes.getDraft(target)!!.body)
    }

    @Test fun trashedMissingUnsupportedAndUnavailableSourcesNeverAttach() = fixture {
        val target = note(); val source = note(); val attached = image(source)
        f.notes.setLifecycle(source, WorkspaceLifecycle.TRASHED)
        assertTrue(reuse(target, source, attached.id) is ToolExecutionResult.Rejected)
        f.notes.setLifecycle(source, WorkspaceLifecycle.ACTIVE)
        File(attached.pathOrUri).delete()
        assertTrue(reuse(target, source, attached.id) is ToolExecutionResult.Rejected)
        val external = image(source, type = "IMAGE", storage = "EXTERNAL_URI")
        assertTrue(reuse(target, source, external.id) is ToolExecutionResult.Rejected)
        val audio = image(source, type = "AUDIO")
        assertTrue(reuse(target, source, audio.id) is ToolExecutionResult.Rejected)
        f.notes.setLifecycle(target, WorkspaceLifecycle.TRASHED)
        assertTrue(reuse(target, source, audio.id) is ToolExecutionResult.Rejected)
        assertTrue(call("halo_note_attachments", mapOf("noteId" to "missing")) is ToolExecutionResult.Rejected)
        assertTrue(assets.observe(target).first().isEmpty())
    }

    @Test fun metadataPagingIsBoundedAndCannotMixVersionsOrExposePaths() = fixture {
        val id = note(); repeat(10) { image(id, "asset-$it") }
        val first = state(id)
        assertEquals(8, first.getJSONArray("attachments").length()); assertEquals(8, first.getInt("nextOffset"))
        assertFalse(first.toString().contains(f.root.path)); assertFalse(first.toString().contains("Saved source"))
        val args = mapOf("noteId" to id, "offset" to 8, "version" to first.getString("version"))
        assertEquals(2, json(call("halo_note_attachments", args)).getJSONArray("attachments").length())
        assertTrue(call("halo_note_attachments", mapOf("noteId" to id, "offset" to 8)) is ToolExecutionResult.Rejected)
        image(id)
        assertTrue(call("halo_note_attachments", args) is ToolExecutionResult.Rejected)
    }

    @Test fun invalidFieldsAndForeignAttachmentIdsNeverMutate() = fixture {
        val source = note(); val target = note(); val attached = image(source)
        assertTrue(call("halo_note_reuse_image", mapOf("noteId" to target, "version" to version(target), "sourceNoteId" to source,
            "sourceVersion" to version(source), "attachmentId" to attached.id, "uri" to "file:///untrusted")) is ToolExecutionResult.Rejected)
        assertTrue(call("halo_note_unlink_attachment", mapOf("noteId" to target, "version" to version(target), "attachmentId" to attached.id)) is ToolExecutionResult.Rejected)
        assertEquals(1, assets.observe(source).first().size); assertTrue(assets.observe(target).first().isEmpty())
    }

    @Test fun maximallyEscapedMetadataFitsTheDeclaredReceiptBudget() = fixture {
        val id = note(); repeat(8) { image(id, "escaped-$it") }
        f.database.openHelper.writableDatabase.execSQL("UPDATE workspace_assets SET displayName=?,mimeType=?", arrayOf("\u0000".repeat(1000), "\u0000".repeat(1000)))
        f.database.openHelper.writableDatabase.execSQL("UPDATE note_attachments SET caption=?", arrayOf("\u0000".repeat(1000)))
        val result = state(id)
        assertTrue(result.toString().length <= 24000)
        assertTrue(result.getJSONArray("attachments").getJSONObject(0).getBoolean("metadataTruncated"))
    }

    @Test fun legacyReadsDoNotInitializeBlocksAndDraftChangesInvalidateTheObservedSet() = fixture {
        val source = note(); val target = note(); val image = image(source)
        assertEquals(0, f.database.notebookDao().countBlocks(target))
        val old = version(target); assertEquals(0, f.database.notebookDao().countBlocks(target))
        f.notes.saveDraft(target, "Changed draft")
        assertNotEquals(old, version(target))
        assertTrue(reuse(target, source, image.id, old) is ToolExecutionResult.Rejected)
        assertEquals("Saved source", f.notes.getNote(target)!!.body)
    }

    @Test fun archiveAndSpecializedDocumentBoundariesMatchExistingOwners() = fixture {
        val source = note(); val target = note(); val attached = image(source)
        f.notes.setLifecycle(source, WorkspaceLifecycle.ARCHIVED)
        json(reuse(target, source, attached.id))
        f.notes.setLifecycle(target, WorkspaceLifecycle.ARCHIVED)
        assertTrue(reuse(target, source, attached.id) is ToolExecutionResult.Rejected)
        val own = assets.observe(target).first().single()
        json(call("halo_note_unlink_attachment", mapOf("noteId" to target, "version" to version(target), "attachmentId" to own.id)))
        f.database.openHelper.writableDatabase.execSQL("UPDATE workspace_objects SET type='FILE' WHERE id=?", arrayOf(source))
        assertTrue(call("halo_note_attachments", mapOf("noteId" to source)) is ToolExecutionResult.Rejected)
        val specialized = f.notes.createNote("```halo-file\n{}\n```").id
        assertTrue(call("halo_note_attachments", mapOf("noteId" to specialized)) is ToolExecutionResult.Rejected)
        assertEquals(0, f.database.notebookDao().countBlocks(specialized))
    }

    @Test fun unlinkedCleanupOnlyAssetsDoNotTurnTextNotesIntoCanvasImageCards() = fixture {
        val note = note(); val image = image(note); val board = f.canvases.create()
        val before = f.canvases.addReference(board, note, 0f, 0f)
        assertEquals(CanvasNodeType.IMAGE, f.canvases.observe(board).first()!!.nodes.single { it.id == before }.type)
        assertTrue(assets.remove(note, image.id)); assertEquals(1, f.database.notebookDao().getAssetsForObject(note).size)
        val after = f.canvases.addReference(board, note, 10f, 10f)
        assertEquals(CanvasNodeType.NOTE, f.canvases.observe(board).first()!!.nodes.single { it.id == after }.type)
    }

    @Test fun failedCanonicalAttachmentInsertRollsBackItsNewAssetAndKeepsObservedVersions() = fixture {
        val source = note(); val target = note(); val image = image(source)
        val before = version(target)
        f.database.openHelper.writableDatabase.execSQL("CREATE TRIGGER block_fixture_link BEFORE INSERT ON note_attachments WHEN NEW.noteId='$target' BEGIN SELECT RAISE(ABORT,'Fixture failure'); END")
        assertTrue(reuse(target, source, image.id) is ToolExecutionResult.Rejected)
        assertEquals(before, version(target)); assertTrue(f.database.notebookDao().getAssetsForObject(target).isEmpty())
        assertEquals(1, assets.observe(source).first().size); assertTrue(File(image.pathOrUri).exists())
    }
}
