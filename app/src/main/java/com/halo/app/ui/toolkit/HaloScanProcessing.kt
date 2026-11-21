package com.halo.app.ui.toolkit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.halo.app.di.ManualDependencyGraph as Graph
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** The grown-up configuration owns provider authorization. This boundary accepts only common labels. */
fun interface HaloScanChildSource {
    suspend fun identify(bitmap: Bitmap): String?
    val usesCloud: Boolean get() = false
    val cloudName: String? get() = null
}

object HaloBundledScanChildSource : HaloScanChildSource {
    override suspend fun identify(bitmap: Bitmap): String? = labelFrame(bitmap, childSafe = true)
}

internal data class HaloScanLine(val text: String, val position: Rect?, val paragraph: Int)
internal data class HaloScanLanguage(val code: String, val name: String)

internal val scanLanguages = listOf(
    HaloScanLanguage("en", "English"), HaloScanLanguage("lv", "Latvian"),
    HaloScanLanguage("pl", "Polish"), HaloScanLanguage("ru", "Russian"),
    HaloScanLanguage("de", "German"), HaloScanLanguage("es", "Spanish"),
    HaloScanLanguage("fr", "French"), HaloScanLanguage("uk", "Ukrainian"),
)

internal suspend fun scanFrameText(bitmap: Bitmap): List<HaloScanLine> {
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    try {
        // Local Tasks hold their input until completion, even if the screen is disposed.
        return withContext(NonCancellable) {
            recognizer.process(InputImage.fromBitmap(bitmap, 0)).resultOrThrow()
                .textBlocks.flatMapIndexed { index, block -> block.lines.map { HaloScanLine(it.text, it.boundingBox, index) } }
        }
    } finally { recognizer.close() }
}

internal suspend fun labelFrame(bitmap: Bitmap, childSafe: Boolean = false): String? {
    val labeler = ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(.72f).build())
    try {
        return withContext(NonCancellable) {
            labeler.process(InputImage.fromBitmap(bitmap, 0)).resultOrThrow()
                .sortedByDescending { it.confidence }
                .firstNotNullOfOrNull { if (childSafe) safeChildLabel(it.text) else it.text.takeIf(String::isNotBlank) }
        }
    } finally { labeler.close() }
}

internal fun safeChildLabel(value: String?): String? = value?.trim()?.lowercase(Locale.ROOT)?.takeIf { it in childLabels }

internal val childLabels = setOf(
    "apple", "ball", "banana", "bicycle", "bird", "boat", "book", "butterfly", "car", "cat", "chair", "clock",
    "cloud", "dog", "duck", "fish", "flower", "frog", "fruit", "garden", "guitar", "horse", "leaf", "moon",
    "mushroom", "piano", "plant", "rabbit", "rainbow", "rock", "sea", "shell", "snow", "table", "train", "tree", "turtle",
)

internal suspend fun scanTranslationReady(from: String, to: String): Boolean {
    val models = RemoteModelManager.getInstance()
    return withContext(NonCancellable) {
        setOf(from, to).filter { it != TranslateLanguage.ENGLISH }.all {
            models.isModelDownloaded(TranslateRemoteModel.Builder(it).build()).resultOrThrow()
        }
    }
}

internal suspend fun translateScanLines(lines: List<HaloScanLine>, from: String, to: String,
                                        download: Boolean): List<HaloScanLine> {
    if (from == to) return lines
    val translator = Translation.getClient(TranslatorOptions.Builder()
        .setSourceLanguage(from).setTargetLanguage(to).build())
    try {
        if (download) translator.downloadModelIfNeeded(DownloadConditions.Builder().requireWifi().build()).resultOrThrow()
        check(scanTranslationReady(from, to))
        return withContext(NonCancellable) {
            lines.take(36).map { it.copy(text = translator.translate(it.text).resultOrThrow()) }
        }
    } finally { translator.close() }
}

internal fun captureFileUri(context: Context, file: File) =
    FileProvider.getUriForFile(context, "${context.packageName}.captures", file)

internal suspend fun keepScanPdf(context: Context, bitmap: Bitmap): String {
    val file = withContext(Dispatchers.IO) {
        val staging = File(context.filesDir, "workspace/owned/scan-staging")
        check(staging.isDirectory || staging.mkdirs())
        File.createTempFile("paper-", ".pdf", staging).also { destination ->
            try {
                val pdf = PdfDocument()
                try {
                    val page = pdf.startPage(PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, 1).create())
                    page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                    pdf.finishPage(page)
                    destination.outputStream().use(pdf::writeTo)
                } finally { pdf.close() }
            } catch (error: Exception) {
                destination.delete()
                throw error
            }
        }
    }
    try {
        return Graph.workspaceUseCases.importForCanvas(captureFileUri(context, file), "Scanned paper.pdf", "application/pdf").objectId.value
    } finally { withContext(NonCancellable + Dispatchers.IO) { file.delete() } }
}

internal suspend fun <T> Task<T>.resultOrThrow(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (task.isSuccessful) continuation.resume(task.result)
        else continuation.resumeWithException(task.exception ?: IllegalStateException("Processing was interrupted"))
    }
}
