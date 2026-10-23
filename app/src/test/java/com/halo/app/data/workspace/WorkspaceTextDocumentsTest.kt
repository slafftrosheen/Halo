package com.halo.app.data.workspace

import com.halo.app.data.notebook.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class WorkspaceTextDocumentsTest {
    @Test fun literalFilesSurviveEditReopenAndExactExport() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            for (format in WorkspaceTextFormat.entries) {
                val raw = if (format == WorkspaceTextFormat.CSV) "name,value\r\nhello,=1+1" else "# Heading\n```literal\n**unchanged**\n```\n"
                val created = f.text.create("Local file", format, raw)
                val updated = f.text.replace(created.id, created.revision, raw + "\n")
                val reopened = WorkspaceTextDocuments(f.context, f.database, f.notes, f.documents, f.files, f.workspace).get(created.id)!!
                assertEquals(updated.revision, reopened.revision)
                assertEquals(raw + "\n", reopened.text)
                val output = f.text.export(reopened.id, reopened.revision)
                assertArrayEquals((raw + "\n").toByteArray(Charsets.UTF_8), f.bytes(output))
                assertEquals(format.mime, output.mimeType)
                assertEquals(WorkspaceFileToolsKind.FILE, f.tools.get(output.objectId.value).kind)
            }
        }
    }
    @Test fun staleRevisionAndWrongSelectedTextNeverOverwrite() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val first = f.text.create("Draft", WorkspaceTextFormat.TXT, "hello world")
            val changed = f.text.replaceRange(first.id, first.revision, 6, 11, "world", "Halo")
            assertTrue(runCatching { f.text.replace(first.id, first.revision, "stale") }.isFailure)
            assertTrue(runCatching { f.text.replaceRange(first.id, changed.revision, 0, 5, "wrong", "oops") }.isFailure)
            assertEquals("hello Halo", f.text.get(first.id)!!.text)
        }
    }
    @Test fun undoRedoIsRevisionBoundAndExternalEditStopsHistory() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val first = f.text.create("Draft", WorkspaceTextFormat.TXT, "one")
            val second = f.text.replace(first.id, first.revision, "two")
            val undone = f.text.undo(first.id, second.revision)
            assertEquals("one", undone.text)
            val redone = f.text.redo(first.id, undone.revision)
            assertEquals("two", redone.text)
            assertTrue(f.documents.apply(first.id, redone.revision, DocumentCommand.SetText(redone.block.id, "external")) is CommitOutcome.Committed)
            val external = f.text.get(first.id)!!
            assertFalse(f.text.historyStatus(first.id, external.revision).canUndo)
            assertTrue(runCatching { f.text.undo(first.id, external.revision) }.isFailure)
            assertEquals("external", f.text.get(first.id)!!.text)
        }
    }
    @Test fun unfinishedCsvRemainsReadableButCannotExportAsValidTable() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val first = f.text.create("Table", WorkspaceTextFormat.CSV, "a,b")
            assertTrue(f.documents.apply(first.id, first.revision, DocumentCommand.SetText(first.block.id, "\"")) is CommitOutcome.Committed)
            val draft = f.text.get(first.id)!!
            assertEquals("\"", draft.text)
            assertTrue(runCatching { f.text.export(first.id, draft.revision) }.isFailure)
            val repaired = f.text.replace(first.id, draft.revision, "a,b")
            assertEquals("a,b", repaired.text)
        }
    }
    @Test fun attachmentRetainsExactSourceAndRejectsStaleVersion() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val text = f.text.create("Attached", WorkspaceTextFormat.MARKDOWN, "# Source")
            val canvas = f.canvases.create("Synthetic canvas")
            f.tools.attachToCanvas(canvas, text.id, text.revision.toString())
            assertEquals(1, f.canvases.observe(canvas).first()!!.nodes.size)
            assertTrue(runCatching { f.tools.attachToCanvas(canvas, text.id, "stale") }.isFailure)
            assertEquals(1, f.canvases.observe(canvas).first()!!.nodes.size)
        }
    }
    @Test fun exactTextOutputsResolveInStudyAndTrashedSourcesAreRejected() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val pdf = object : com.halo.app.study.StudyPdfPages {
                override suspend fun pageCount(file: WorkspaceFile): Int = error("No PDF in this fixture")
                override suspend fun render(file: WorkspaceFile, page: Int): com.halo.sdk.runtime.multimodal.PreparedImageInput = error("No PDF in this fixture")
            }
            val resolver = com.halo.app.study.WorkspaceStudySourceResolver(f.context, f.documents, f.workspace, f.files, pdf)
            for (format in WorkspaceTextFormat.entries) {
                val raw = if (format == WorkspaceTextFormat.CSV) "Name,Value\r\nSynthetic,=1+1" else "# Synthetic source\n**literal**"
                val document = f.text.create("Synthetic source", format, raw)
                val output = f.text.export(document.id, document.revision)
                val material = resolver.resolveExplicitReference(output.objectId.value, "Synthetic") as com.halo.app.study.ResolvedStudySource.Text
                assertEquals(output.version, material.revision)
                assertTrue(material.passages.any { it.text.contains("Synthetic") })
                f.workspace.trash(output.objectId)
                assertTrue(resolver.resolveExplicitReference(output.objectId.value, "Synthetic") is com.halo.app.study.ResolvedStudySource.Unsupported)
            }
        }
    }
    @Test fun emptyTextPublishesAsDurableOwnedFileAndImportsExactly() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val text = f.text.create("Empty", WorkspaceTextFormat.TXT)
            val output = f.text.export(text.id, text.revision)
            assertEquals(0, f.bytes(output).size)
            assertNotNull(output.sha256)
            val imported = f.text.import(output.objectId.value, output.version!!)
            assertEquals("", imported.text)
            assertNotEquals(text.id, imported.id)
        }
    }
}
