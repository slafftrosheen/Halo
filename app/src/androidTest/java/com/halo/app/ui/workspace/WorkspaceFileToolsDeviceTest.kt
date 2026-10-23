package com.halo.app.ui.workspace

import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.halo.app.data.AppDatabase
import com.halo.app.data.notebook.*
import com.halo.app.data.workspace.*
import com.halo.app.ui.theme.HaloTheme
import com.halo.sdk.runtime.ink.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale
import java.util.UUID

/** Native PDF/graphics checks using isolated synthetic files and an isolated Room database. */
@RunWith(AndroidJUnit4::class)
class WorkspaceFileToolsDeviceTest {
    @get:Rule val compose = createComposeRule()
    @Test fun nativePdfAnnotationsExportPageImagesAndPreserveOriginalBytes() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "file-tools-restart-${UUID.randomUUID()}.db"
        var db = Room.databaseBuilder(context, AppDatabase::class.java, databaseName).allowMainThreadQueries().build()
        val root = File(context.cacheDir, "file-tools-device-" + UUID.randomUUID()).also { it.mkdirs() }
        try {
            val files = RoomWorkspaceFileRepository(context, db, root, ownedUri = Uri::fromFile)
            val workspace = RoomWorkspaceRepository(db.notebookDao())
            val versions = WorkspaceFileVersions(files, workspace)
            val documents = NotebookDocumentRepository(db.notebookDocumentDao(), db)
            val notes = RoomNotebookRepository(db.notebookDao(), documents = documents)
            val text = WorkspaceTextDocuments(context, db, notes, documents, files, workspace)
            val savedText = mutableListOf<Triple<String, String, String>>()
            val board = NotebookCanvasRepository(db, db.notebookCanvasDao(), db.notebookDao()).create("Synthetic files")
            for (format in WorkspaceTextFormat.entries) {
                val literal = if (format == WorkspaceTextFormat.CSV) "a,b\r\n1,=1+1" else "# Literal\n**raw**\n"
                val document = text.create("Synthetic file", format, literal)
                val editedText = if (format == WorkspaceTextFormat.CSV) "a,b\r\n2,=2+2" else literal + "Saved revision\n"
                val edited = text.replace(document.id, document.revision, editedText)
                val output = text.export(edited.id, edited.revision)
                assertArrayEquals(editedText.toByteArray(Charsets.UTF_8), context.contentResolver.openInputStream(output.uri)!!.use { it.readBytes() })
                assertEquals(editedText, text.import(output.objectId.value, output.version!!).text)
                NotebookCanvasRepository(db, db.notebookCanvasDao(), db.notebookDao()).addReference(board, output.objectId.value, 0f, 0f)
                savedText += Triple(edited.id, output.objectId.value, editedText)
            }
            val pdfFile = File(root, "synthetic.pdf")
            val pdf = PdfDocument()
            try {
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(300, 400, 1).create())
                page.canvas.drawColor(Color.WHITE)
                page.canvas.drawText("Synthetic offline PDF", 20f, 40f, Paint().apply { color = Color.BLACK; textSize = 18f })
                pdf.finishPage(page)
                pdfFile.outputStream().use(pdf::writeTo)
            } finally { pdf.close() }
            val original = pdfFile.readBytes()
            val source = files.importFile(Uri.fromFile(pdfFile), "synthetic.pdf", "application/pdf", original.size.toLong())
            val annotations = WorkspaceFileAnnotations(context, db, notes, documents, files, versions)
            val initial = annotations.create(WorkspaceFileVersion(source.objectId.value, source.version!!), "Synthetic marks")
            val ink = InkCodec.encode(InkDocument(listOf(InkStroke(InkTool.FINGER, listOf(InkPoint(100, 500), InkPoint(900, 500))))))
            val marked = annotations.setPage(initial.id, initial.revision, 1, ink)
            assertEquals(ink, annotations.get(marked.id)!!.markup.pages[1])
            val output = annotations.exportPageImage(marked.id, marked.revision, 1)
            val bitmap = context.contentResolver.openInputStream(output.uri)!!.use { android.graphics.BitmapFactory.decodeStream(it) }!!
            try { assertTrue(bitmap.width > 0); assertTrue(bitmap.height > 0) } finally { bitmap.recycle() }
            assertTrue(runCatching { annotations.setPage(initial.id, initial.revision, 1, ink) }.isFailure)
            assertArrayEquals(original, pdfFile.readBytes())
            repeat(4) { annotations.preview(marked.id, marked.revision, 1).recycle() }
            NotebookCanvasRepository(db, db.notebookCanvasDao(), db.notebookDao()).addReference(board, output.objectId.value, 0f, 0f, image = true)
            val imageNote = notes.createNote(title = "Synthetic image source")
            val originalImage = android.graphics.Bitmap.createBitmap(80, 40, android.graphics.Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
            val originalImageBytes = try { java.io.ByteArrayOutputStream().also { originalImage.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray() } finally { originalImage.recycle() }
            val imageSource = files.createManagedFile("synthetic.png", "image/png", originalImageBytes, imageNote.id, imageNote.revision.toString(), "{}")
            val receipts = WorkspaceFileReceipts(db, notes, documents)
            val transformed = WorkspaceMediaFiles(context, files, versions, receipts).transform(WorkspaceFileVersion(imageSource.objectId.value, imageSource.version!!), CanvasImageRecipe(quarterTurns = 1, format = CanvasImageFormat.JPEG))
            NotebookCanvasRepository(db, db.notebookCanvasDao(), db.notebookDao()).addReference(board, transformed.objectId.value, 0f, 0f, image = true)

            // Close the actual file-backed database, then reconstruct all readers over the same disk state.
            db.close()
            db = Room.databaseBuilder(context, AppDatabase::class.java, databaseName).allowMainThreadQueries().build()
            val reopenedDocuments = NotebookDocumentRepository(db.notebookDocumentDao(), db)
            val reopenedNotes = RoomNotebookRepository(db.notebookDao(), documents = reopenedDocuments)
            val reopenedWorkspace = RoomWorkspaceRepository(db.notebookDao())
            val reopenedFiles = RoomWorkspaceFileRepository(context, db, root, ownedUri = Uri::fromFile)
            val reopenedText = WorkspaceTextDocuments(context, db, reopenedNotes, reopenedDocuments, reopenedFiles, reopenedWorkspace)
            val reopenedCanvas = NotebookCanvasRepository(db, db.notebookCanvasDao(), db.notebookDao()).observe(board).first()!!
            val pdfPages = object : com.halo.app.study.StudyPdfPages {
                override suspend fun pageCount(file: WorkspaceFile): Int = error("Exported page is an image")
                override suspend fun render(file: WorkspaceFile, page: Int): com.halo.sdk.runtime.multimodal.PreparedImageInput = error("Exported page is an image")
            }
            val study = com.halo.app.study.WorkspaceStudySourceResolver(context, reopenedDocuments, reopenedWorkspace, reopenedFiles, pdfPages)
            for ((noteId, outputId, expected) in savedText) {
                assertEquals(expected, reopenedText.get(noteId)!!.text)
                val file = reopenedFiles.getFile(WorkspaceObjectId(outputId))!!
                assertArrayEquals(expected.toByteArray(Charsets.UTF_8), context.contentResolver.openInputStream(file.uri)!!.use { it.readBytes() })
                assertTrue(reopenedCanvas.nodes.any { it.refObjectId == outputId })
                assertTrue(study.resolveExplicitReference(outputId, "") is com.halo.app.study.ResolvedStudySource.Text)
            }
            val reopenedMarks = WorkspaceFileAnnotations(context, db, reopenedNotes, reopenedDocuments, reopenedFiles, WorkspaceFileVersions(reopenedFiles, reopenedWorkspace)).get(marked.id)!!
            assertEquals(ink, reopenedMarks.markup.pages[1])
            for (outputId in listOf(output.objectId.value, transformed.objectId.value)) {
                assertTrue(reopenedCanvas.nodes.any { it.refObjectId == outputId })
                assertTrue(study.resolveExplicitReference(outputId, "") is com.halo.app.study.ResolvedStudySource.Visual)
            }
            val reopenedImage = reopenedFiles.getFile(transformed.objectId)!!
            val rotated = context.contentResolver.openInputStream(reopenedImage.uri)!!.use { android.graphics.BitmapFactory.decodeStream(it) }!!
            try { assertEquals(40, rotated.width); assertEquals(80, rotated.height) } finally { rotated.recycle() }
            assertArrayEquals(originalImageBytes, context.contentResolver.openInputStream(reopenedFiles.getFile(imageSource.objectId)!!.uri)!!.use { it.readBytes() })
            assertArrayEquals(original, pdfFile.readBytes())
            assertTrue(db.memoryRecordDao().observeActiveRecords().first().isEmpty())
        } finally {
            db.close()
            check(databaseName.startsWith("file-tools-restart-") && !databaseName.contains('/') && !databaseName.contains('\\'))
            context.deleteDatabase(databaseName)
            check(root.canonicalFile.parentFile == context.cacheDir.canonicalFile)
            root.deleteRecursively()
        }
    }
    @Test fun russianTableKeepsCloseReachableAt200Percent() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag("ru-RU")) }
        val localized = context.createConfigurationContext(config)
        var dismissed = false
        val block = NoteBlock("block", 0, NoteBlockType.CODE, "Name,Value\r\nSynthetic,=1+1", language = WorkspaceTextFormat.CSV.blockLanguage)
        val document = WorkspaceTextDocument(NotebookDocument("fixture", "Synthetic table", 1, listOf(block), WorkspaceLifecycle.ACTIVE, 1, 1), WorkspaceTextFormat.CSV, block)
        compose.setContent {
            CompositionLocalProvider(LocalContext provides localized, LocalConfiguration provides config, LocalInspectionMode provides true) {
                HaloTheme(fontScale = 2f) {
                    WorkspaceCsvDialog(document, false, false, WorkspaceTextDocuments.HistoryStatus(), { _, _, _ -> }, {}, {}, { dismissed = true })
                }
            }
        }
        compose.onNodeWithText(localized.getString(com.halo.app.R.string.file52_done)).assertIsDisplayed().performClick()
        assertTrue(dismissed)
    }
}
