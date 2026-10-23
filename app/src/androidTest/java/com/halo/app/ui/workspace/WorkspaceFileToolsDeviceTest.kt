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
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val root = File(context.cacheDir, "file-tools-device-" + UUID.randomUUID()).also { it.mkdirs() }
        try {
            val files = RoomWorkspaceFileRepository(context, db, root, ownedUri = Uri::fromFile)
            val workspace = RoomWorkspaceRepository(db.notebookDao())
            val versions = WorkspaceFileVersions(files, workspace)
            val documents = NotebookDocumentRepository(db.notebookDocumentDao(), db)
            val notes = RoomNotebookRepository(db.notebookDao(), documents = documents)
            val text = WorkspaceTextDocuments(context, db, notes, documents, files, workspace)
            for (format in WorkspaceTextFormat.entries) {
                val literal = if (format == WorkspaceTextFormat.CSV) "a,b\r\n1,=1+1" else "# Literal\n**raw**\n"
                val document = text.create("Synthetic file", format, literal)
                val output = text.export(document.id, document.revision)
                assertArrayEquals(literal.toByteArray(Charsets.UTF_8), context.contentResolver.openInputStream(output.uri)!!.use { it.readBytes() })
                assertEquals(literal, text.import(output.objectId.value, output.version!!).text)
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
        } finally {
            db.close()
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
