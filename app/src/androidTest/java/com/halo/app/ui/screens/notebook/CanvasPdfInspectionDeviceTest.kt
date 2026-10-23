package com.halo.app.ui.screens.notebook

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.halo.app.data.notebook.WorkspaceObjectId
import com.halo.app.data.workspace.*
import com.halo.app.ui.theme.HaloTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/** Real Android PDF and Compose presentation; no application graph or user documents. */
@RunWith(AndroidJUnit4::class)
class CanvasPdfInspectionDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun realPdfInspectionKeepsPagesFitAndCloseReachableAt200() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val file = File.createTempFile("issue50-inspection-", ".pdf", context.cacheDir)
        val document = PdfDocument()
        try {
            for ((index, color) in listOf(Color.RED, Color.GREEN, Color.BLUE).withIndex()) {
                val page = document.startPage(PdfDocument.PageInfo.Builder(400, 600, index + 1).create())
                page.canvas.drawColor(color)
                document.finishPage(page)
            }
            file.outputStream().use(document::writeTo)
        } finally { document.close() }
        val digest = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
        val source = WorkspaceFile(WorkspaceObjectId("fixture"), Uri.fromFile(file), "Local pages", "application/pdf",
            file.length(), FilePermissionMode.OWNED_COPY, ContentOwnership.HALO_OWNED,
            WorkspaceFileAvailability.OWNED_LOCAL, "fixture-v1")
        val config = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag("ru-RU")) }
        val localized = context.createConfigurationContext(config)
        var closed = false
        try {
            compose.setContent {
                CompositionLocalProvider(LocalContext provides localized, LocalConfiguration provides config,
                    LocalInspectionMode provides true) {
                    HaloTheme(fontScale = 2f) {
                        CanvasMediaInspection("Local pages", null, source, "Local pages", true, {}, { closed = true })
                    }
                }
            }
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("canvas-source-fit").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Следующая страница").assertIsDisplayed().assertIsEnabled().performClick()
            compose.onNodeWithText("Страница 2 из 3").assertIsDisplayed()
            compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Страница 2 из 3").fetchSemanticsNodes().isNotEmpty() }
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            compose.onNodeWithText("Local pages", useUnmergedTree = true)
                .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(2f, layouts.single().layoutInput.density.fontScale, .01f)
            compose.onNodeWithContentDescription("Вписать предпросмотр").assertIsDisplayed().performClick()
            val capture = instrumentation.uiAutomation.takeScreenshot()
            try { File(context.cacheDir, "issue50-pdf-inspection.png").outputStream().use {
                check(capture.compress(Bitmap.CompressFormat.PNG, 100, it))
            } } finally { capture.recycle() }
            compose.onNodeWithContentDescription("Закрыть").assertIsDisplayed().performClick()
            assertTrue(closed)
            assertArrayEquals(digest, MessageDigest.getInstance("SHA-256").digest(file.readBytes()))
        } finally { file.delete() }
    }
}
