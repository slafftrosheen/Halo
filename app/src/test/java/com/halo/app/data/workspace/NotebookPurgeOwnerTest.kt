package com.halo.app.data.workspace

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import com.halo.app.data.notebook.*
import com.halo.app.di.tools.*
import com.halo.app.notebook.NotebookUseCases
import com.halo.runtime.cognition.skills.DefaultSkillRegistry
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import kotlinx.coroutines.*
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
class NotebookPurgeOwnerTest {
    private class Fixture(val f: WorkspaceFileToolsFixture) {
        val root = f.root.canonicalFile
        var scheduled = 0
        var removals = 0
        var failRemoval = false
        var onRemove: (() -> Unit)? = null
        val owner = NotebookPurgeOwner(f.database, root, { scheduled++ }, { file ->
            removals++; onRemove?.invoke(); if (failRemoval) error("Fixture busy file"); check(file.delete())
        })
        val repository = RoomNotebookRepository(f.database.notebookDao(), documents = f.documents, purgeOwner = owner)
        val useCases = NotebookUseCases(repository)
        val reads = WorkspaceNoteActions(f.database, f.documents, useCases, f.contextOwner())
        val actions = WorkspaceNotePurgeActions(useCases)
        suspend fun create() = f.notes.createNote("Private fixture text", title = "Private fixture title").id.also {
            json(reads.execute(ToolCall("read", "halo_note_get", mapOf("noteId" to it))))
        }
        suspend fun state(id: String) = json(reads.execute(ToolCall("read", "halo_note_get", mapOf("noteId" to id))))
        suspend fun args(id: String): Map<String, Any> = mapOf("noteId" to id, "version" to state(id).getString("version"))
        suspend fun trash(id: String) { f.notes.setLifecycle(id, WorkspaceLifecycle.TRASHED) }
        suspend fun asset(id: String, key: String = UUID.randomUUID().toString(), file: File = File(root, key).also { it.writeText("Owned fixture") }, kind: String = "MANAGED_FILE"): NoteAttachment {
            f.database.workspaceAssetDao().insertAsset(WorkspaceAssetEntity(key, id, "IMAGE", "image/png", kind, file.path, true, "Fixture", 13, 1, 1, null, 1))
            f.database.workspaceAssetDao().insertAttachment(NoteAttachmentEntity("link-$key", id, key, 0, null, 1))
            return requireNotNull(f.database.workspaceAssetDao().attachment(id, "link-$key"))
        }
        suspend fun call(name: String, fields: Map<String, Any>) = actions.execute(ToolCall(UUID.randomUUID().toString(), name, fields))
        suspend fun status(id: String) = requireNotNull(owner.status(id))
    }
    private fun fixture(block: suspend Fixture.() -> Unit) = runBlocking { WorkspaceFileToolsFixture().use { Fixture(it).block() } }
    companion object {
        private fun json(result: ToolExecutionResult): JSONObject {
            assertTrue(result.toString(), result is ToolExecutionResult.Success)
            return JSONObject((result as ToolExecutionResult.Success).output)
        }
        private fun saved(result: NotebookPurgeResult) = (result as NotebookPurgeResult.Saved).receipt
    }

    @Test fun actualRegistrationHasClosedContractsSoleOwnerBundledRisksAndMandatoryApproval() = fixture {
        val definitions = actions.definitions(); assertEquals(3, definitions.size)
        val executor = WorkspaceToolExecutor(f.notes, f.workspace, f.contextOwner(), notePurgeActions = actions)
        assertTrue(executor.definitions().containsAll(definitions))
        val descriptors = definitions.map { requireNotNull(HaloToolDescriptors.workspace(it)) }
        assertTrue(ToolRegistry(listOf(executor), descriptors).operabilityReport(listOf(ToolFeatureOperability("purge", descriptors.map { it.name }.toSet()))).isEmpty())
        val skill = DefaultSkillRegistry.defaultBuiltInSkills().single { it.id.value == "halo.notebook" }
        val bundle = JSONObject(f.context.assets.open("skills/halo.notebook.json").bufferedReader().use { it.readText() }).getJSONArray("toolDefinitions")
        val risks = (0 until bundle.length()).associate { bundle.getJSONObject(it).let { row -> row.getString("toolId") to row.getString("risk") } }
        for (descriptor in descriptors) {
            val read = descriptor.name == "halo_note_purge_status"
            assertEquals("NotebookPurgeOwner", descriptor.operability!!.owner)
            assertEquals(false, descriptor.definition.parameters["additionalProperties"])
            assertEquals(risks[descriptor.name], skill.toolDefinitions.single { it.toolId == descriptor.name }.risk.name)
            assertEquals(if (read) ReplaySafety.SAFE else ReplaySafety.NEVER_REPLAY, descriptor.security.replaySafety)
            val policy = PolicyToolAuthorizer(defaultPolicyProvider = { ToolApprovalPolicy.ALWAYS_ALLOW }, descriptorProvider = { descriptor })
            val authorization = policy.authorize(ToolCall("policy", descriptor.name, emptyMap()))
            if (read) assertTrue(authorization is ToolAuthorization.Authorized) else {
                assertEquals(ConfirmationPolicy.ALWAYS, descriptor.security.confirmation)
                assertTrue(authorization is ToolAuthorization.Rejected)
            }
        }
    }

    @Test fun deletionCascadesOnlyTheObservedNoteAndKeepsDurableContentFreeReceipt() = fixture {
        val id = create(); val linked = create(); val attached = asset(id); trash(id)
        val old = args(id)
        val result = json(call("halo_note_purge", old))
        assertTrue(result.getBoolean("deleted")); assertTrue(result.getBoolean("cleanupComplete"))
        assertEquals(1, result.getInt("exclusiveAssetsCleared")); assertFalse(File(attached.pathOrUri).exists())
        assertNull(f.notes.getNote(id)); assertNotNull(f.notes.getNote(linked)); assertNull(f.documents.load(id))
        assertTrue(f.database.notebookDao().getAssetsForObject(id).isEmpty())
        val raw = result.toString()
        assertFalse(raw.contains("Private fixture")); assertFalse(raw.contains(root.path))
        val duplicate = json(call("halo_note_purge", old))
        assertTrue(duplicate.getBoolean("alreadyRecorded")); assertEquals(1, removals)
        assertEquals(result.getString("receiptVersion"), duplicate.getString("receiptVersion"))
    }

    @Test fun activeMissingNonNoteAndUnconfiguredOwnersCannotDeleteOrCreateAReceipt() = fixture {
        val id = create(); asset(id)
        assertTrue(call("halo_note_purge", args(id)) is ToolExecutionResult.Rejected)
        assertEquals("NOT_FOUND", json(call("halo_note_purge_status", mapOf("noteId" to id))).getString("status"))
        assertTrue(owner.purge("missing") is NotebookPurgeResult.Refused)
        assertFalse(RoomNotebookRepository(f.database.notebookDao()).permanentlyDelete(id))
        f.database.openHelper.writableDatabase.execSQL("UPDATE workspace_objects SET type='FILE',lifecycle='TRASHED' WHERE id=?", arrayOf(id))
        assertTrue(owner.purge(id) is NotebookPurgeResult.Refused)
        assertNotNull(f.database.notebookDao().getWorkspaceObject(id)); assertEquals(0, removals)
    }

    @Test fun changedNoteOrUnattachedAssetRefusesAnOldPermanentDeletionApproval() = fixture {
        val id = create(); trash(id); val old = args(id)
        val path = File(root, "unattached").also { it.writeText("New material") }
        f.database.workspaceAssetDao().insertAsset(WorkspaceAssetEntity("unattached", id, "IMAGE", "image/png", "MANAGED_FILE", path.path, true, "New", 12, 1, 1, null, 1))
        assertTrue(call("halo_note_purge", old) is ToolExecutionResult.Rejected)
        assertTrue(path.exists()); assertNotNull(f.notes.getNote(id)); assertNull(owner.status(id))
    }

    @Test fun recoveryDeletionNeedsExplicitChoiceAndExactVersionAndCannotChangeUnderOneReceipt() = fixture {
        val id = create(); repository.saveDraft(id, "Uncommitted work"); trash(id)
        val current = state(id); val fields = args(id)
        assertTrue(call("halo_note_purge", fields) is ToolExecutionResult.Rejected)
        assertTrue(call("halo_note_purge", fields + mapOf("includeRecoveryDraft" to true, "recoveryVersion" to "note-draft:none")) is ToolExecutionResult.Rejected)
        val approved = fields + mapOf("includeRecoveryDraft" to true, "recoveryVersion" to current.getString("recoveryVersion"))
        assertTrue(json(call("halo_note_purge", approved)).getBoolean("deleted")); assertNull(repository.getDraft(id))
        assertTrue(call("halo_note_purge", fields) is ToolExecutionResult.Rejected)
        assertTrue(json(call("halo_note_purge", approved)).getBoolean("alreadyRecorded"))
    }

    @Test fun busyFileKeepsOwnershipAndExplicitReceiptRetryCompletesWithoutReplayingTheOriginalPurge() = fixture {
        val id = create(); val attached = asset(id); trash(id); val original = args(id)
        failRemoval = true
        val first = json(call("halo_note_purge", original))
        assertTrue(first.getBoolean("deleted")); assertFalse(first.getBoolean("cleanupComplete")); assertEquals("PENDING", first.getString("status"))
        assertNull(f.notes.getNote(id)); assertNotNull(f.database.notebookPurgeDao().asset(attached.assetId)); assertTrue(File(attached.pathOrUri).exists())
        assertEquals(1, scheduled); assertEquals(1, removals)
        assertTrue(json(call("halo_note_purge", original)).getBoolean("alreadyRecorded")); assertEquals(1, removals)
        failRemoval = false
        val retried = json(call("halo_note_purge_retry", mapOf("noteId" to id, "receiptVersion" to first.getString("receiptVersion"))))
        assertTrue(retried.getBoolean("cleanupComplete")); assertFalse(File(attached.pathOrUri).exists())
        assertTrue(call("halo_note_purge_retry", mapOf("noteId" to id, "receiptVersion" to first.getString("receiptVersion"))) is ToolExecutionResult.Rejected)
        assertEquals(2, removals)
    }

    @Test fun sharedAndExternalFilesStayOwnedByOtherNotesAndOnlyExclusiveBytesAreCleared() = fixture {
        val id = create(); val other = create(); val exclusive = asset(id, "exclusive"); val shared = asset(id, "shared")
        asset(other, "other-shared", File(shared.pathOrUri))
        val external = asset(id, "external", kind = "EXTERNAL_URI")
        trash(id)
        val result = json(call("halo_note_purge", args(id)))
        assertEquals(1, result.getInt("exclusiveAssetsCleared")); assertEquals(1, result.getInt("sharedAssetsRetained")); assertEquals(1, result.getInt("externalAssetsRetained"))
        assertFalse(File(exclusive.pathOrUri).exists()); assertTrue(File(shared.pathOrUri).exists()); assertTrue(File(external.pathOrUri).exists())
        assertNotNull(f.notes.getNote(other)); assertEquals(1, f.database.workspaceAssetDao().observeAttachments(other).first().size)
    }

    @Test fun aliasedSharedOwnershipAndWorkspaceFileOwnershipProtectTheSameBytes() = fixture {
        val id = create(); val other = create(); val shared = asset(id, "canonical")
        val alias = File(root, "sub/../canonical")
        asset(other, "alias", alias)
        val source = f.notes.createNote("Source")
        val file = f.files.createManagedFile("owned.png", "image/png", byteArrayOf(1,2,3), source.id, "1", "{}")
        val ownedPath = requireNotNull(f.database.workspaceFileDao().getFile(file.objectId.value)?.ownedCopyPath)
        asset(id, "workspace-file", File(ownedPath))
        trash(id)
        val result = json(call("halo_note_purge", args(id)))
        assertEquals(2, result.getInt("sharedAssetsRetained")); assertTrue(File(shared.pathOrUri).exists()); assertTrue(File(ownedPath).exists())
    }

    @Test fun uriOnlyWorkspaceFilesAndAppOwnedContentAliasesRetainSharedBytes() = fixture {
        val id = create(); val other = create()
        val fileUriAsset = asset(id, "file-uri"); val contentAsset = asset(id, "content-uri"); val appAsset = asset(id, "app-uri")
        val authority = "${com.halo.app.BuildConfig.APPLICATION_ID}.captures"
        fun uri(path: String) = android.net.Uri.Builder().scheme("content").authority(authority).appendPath("workspace-owned").appendPath(File(path).name).build().toString()
        for ((attachment, uri) in listOf(fileUriAsset to File(fileUriAsset.pathOrUri).toURI().toString(), contentAsset to uri(contentAsset.pathOrUri))) {
            val source = create()
            f.database.workspaceFileDao().insertFile(WorkspaceFileEntity(source, uri, "Shared", "image/png", 13, 1, null, false))
        }
        f.database.workspaceAssetDao().insertAsset(WorkspaceAssetEntity("app-owned", other, "IMAGE", "image/png", "APP_OWNED", uri(appAsset.pathOrUri), true, "Shared", 13, 1, 1, null, 1))
        trash(id)
        val result = json(call("halo_note_purge", args(id)))
        assertTrue(result.getBoolean("cleanupComplete")); assertEquals(3, result.getInt("sharedAssetsRetained")); assertEquals(0, removals)
        listOf(fileUriAsset, contentAsset, appAsset).forEach { assertTrue(File(it.pathOrUri).exists()) }
    }

    @Test fun duplicateClaimsInTheDeletedNoteDoNotPretendToBeOtherReaders() = fixture {
        val id = create(); val first = asset(id, "first-claim")
        asset(id, "second-claim", File(first.pathOrUri)); trash(id)
        val result = json(call("halo_note_purge", args(id)))
        assertTrue(result.getBoolean("cleanupComplete")); assertEquals(0, result.getInt("sharedAssetsRetained"))
        assertEquals(1, removals); assertFalse(File(first.pathOrUri).exists())
        assertEquals(2, result.getInt("exclusiveAssetsCleared")) // Two cleanup entries verified absent, one file effect.
    }

    @Test fun ambiguousOwnedUriRequiresReviewWhileUnrelatedProvidersDoNotBlockCleanup() = fixture {
        val other = create()
        val authority = "${com.halo.app.BuildConfig.APPLICATION_ID}.captures"
        val row = WorkspaceAssetEntity("unknown-uri", other, "IMAGE", "image/png", "APP_OWNED", "content://$authority/unknown/value", true, "Unknown", 1, 1, 1, null, 1)
        f.database.workspaceAssetDao().insertAsset(row)
        val id = create(); val attached = asset(id); trash(id)
        val result = json(call("halo_note_purge", args(id)))
        assertEquals("OWNERSHIP_UNVERIFIED", result.getString("reason")); assertEquals("REVIEW_REQUIRED", result.getString("status")); assertTrue(File(attached.pathOrUri).exists())
        f.database.openHelper.writableDatabase.execSQL("UPDATE workspace_assets SET pathOrUri='content://external.provider/unknown/value' WHERE id='unknown-uri'")
        val retried = json(call("halo_note_purge_retry", mapOf("noteId" to id, "receiptVersion" to result.getString("receiptVersion"))))
        assertTrue(retried.getBoolean("cleanupComplete")); assertFalse(File(attached.pathOrUri).exists())
    }

    @Test fun noteWithoutManagedBytesHasANewCompleteReceipt() = fixture {
        val id = create(); trash(id)
        val result = json(call("halo_note_purge", args(id)))
        assertTrue(result.getBoolean("cleanupComplete")); assertFalse(result.getBoolean("alreadyRecorded")); assertEquals(0, removals)
    }

    @Test fun boundedReconciliationDoesNotStarveLaterReceiptsBehindBusyFiles() = fixture {
        failRemoval = true
        val ids = (0..20).map {
            val id = create(); asset(id, "fair-$it"); trash(id)
            assertEquals("PENDING", saved(owner.purge(id)).record.state)
            id
        }
        failRemoval = false
        var attempts = 0
        onRemove = { if (++attempts <= 20) error("First twenty remain busy") }
        assertTrue(owner.reconcile())
        assertEquals("PENDING", status(ids.last()).record.state)
        assertTrue(owner.reconcile())
        assertEquals("COMPLETE", status(ids.last()).record.state)
    }

    @Test fun unsafeChangedOrMissingAssetMetadataNeverAuthorizesAFileEffect() = fixture {
        val id = create(); val attached = asset(id); trash(id); failRemoval = true
        val first = json(call("halo_note_purge", args(id))); val changed = File(root, "changed").also { it.writeText("Keep") }
        f.database.openHelper.writableDatabase.execSQL("UPDATE workspace_assets SET pathOrUri=? WHERE id=?", arrayOf(changed.path, attached.assetId))
        failRemoval = false
        val reviewed = json(call("halo_note_purge_retry", mapOf("noteId" to id, "receiptVersion" to first.getString("receiptVersion"))))
        assertEquals("REVIEW_REQUIRED", reviewed.getString("status")); assertEquals("ASSET_CHANGED", reviewed.getString("reason"))
        assertTrue(changed.exists()); assertTrue(File(attached.pathOrUri).exists()); assertEquals(1, removals)
    }

    @Test fun outsideRootAndTraversalPathsRemainForReviewAndAreNeverDeleted() = fixture {
        val outside = File(f.context.cacheDir, "outside-${UUID.randomUUID()}").also { it.writeText("Keep outside") }
        try {
            for (path in listOf(outside, File(root, "sub/../outside-alias"))) {
                File(root, "outside-alias").writeText("Keep inside")
                val id = create(); asset(id, file = path); trash(id)
                val result = json(call("halo_note_purge", args(id)))
                assertEquals("REVIEW_REQUIRED", result.getString("status")); assertEquals("UNSAFE_PATH", result.getString("reason"))
            }
            assertTrue(outside.exists()); assertEquals(0, removals)
        } finally { outside.delete() }
    }

    @Test fun claimRollbackCannotDeleteSourceBytesOrLeaveADeletionReceipt() = fixture {
        val id = create(); val attached = asset(id); trash(id)
        f.database.openHelper.writableDatabase.execSQL("CREATE TRIGGER fixture_refuse_purge BEFORE DELETE ON workspace_objects BEGIN SELECT RAISE(ABORT,'fixture'); END")
        assertTrue(runCatching { owner.purge(id) }.isFailure)
        assertNotNull(f.notes.getNote(id)); assertNull(owner.status(id)); assertTrue(File(attached.pathOrUri).exists()); assertEquals(0, removals)
    }

    @Test fun cleanupTransactionFailureKeepsTheCommittedDeletionAndCanReconcileAnAlreadyAbsentFile() = fixture {
        val id = create(); val attached = asset(id); trash(id)
        f.database.openHelper.writableDatabase.execSQL("CREATE TRIGGER fixture_refuse_asset_delete BEFORE DELETE ON workspace_assets BEGIN SELECT RAISE(ABORT,'fixture'); END")
        assertTrue(runCatching { owner.purge(id) }.isFailure)
        assertNull(f.notes.getNote(id)); assertFalse(File(attached.pathOrUri).exists()); assertEquals("PENDING", status(id).record.state)
        assertNotNull(f.database.notebookPurgeDao().asset(attached.assetId)); assertEquals(1, scheduled)
        f.database.openHelper.writableDatabase.execSQL("DROP TRIGGER fixture_refuse_asset_delete")
        assertFalse(owner.reconcile()); assertEquals("COMPLETE", status(id).record.state); assertEquals(1, removals)
    }

    @Test fun boundedCleanupContinuesFromItsDurableQueueAndIgnoresUnapprovedOrphans() = fixture {
        val id = create(); repeat(35) { asset(id, "asset-${it.toString().padStart(2,'0')}") }; trash(id)
        val result = json(call("halo_note_purge", args(id)))
        assertEquals(3, result.getInt("pendingAssets")); assertEquals(32, removals)
        assertFalse(owner.reconcile()); assertEquals(35, removals); assertEquals("COMPLETE", status(id).record.state)
        val orphan = File(root, "unapproved-orphan").also { it.writeText("Keep") }
        f.database.workspaceAssetDao().insertAsset(WorkspaceAssetEntity("orphan", "unknown", "IMAGE", "image/png", "MANAGED_FILE", orphan.path, true, null, 4, 1, 1, null, 1))
        assertFalse(owner.reconcile()); assertTrue(orphan.exists()); assertEquals(35, removals)
    }

    @Test fun aRestoredParentBlocksCleanupAndStatusDoesNotClaimThatNewSourceIsDeleted() = fixture {
        val id = create(); val attached = asset(id); trash(id); failRemoval = true
        json(call("halo_note_purge", args(id)))
        RoomNotebookRepository(f.database.notebookDao(), newId = { id }).createNote("Restored original")
        failRemoval = false; assertFalse(owner.reconcile())
        val result = json(call("halo_note_purge_status", mapOf("noteId" to id)))
        assertTrue(result.getBoolean("sourcePresent")); assertFalse(result.getBoolean("deleted")); assertFalse(result.getBoolean("cleanupComplete"))
        assertEquals("SOURCE_PRESENT", result.getString("reason")); assertTrue(File(attached.pathOrUri).exists()); assertEquals(1, removals)
    }

    @Test fun cachedAttachmentsCannotResurrectAPurgedSourceButValidCopiesKeepSharedBytes() = fixture {
        val source = create(); val target = create(); val image = asset(source)
        val assets = NotebookAssetRepository(f.context, f.database)
        assets.attachExistingImage(target, image)
        trash(source); val result = json(call("halo_note_purge", args(source)))
        assertEquals(1, result.getInt("sharedAssetsRetained")); assertTrue(File(image.pathOrUri).exists())
        val newTarget = create()
        assertTrue(runCatching { assets.attachExistingImage(newTarget, image) }.isFailure)
        assertTrue(f.database.workspaceAssetDao().observeAttachments(newTarget).first().isEmpty())
    }

    @Test fun cancellationAfterLogicalDeletionStillPreservesCleanupAndSchedulesItsRecovery() = fixture {
        val id = create(); val attached = asset(id); trash(id); failRemoval = true
        coroutineScope {
            lateinit var operation: Job
            onRemove = { operation.cancel() }
            operation = launch(start = CoroutineStart.LAZY) { owner.purge(id) }
            operation.start(); operation.join()
        }
        assertNull(f.notes.getNote(id)); assertTrue(File(attached.pathOrUri).exists()); assertEquals("PENDING", status(id).record.state)
        assertEquals(1, scheduled)
    }

    @Test fun ownerRecreationResumesOnlyItsCommittedPendingReceipt() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val name = "purge-reopen-${UUID.randomUUID()}.db"
        val file = File(context.cacheDir, "purge-reopen-${UUID.randomUUID()}").also { it.writeText("Owned") }.canonicalFile
        fun open() = Room.databaseBuilder(context, AppDatabase::class.java, name).allowMainThreadQueries().build()
        var db = open()
        try {
            val notes = RoomNotebookRepository(db.notebookDao()); val note = notes.createNote("Durable")
            db.workspaceAssetDao().insertAsset(WorkspaceAssetEntity("asset", note.id, "IMAGE", "image/png", "MANAGED_FILE", file.path, true, null, 5, 1, 1, null, 1))
            notes.setLifecycle(note.id, WorkspaceLifecycle.TRASHED)
            val first = NotebookPurgeOwner(db, context.cacheDir.canonicalFile, removeFile = { error("Fixture process stop") })
            assertEquals("PENDING", saved(first.purge(note.id)).record.state)
            db.close(); db = open()
            val reopened = NotebookPurgeOwner(db, context.cacheDir.canonicalFile)
            assertFalse(reopened.reconcile()); assertEquals("COMPLETE", reopened.status(note.id)!!.record.state)
            assertFalse(file.exists()); assertNull(db.notebookDao().getNote(note.id))
        } finally { db.close(); context.deleteDatabase(name); file.delete() }
    }

    @Test fun callerTransactionCannotMakeCleanupRunBeforeTheNoteDeletionCommit() = fixture {
        val id = create(); val attached = asset(id); trash(id)
        f.database.withTransaction { assertTrue(owner.purge(id) is NotebookPurgeResult.Refused) }
        assertTrue(File(attached.pathOrUri).exists()); assertNotNull(f.notes.getNote(id)); assertNull(owner.status(id))
        assertEquals(0, removals)
    }

    @Test fun restoredNoteNeedsAFreshSourceAndPriorReceiptApprovalBeforeANewPurgeGeneration() = fixture {
        val id = create(); val attached = asset(id); trash(id); failRemoval = true
        val oldArgs = args(id); val initial = json(call("halo_note_purge", oldArgs))
        RoomNotebookRepository(f.database.notebookDao(), newId = { id }).createNote("Restored content")
        trash(id); failRemoval = false
        assertTrue(call("halo_note_purge", oldArgs) is ToolExecutionResult.Rejected)
        val fresh = state(id); val freshArgs = args(id)
        assertTrue(call("halo_note_purge", freshArgs) is ToolExecutionResult.Rejected)
        val result = json(call("halo_note_purge", freshArgs + ("priorReceiptVersion" to fresh.getString("purgeReceiptVersion"))))
        assertTrue(result.getBoolean("deleted")); assertTrue(result.getBoolean("cleanupComplete"))
        assertNotEquals(initial.getString("receiptVersion"), result.getString("receiptVersion")); assertFalse(File(attached.pathOrUri).exists())
    }

    @Test fun malformedPurgeChoicesAndReceiptIdentitiesCannotMutateAnything() = fixture {
        val id = create(); asset(id); trash(id)
        for (extra in listOf(mapOf("unexpected" to true), mapOf("includeRecoveryDraft" to "true"),
            mapOf("includeRecoveryDraft" to true), mapOf("recoveryVersion" to "wrong"))) {
            assertTrue(call("halo_note_purge", args(id) + extra) is ToolExecutionResult.Rejected)
        }
        assertTrue(call("halo_note_purge_retry", mapOf("noteId" to id, "receiptVersion" to "guessed")) is ToolExecutionResult.Rejected)
        assertNotNull(f.notes.getNote(id)); assertNull(owner.status(id)); assertEquals(0, removals)
    }
}
