package com.halo.app.data.workspace

import com.halo.app.data.notebook.*
import com.halo.app.di.tools.*
import com.halo.app.notebook.NotebookUseCases
import com.halo.runtime.cognition.skills.DefaultSkillRegistry
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class WorkspaceNoteActionsTest {
    private class Fixture(val f: WorkspaceFileToolsFixture) {
        val context = f.contextOwner()
        val actions = WorkspaceNoteActions(f.database, f.documents, NotebookUseCases(f.notes), context)
        suspend fun create(text: String = "First paragraph\n\nSecond paragraph"): String = f.notes.createNote(text).id.also { state(it) }
        suspend fun state(id: String) = json(call("halo_note_get", mapOf("noteId" to id)))
        suspend fun args(id: String): Map<String, Any> = mapOf("noteId" to id, "version" to state(id).getString("version"))
        suspend fun document(id: String) = requireNotNull(f.documents.load(id))
        suspend fun call(name: String, args: Map<String, Any>) = actions.execute(ToolCall(UUID.randomUUID().toString(), name, args))
        suspend fun blockArgs(id: String, blockId: String? = null) = args(id) + ("blockId" to (blockId ?: document(id).blocks.first().id))
    }
    private fun fixture(block: suspend Fixture.() -> Unit) = runBlocking { WorkspaceFileToolsFixture().use { Fixture(it).block() } }
    companion object {
        private fun json(result: ToolExecutionResult): JSONObject {
            assertTrue(result.toString(), result is ToolExecutionResult.Success)
            return JSONObject((result as ToolExecutionResult.Success).output)
        }
    }

    @Test fun registrationContractsBundledRisksAndDestructivePolicyMatchActualOwners() = fixture {
        val definitions = actions.definitions()
        assertEquals(18, definitions.size)
        val executor = WorkspaceToolExecutor(f.notes, f.workspace, context, noteActions = actions)
        assertTrue(executor.definitions().containsAll(definitions))
        val descriptors = definitions.map { requireNotNull(HaloToolDescriptors.workspace(it)) }
        val registry = ToolRegistry(listOf(executor), descriptors)
        assertTrue(registry.operabilityReport(listOf(ToolFeatureOperability("notes", descriptors.map { it.name }.toSet()))).isEmpty())
        val skill = DefaultSkillRegistry.defaultBuiltInSkills().single { it.id.value == "halo.notebook" }
        val bundled = JSONObject(f.context.assets.open("skills/halo.notebook.json").bufferedReader().use { it.readText() }).getJSONArray("toolDefinitions")
        val bundledRisks = (0 until bundled.length()).associate { bundled.getJSONObject(it).let { row -> row.getString("toolId") to row.getString("risk") } }
        for (descriptor in descriptors) {
            val read = descriptor.name in setOf("halo_note_get", "halo_note_read_block")
            val destructive = descriptor.name in setOf("halo_note_delete_block", "halo_note_replace_content", "halo_note_trash")
            assertEquals(bundledRisks[descriptor.name], skill.toolDefinitions.single { it.toolId == descriptor.name }.risk.name)
            assertEquals(if (read) ReplaySafety.SAFE else ReplaySafety.NEVER_REPLAY, descriptor.security.replaySafety)
            assertEquals(CapabilityId(if (read) "workspace.read" else "workspace.write"), descriptor.operability!!.capabilityId)
            assertTrue((descriptor.operability!!.resultSchema["required"] as List<*>).containsAll(listOf("noteId", "version", "revision")))
            assertEquals(false, descriptor.definition.parameters["additionalProperties"])
            val policy = PolicyToolAuthorizer(defaultPolicyProvider = { ToolApprovalPolicy.ALWAYS_ALLOW }, descriptorProvider = { descriptor })
            val authorization = policy.authorize(ToolCall("policy", descriptor.name, emptyMap()))
            if (destructive) {
                assertEquals(ConfirmationPolicy.ALWAYS, descriptor.security.confirmation)
                assertTrue(authorization is ToolAuthorization.Rejected)
            } else assertTrue(authorization is ToolAuthorization.Authorized)
        }
    }

    @Test fun readingLegacyNoteInitializesStableBlocksWithoutChangingCommittedRevisionOrText() = fixture {
        val note = f.notes.createNote("Literal **material**\n\n- [ ] Task")
        val first = state(note.id); val blocks = document(note.id).blocks
        assertEquals(note.revision.toLong(), document(note.id).revision)
        assertEquals(first.getString("version"), state(note.id).getString("version"))
        assertEquals(blocks, document(note.id).blocks)
        assertEquals(NoteBlockType.CHECKLIST, blocks.last().type)
        assertEquals("Literal **material**", blocks.first().text)
    }

    @Test fun renameUsesCanonicalRevisionAndRejectsAStaleOrConcurrentEditorWrite() = fixture {
        val id = create(); val old = args(id); val before = document(id)
        val result = json(call("halo_note_rename", old + ("title" to "My title")))
        assertEquals(before.revision + 1, result.getLong("revision"))
        assertEquals(before.blocks, document(id).blocks)
        assertEquals("My title", document(id).title)
        assertTrue(call("halo_note_rename", old + ("title" to "Stale title")) is ToolExecutionResult.Rejected)
        val observed = args(id)
        f.documents.apply(id, document(id).revision, DocumentCommand.SetText(before.blocks.first().id, "New UI edit"))
        assertTrue(call("halo_note_edit_block", observed + mapOf("blockId" to before.blocks.first().id, "text" to "Stale overwrite")) is ToolExecutionResult.Rejected)
        assertEquals("New UI edit", document(id).blocks.first().text)
    }

    @Test fun insertAndPartialEditPreserveOtherBlocksAndOmittedChecklistFields() = fixture {
        val id = create(); val original = document(id).blocks
        val result = json(call("halo_note_insert_block", args(id) + mapOf("afterBlockId" to original.first().id, "blockType" to "CHECKLIST", "text" to "Task")))
        val inserted = result.getJSONArray("blockIds").getString(0)
        json(call("halo_note_edit_block", blockArgs(id, inserted) + ("checked" to true)))
        json(call("halo_note_edit_block", blockArgs(id, inserted) + ("text" to "Edited task")))
        val updated = document(id).blocks
        assertEquals(original.first(), updated.first()); assertEquals(original.last().text, updated.last().text)
        assertTrue(updated[1].checked); assertEquals("Edited task", updated[1].text)
        assertEquals(inserted, updated[1].id)
        json(call("halo_note_edit_block", blockArgs(id, inserted) + mapOf("blockType" to "CODE", "language" to "kotlin", "text" to "val x = 1")))
        assertEquals("kotlin", document(id).blocks[1].language)
        json(call("halo_note_edit_block", blockArgs(id, inserted) + ("language" to "")))
        assertNull(document(id).blocks[1].language)
    }

    @Test fun moveAndIndentUseCanonicalListBoundsWithoutSilentlyClamping() = fixture {
        val id = create("- First\n- Second"); val item = document(id).blocks.first()
        json(call("halo_note_move_block", blockArgs(id, item.id) + ("delta" to 1)))
        assertEquals(item.id, document(id).blocks.last().id)
        assertTrue(call("halo_note_move_block", blockArgs(id, item.id) + ("delta" to 1)) is ToolExecutionResult.Rejected)
        json(call("halo_note_indent_block", blockArgs(id, item.id) + ("delta" to 1)))
        assertEquals(1, document(id).blocks.last().indent)
        assertTrue(call("halo_note_indent_block", blockArgs(id, item.id) + ("delta" to 0)) is ToolExecutionResult.Rejected)
        json(call("halo_note_indent_block", blockArgs(id, item.id) + ("delta" to -1)))
        assertTrue(call("halo_note_indent_block", blockArgs(id, item.id) + ("delta" to -1)) is ToolExecutionResult.Rejected)
    }

    @Test fun splitMergeDuplicateAndDeleteShareEditorSemanticsAndStableAnchors() = fixture {
        val id = create("Hello world"); val original = document(id).blocks.first().id
        val split = json(call("halo_note_split_block", blockArgs(id, original) + ("offset" to 5)))
        val second = split.getJSONArray("blockIds").getString(0)
        assertEquals(listOf("Hello", " world"), document(id).blocks.map { it.text })
        json(call("halo_note_merge_block", blockArgs(id, second)))
        assertEquals(original, document(id).blocks.single().id)
        assertEquals("Hello\n world", document(id).blocks.single().text)
        val copy = json(call("halo_note_duplicate_block", blockArgs(id, original))).getJSONArray("blockIds").getString(0)
        assertNotEquals(original, copy); assertEquals(2, document(id).blocks.size)
        json(call("halo_note_delete_block", blockArgs(id, copy)))
        assertEquals(original, document(id).blocks.single().id)
        assertTrue(call("halo_note_delete_block", blockArgs(id, original)) is ToolExecutionResult.Rejected)
        assertEquals("Hello\n world", document(id).blocks.single().text)
    }

    @Test fun referencesRequireExactActiveTargetAndCannotMutateTheirSource() = fixture {
        val id = create(); val target = create("Original source"); val other = create("Other source")
        val targetArgs = mapOf("targetId" to target, "targetVersion" to requireNotNull(context.versionFor(target)))
        val created = json(call("halo_note_insert_reference", args(id) + targetArgs + ("label" to "Reference")))
        val blockId = created.getJSONArray("blockIds").getString(0)
        assertEquals(target, document(id).blocks.last().refObjectId)
        json(call("halo_note_set_reference", blockArgs(id, blockId) + mapOf("targetId" to other, "targetVersion" to requireNotNull(context.versionFor(other)))))
        assertEquals(other, document(id).blocks.last().refObjectId)
        assertEquals("Original source", document(target).blocks.single().text)
        assertTrue(call("halo_note_edit_block", blockArgs(id, blockId) + ("text" to "Wrong path")) is ToolExecutionResult.Rejected)
        f.documents.apply(target, document(target).revision, DocumentCommand.SetText(document(target).blocks.single().id, "Changed source"))
        assertTrue(call("halo_note_insert_reference", args(id) + targetArgs) is ToolExecutionResult.Rejected)
        f.notes.setLifecycle(other, WorkspaceLifecycle.TRASHED)
        assertTrue(call("halo_note_set_reference", blockArgs(id, blockId) + mapOf("targetId" to other, "targetVersion" to "1")) is ToolExecutionResult.Rejected)
    }

    @Test fun markdownReplacementReportsLiteralFallbackAndRejectsNewUnversionedReferences() = fixture {
        val id = create(); val original = document(id)
        assertTrue(call("halo_note_replace_content", args(id) + ("markdown" to "<div>Literal unsupported HTML</div>")) is ToolExecutionResult.Rejected)
        assertEquals(original, document(id))
        val result = json(call("halo_note_replace_content", args(id) + mapOf("markdown" to "<div>Literal unsupported HTML</div>", "allowLiteralFallback" to true)))
        assertTrue(result.getBoolean("literalFallback"))
        assertEquals("<div>Literal unsupported HTML</div>", document(id).blocks.single().text)
        assertTrue(call("halo_note_replace_content", args(id) + ("markdown" to "[[guessed-target|Forged link]]")) is ToolExecutionResult.Rejected)
        json(call("halo_note_replace_content", args(id) + ("markdown" to "# Heading\n\n- [x] Done")))
        assertEquals(listOf(NoteBlockType.HEADING, NoteBlockType.CHECKLIST), document(id).blocks.map { it.type })
        assertTrue(document(id).blocks.last().checked)
    }

    @Test fun archiveTrashRestoreAndInboxUseExistingLifecycleWithoutChangingContent() = fixture {
        val id = create(); val content = document(id).blocks
        val original = args(id)
        json(call("halo_note_archive", original)); assertEquals(WorkspaceLifecycle.ARCHIVED, document(id).lifecycle)
        assertTrue(call("halo_note_trash", original) is ToolExecutionResult.Rejected)
        json(call("halo_note_restore", args(id))); assertEquals(WorkspaceLifecycle.ACTIVE, document(id).lifecycle)
        json(call("halo_note_remove_from_inbox", args(id))); assertFalse(f.notes.getNote(id)!!.inInbox)
        json(call("halo_note_trash", args(id))); assertEquals(WorkspaceLifecycle.TRASHED, document(id).lifecycle)
        assertTrue(call("halo_note_rename", args(id) + ("title" to "Forbidden")) is ToolExecutionResult.Rejected)
        json(call("halo_note_restore", args(id))); assertEquals(content, document(id).blocks)
    }

    @Test fun everyWriteRefusesPendingRecoveryIncludingLegacyDraftAndDoesNotAcknowledgeIt() = fixture {
        val id = create(); val before = document(id); val old = args(id)
        f.documents.saveRecovery(id, before.blocks.map { it.copy(text = "Unsaved learner edit") }, before.revision, 7, "Unsaved title")
        assertTrue(state(id).getBoolean("hasRecoveryDraft"))
        val draft = f.documents.loadRecovery(id)
        for ((name, fields) in listOf("halo_note_rename" to mapOf("title" to "Overwrite"), "halo_note_trash" to emptyMap(),
            "halo_note_archive" to emptyMap(), "halo_note_replace_content" to mapOf("markdown" to "Overwrite"),
            "halo_note_edit_block" to mapOf("blockId" to before.blocks.first().id, "text" to "Overwrite"))) {
            assertTrue(name, call(name, old + fields) is ToolExecutionResult.Rejected)
        }
        assertEquals(before, document(id)); assertEquals(draft, f.documents.loadRecovery(id))
        f.database.notebookDocumentDao().deleteRecovery(id)
        f.notes.saveDraft(id, "Legacy unsaved content")
        assertTrue(call("halo_note_rename", args(id) + ("title" to "Overwrite")) is ToolExecutionResult.Rejected)
        assertEquals("Legacy unsaved content", f.notes.getDraft(id)!!.body)
    }

    @Test fun concurrentEditsOfAnObservedVersionCommitExactlyOnce() = fixture {
        val id = create(); val original = blockArgs(id)
        val results = coroutineScope { listOf(async(Dispatchers.IO) { call("halo_note_edit_block", original + ("text" to "First writer")) },
            async(Dispatchers.IO) { call("halo_note_edit_block", original + ("text" to "Second writer")) }).awaitAll() }
        assertEquals(1, results.count { it is ToolExecutionResult.Success }); assertEquals(1, results.count { it is ToolExecutionResult.Rejected })
        assertTrue(document(id).blocks.first().text in setOf("First writer", "Second writer"))
    }

    @Test fun databaseFailureRollsBackRevisionAndBlockMutationTogether() = fixture {
        val id = create(); val before = document(id)
        f.database.openHelper.writableDatabase.execSQL("CREATE TRIGGER fixture_refuse_note_block BEFORE INSERT ON note_blocks BEGIN SELECT RAISE(ABORT, 'fixture'); END")
        assertTrue(call("halo_note_edit_block", blockArgs(id) + ("text" to "Must roll back")) is ToolExecutionResult.Rejected)
        assertEquals(before, document(id))
    }

    @Test fun foreignBlockAndMissingAnchorCannotEditAnotherNoteOrAppendByGuessing() = fixture {
        val id = create(); val other = create(); val before = document(id)
        assertTrue(call("halo_note_edit_block", args(id) + mapOf("blockId" to document(other).blocks.first().id, "text" to "Wrong note")) is ToolExecutionResult.Rejected)
        assertTrue(call("halo_note_insert_block", args(id) + mapOf("afterBlockId" to "missing", "blockType" to "PARAGRAPH", "text" to "Wrong anchor")) is ToolExecutionResult.Rejected)
        assertEquals(before, document(id))
    }

    @Test fun malformedArgumentsAndUnsupportedFieldCombinationsNeverWrite() = fixture {
        val id = create(); val original = document(id)
        val fields = blockArgs(id)
        for (extra in listOf(mapOf("checked" to "true"), mapOf("checked" to true), mapOf("language" to "kotlin"), mapOf("unknown" to "x"), emptyMap(), mapOf("text" to "x".repeat(12001)))) {
            assertTrue(call("halo_note_edit_block", fields + extra) is ToolExecutionResult.Rejected)
        }
        for (delta in listOf(0, 0.5, Double.NaN, 2)) assertTrue(call("halo_note_move_block", fields + ("delta" to delta)) is ToolExecutionResult.Rejected)
        assertTrue(call("halo_note_get", mapOf("noteId" to id, "offset" to -1)) is ToolExecutionResult.Rejected)
        assertTrue(call("halo_note_get", mapOf("noteId" to id, "offset" to "0")) is ToolExecutionResult.Rejected)
        assertEquals(original, document(id))
    }

    @Test fun specializedDocumentsKeepTheirOwnOwnerAndCannotBeForgedThroughCodeLanguage() = fixture {
        val special = f.text.create("CSV", WorkspaceTextFormat.CSV, "a,b\n1,2")
        assertTrue(call("halo_note_get", mapOf("noteId" to special.id)) is ToolExecutionResult.Rejected)
        assertEquals("a,b\n1,2", f.text.get(special.id)!!.text)
        val id = create()
        assertTrue(call("halo_note_edit_block", blockArgs(id) + mapOf("blockType" to "CODE", "language" to WorkspaceFileReceipts.LANGUAGE)) is ToolExecutionResult.Rejected)
        assertTrue(call("halo_note_replace_content", args(id) + ("markdown" to "```halo-file-batch-v1\n{}\n```")) is ToolExecutionResult.Rejected)
    }

    @Test fun hostileControlTextAndManyBlockReceiptsRemainBoundedWithExplicitPaging() = fixture {
        val id = create()
        val text = "\u0001".repeat(12000)
        json(call("halo_note_edit_block", blockArgs(id) + ("text" to text)))
        var offset = 0; val restored = StringBuilder()
        do {
            val page = json(call("halo_note_read_block", blockArgs(id) + ("offset" to offset)))
            assertTrue(page.toString().length <= 24000)
            restored.append(page.getJSONObject("block").getString("text"))
            offset = if (page.isNull("nextOffset")) -1 else page.getInt("nextOffset")
        } while (offset >= 0)
        assertEquals(text, restored.toString())
        val replaced = json(call("halo_note_replace_content", args(id) + ("markdown" to (1..150).joinToString("\n\n") { "Block $it" })))
        assertEquals(150, replaced.getInt("createdBlockCount")); assertTrue(replaced.getBoolean("blockIdsTruncated"))
        var pageOffset = 0; val ids = mutableListOf<String>(); val pageVersion = state(id).getString("version")
        do {
            val page = json(call("halo_note_get", mapOf("noteId" to id, "offset" to pageOffset, "version" to pageVersion)))
            val blocks = page.getJSONArray("blocks"); repeat(blocks.length()) { ids += blocks.getJSONObject(it).getString("blockId") }
            pageOffset = if (page.isNull("nextOffset")) -1 else page.getInt("nextOffset")
        } while (pageOffset >= 0)
        assertEquals(document(id).blocks.map { it.id }, ids)
    }

    @Test fun blockCapacityAndSurrogateBoundaryRefuseLossyOperations() = fixture {
        val id = create("A😀B"); val before = document(id)
        assertTrue(call("halo_note_split_block", blockArgs(id) + ("offset" to 2)) is ToolExecutionResult.Rejected)
        assertEquals(before, document(id))
        val blocks = (0 until DocumentReducer.MAX_BLOCKS).map { NoteBlock(UUID.randomUUID().toString(), it, NoteBlockType.PARAGRAPH, "x") }
        assertTrue(f.documents.commit(id, before.revision, blocks) is CommitOutcome.Committed)
        val full = document(id)
        assertTrue(call("halo_note_duplicate_block", blockArgs(id)) is ToolExecutionResult.Rejected)
        assertTrue(call("halo_note_insert_block", args(id) + mapOf("blockType" to "PARAGRAPH", "text" to "overflow")) is ToolExecutionResult.Rejected)
        assertEquals(full, document(id))
    }

    @Test fun replacementCannotSilentlyDropTheTailBeyondTheCanonicalBlockLimit() = fixture {
        val id = create(); val before = document(id)
        val markdown = (0..DocumentReducer.MAX_BLOCKS).joinToString("\n\n") { "x" }
        val parsed = NotebookDocumentCodec.parse(markdown) { UUID.randomUUID().toString() }
        assertEquals(2001, parsed.sourceBlockCount)
        assertEquals(2000, parsed.blocks.size)
        for (literalFallback in listOf(false, true)) {
            assertTrue(call("halo_note_replace_content", args(id) + mapOf("markdown" to markdown, "allowLiteralFallback" to literalFallback)) is ToolExecutionResult.Rejected)
            assertEquals(before, document(id))
        }
    }

    @Test fun laterPagesRequireTheOriginalObservedVersionAndRejectInterleavedEdits() = fixture {
        val id = create((0..10).joinToString("\n\n") { "Block $it" }); val original = args(id)
        assertTrue(call("halo_note_get", mapOf("noteId" to id, "offset" to 8)) is ToolExecutionResult.Rejected)
        val next = json(call("halo_note_get", original + ("offset" to 8)))
        assertEquals(3, next.getJSONArray("blocks").length())
        json(call("halo_note_rename", original + ("title" to "New title")))
        assertTrue(call("halo_note_get", original + ("offset" to 8)) is ToolExecutionResult.Rejected)
    }

    @Test fun readingAnOversizedLegacyNoteCannotInitializeATruncatedDocument() = fixture {
        val body = (0..DocumentReducer.MAX_BLOCKS).joinToString("\n\n") { "Original $it" }
        val note = f.notes.createNote(body)
        assertTrue(call("halo_note_get", mapOf("noteId" to note.id)) is ToolExecutionResult.Rejected)
        assertEquals(note, f.notes.getNote(note.id))
        assertTrue(document(note.id).blocks.isEmpty())
    }

    @Test fun addingOrRemovingAnAttachmentInvalidatesAnOlderTrashApprovalWithoutChangingNoteRevision() = fixture {
        val id = create(); val before = document(id); val old = args(id)
        f.database.workspaceAssetDao().insertAsset(WorkspaceAssetEntity("fixture-asset", id, "IMAGE", "image/png", "EXTERNAL_URI", "content://fixture/image", true, "Fixture", 10, 1, 1, null, 1))
        f.database.workspaceAssetDao().insertAttachment(NoteAttachmentEntity("fixture-link", id, "fixture-asset", 0, null, 1))
        assertEquals(before.revision, document(id).revision)
        assertTrue(call("halo_note_trash", old) is ToolExecutionResult.Rejected)
        assertEquals(1, state(id).getInt("attachmentCount"))
        val withAttachment = args(id)
        f.database.workspaceAssetDao().deleteAttachment(id, "fixture-link")
        assertTrue(call("halo_note_trash", withAttachment) is ToolExecutionResult.Rejected)
        assertEquals(WorkspaceLifecycle.ACTIVE, document(id).lifecycle)
        assertEquals(before.blocks, document(id).blocks)
    }

    @Test fun referenceCannotReadUnsupportedWorkspaceKindsThroughAGuessedVersion() = fixture {
        val id = create(); val target = create("Conversation-private content")
        f.database.openHelper.writableDatabase.execSQL("UPDATE workspace_objects SET type='CONVERSATION' WHERE id=?", arrayOf(target))
        val targetVersion = requireNotNull(context.versionFor(target))
        val before = document(id)
        assertTrue(call("halo_note_insert_reference", args(id) + mapOf("targetId" to target, "targetVersion" to targetVersion)) is ToolExecutionResult.Rejected)
        assertEquals(before, document(id))
    }
}
