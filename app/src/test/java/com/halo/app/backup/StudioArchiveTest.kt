package com.halo.app.backup

import com.halo.app.data.studio.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class StudioArchiveTest {
    private val source = StudioDocument(layers = listOf(StudioLayer(id = "image", name = "Original", kind = StudioLayerKind.IMAGE,
        source = StudioSource("old-file", "immutable-sha"))))
    @Test fun duplicationRemapsEveryUndoRedoAndProvenanceSourceWithoutChangingVersion() {
        val session = StudioSession(source).apply(StudioCommand.Background(10)).apply(StudioCommand.Background(20)).undo()
        val ids = mapOf("old-file" to "new-file", "old-project" to "new-project")
        val remapped = StudioSession.decode(StudioArchive.session(session.encode(), ids))
        (remapped.undo + remapped.current + remapped.redo).forEach {
            assertEquals(StudioSource("new-file", "immutable-sha"), it.layers.single().source)
        }
        assertEquals(StudioSource("old-file", "immutable-sha"), source.layers.single().source)
        val metadata = JSONObject().put("operation", "studio-export").put("projectId", "old-project")
            .put("document", JSONObject(StudioDocumentCodec.encode(source))).toString()
        val result = JSONObject(StudioArchive.metadata(metadata, ids))
        assertEquals("new-project", result.getString("projectId"))
        assertEquals("new-file", StudioDocumentCodec.decode(result.getJSONObject("document").toString()).layers.single().source?.objectId)
        assertEquals("opaque external metadata", StudioArchive.metadata("opaque external metadata", ids))
    }
    @Test fun archiveRejectsMissingProjectRowsFutureDocumentsAndDuplicateVersions() {
        val encoded = StudioDocumentCodec.encode(source)
        val header = WorkspaceObjectSnapshot("project", "ARTIFACT", "Project", 1, 1, "USER_CREATED", "ACTIVE", false, "READY", "studio:project")
        val project = StudioProjectSnapshot("project", 2, encoded, StudioSession(source).encode())
        val version = StudioVersionSnapshot("project", 1, encoded, 1)
        val snapshot = emptySnapshot().copy(workspace = listOf(header), studioProjects = listOf(project), studioVersions = listOf(version))
        StudioArchive.validate(snapshot)
        assertTrue(runCatching { StudioArchive.validate(snapshot.copy(studioProjects = emptyList())) }.isFailure)
        assertTrue(runCatching { StudioArchive.validate(snapshot.copy(studioVersions = listOf(version, version))) }.isFailure)
        assertTrue(runCatching { StudioArchive.validate(snapshot.copy(studioProjects = listOf(project.copy(committed = "{\"schema\":99}")))) }.isFailure)
        assertTrue(runCatching { StudioArchive.validate(snapshot.copy(studioVersions = listOf(version.copy(revision = 3)))) }.isFailure)
        StudioArchive.validate(emptySnapshot()) // Older archives legitimately contain no Studio section.
    }
    private fun emptySnapshot() = BrainSnapshot("snapshot", 1, emptyList(), emptyList(), emptyList(), emptyList(),
        emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
        emptyList(), emptyList(), emptyList(), UserSettingsSnapshot(emptyMap(), emptyMap()))
}
