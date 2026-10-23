package com.halo.app.data.workspace

import com.halo.app.di.tools.*
import com.halo.app.data.study.StudyRepository
import com.halo.app.study.*
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class WorkspaceOperabilityTest {
    @Test fun actualHighValueOperationsHaveVersionedContractsAndSafeReplayMetadata() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val context = f.contextOwner()
            val executors = listOf(WorkspaceCanvasActions(f.database, f.canvases), WorkspaceContinuityActions(f.continuity(context)),
                WorkspaceFileEditingActions(f.tools), StudioToolExecutor(f.studio()), WorkspaceToolExecutor(f.notes, f.workspace, context))
            val descriptors = executors.flatMap { it.definitions() }.map { requireNotNull(HaloToolDescriptors.workspace(it) ?: HaloToolDescriptors.file(it)) }
            val registry = ToolRegistry(executors, descriptors)
            val feature = ToolFeatureOperability("workspace", descriptors.map { it.name }.toSet())
            assertEquals(emptyList<ToolOperabilityProblem>(), registry.operabilityReport(listOf(feature)))
            listOf("halo_create_note", "halo_create_empty_note", "halo_save_screen_to_notebook", "halo_study_with_halo", "halo_canvas_add_text").forEach {
                assertEquals(ReplaySafety.NEVER_REPLAY, registry.descriptor(it)!!.security.replaySafety)
            }
            val discovery = ToolDiscoveryService(registry)
            assertTrue(discovery.expand(listOf("halo_canvas_add_text")).single { it.name == "halo_canvas_add_text" }.description.contains("Operation contract v1"))
            assertTrue(registry.descriptors().any { it.name == "halo_canvas_add_text" && "canvas" in it.keywords })
        }
    }
    @Test fun assistantStudyRequiresExactObservedVersionAndPersistsThroughTheSameStudyOwner() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val context = f.contextOwner()
            val study = StudyUseCases(StudyRepository(f.database, f.database.studyDao()), sourceResolver = WorkspaceStudySourceResolver(f.context, f.documents, f.workspace, f.files, noPdf()))
            val actions = WorkspaceToolExecutor(f.notes, f.workspace, context, exactStudyStarter = { id, version ->
                study.startExactMaterial(id, version, f.contextOwner()::versionFor)
            })
            val source = f.text.create("Study this", WorkspaceTextFormat.TXT, "Source content")
            suspend fun call(args: Map<String, Any>) = actions.execute(ToolCall("fixture", "halo_study_with_halo", args))
            assertTrue(call(mapOf("noteId" to source.id)) is ToolExecutionResult.Rejected)
            assertTrue(call(mapOf("noteId" to source.id, "sourceVersion" to "stale")) is ToolExecutionResult.Rejected)
            assertTrue(study.observeActiveSessions().first().isEmpty())
            assertTrue(call(mapOf("noteId" to source.id, "sourceVersion" to source.revision.toString())) is ToolExecutionResult.Success)
            val session = study.observeActiveSessions().first().single()
            assertEquals(source.id, session.sources.single().objectId)
            assertEquals(source.revision.toString(), session.sources.single().revision)
        }
    }
    @Test fun fileInsertionChecksTargetRevisionWithoutOverwritingEitherSource() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val source = f.text.create("Source", WorkspaceTextFormat.TXT, "Retain")
            val canvas = f.canvases.create("Board")
            val actions = WorkspaceFileEditingActions(f.tools)
            val args = mapOf("canvasId" to canvas, "canvasVersion" to "canvas:1", "objectId" to source.id, "version" to source.revision.toString())
            assertTrue(actions.execute(ToolCall("fixture", "halo_file_attach_canvas", args)) is ToolExecutionResult.Success)
            assertTrue(actions.execute(ToolCall("fixture", "halo_file_attach_canvas", args)) is ToolExecutionResult.Rejected)
            assertEquals(1, f.canvases.observe(canvas).first()!!.nodes.size)
            assertEquals("Retain", f.text.get(source.id)!!.text)
        }
    }
    @Test fun attachedPhotoUsesContextIdentityAndItsDistinctStudyCitationRevision() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val note = f.notes.createNote(title = "Photo note")
            f.database.workspaceAssetDao().insertAsset(WorkspaceAssetEntity("image-asset", note.id, "IMAGE", "image/png", "MANAGED_FILE", "synthetic-no-read", true, "Photo", 10, 16, 8, null, 1))
            f.database.workspaceAssetDao().insertAttachment(NoteAttachmentEntity("image-link", note.id, "image-asset", 0, null, 1))
            val context = f.contextOwner()
            val observed = context.versionFor(note.id)!!
            assertTrue(observed.contains(":"))
            val resolver = WorkspaceStudySourceResolver(f.context, f.documents, f.workspace, f.files, noPdf(), NotebookAssetRepository(f.context, f.database))
            val study = StudyUseCases(StudyRepository(f.database, f.database.studyDao()), sourceResolver = resolver)
            val actions = WorkspaceToolExecutor(f.notes, f.workspace, context, exactStudyStarter = { id, version -> study.startExactMaterial(id, version, context::versionFor) })
            assertTrue(actions.execute(ToolCall("fixture", "halo_study_with_halo", mapOf("noteId" to note.id, "sourceVersion" to observed))) is ToolExecutionResult.Success)
            assertEquals("image-asset", study.observeActiveSessions().first().single().sources.single().revision)
        }
    }
    @Test fun attachmentChangeBetweenResolutionAndPersistenceCannotCreateAStudySession() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val note = f.text.create("Source", WorkspaceTextFormat.TXT, "Exact text")
            val context = f.contextOwner()
            val observed = context.versionFor(note.id)!!
            val canonical = WorkspaceStudySourceResolver(f.context, f.documents, f.workspace, f.files, noPdf())
            var reads = 0
            val resolver = object : StudySourceResolver by canonical {
                override suspend fun resolve(objectId: String, query: String): ResolvedStudySource {
                    if (++reads == 2) {
                        f.database.workspaceAssetDao().insertAsset(WorkspaceAssetEntity("audio-asset", note.id, "AUDIO", "audio/wav", "MANAGED_FILE", "synthetic-no-read", true, "Audio", 10, null, null, 100, 1))
                        f.database.workspaceAssetDao().insertAttachment(NoteAttachmentEntity("audio-link", note.id, "audio-asset", 0, null, 1))
                    }
                    return canonical.resolve(objectId, query)
                }
            }
            val study = StudyUseCases(StudyRepository(f.database, f.database.studyDao()), sourceResolver = resolver)
            val result = study.startExactMaterial(note.id, observed, context::versionFor)
            assertEquals(StudyUnavailableReason.SOURCE_CHANGED, (result as StudyStartResult.Unavailable).reason)
            assertTrue(study.observeActiveSessions().first().isEmpty())
        }
    }

}
