package com.halo.app.data.workspace

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Android-only local image transformations; no model, network or second storage owner.
 * Preview and export use the same transforms and hard bounded decode.
 */
object CanvasImageTools {
    private const val MAX_PIXELS = 12_000_000L
    private const val MAX_OUTPUT_BYTES = 20_000_000

    suspend fun preview(context: Context, file: WorkspaceFile, recipe: CanvasImageRecipe): Bitmap =
        render(context, file, recipe, preview = true)

    suspend fun export(context: Context, file: WorkspaceFile, recipe: CanvasImageRecipe): ByteArray =
        withContext(Dispatchers.IO) {
            val bitmap = render(context, file, recipe, preview = false)
            try {
                coroutineContext.ensureActive()
                val stream = ByteArrayOutputStream()
                val format = when (recipe.format) {
                    CanvasImageFormat.PNG -> Bitmap.CompressFormat.PNG
                    CanvasImageFormat.JPEG -> Bitmap.CompressFormat.JPEG
                }
                if (recipe.format == CanvasImageFormat.JPEG && bitmap.hasAlpha()) {
                    val opaque = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
                    try {
                        opaque.eraseColor(android.graphics.Color.WHITE)
                        android.graphics.Canvas(opaque).drawBitmap(bitmap, 0f, 0f, null)
                        check(opaque.compress(format, 86, stream))
                    } finally { opaque.recycle() }
                } else check(bitmap.compress(format, 86, stream))
                val bytes = stream.toByteArray()
                require(bytes.size in 1..MAX_OUTPUT_BYTES) { "Edited image exceeds 20 MB." }
                coroutineContext.ensureActive()
                bytes
            } finally { bitmap.recycle() }
        }

    private suspend fun render(context: Context, file: WorkspaceFile, recipe: CanvasImageRecipe, preview: Boolean): Bitmap =
        withContext(Dispatchers.IO) {
            require(file.mimeType.startsWith("image/")) { "Select an image file." }
            require(file.availability in setOf(WorkspaceFileAvailability.AVAILABLE, WorkspaceFileAvailability.OWNED_LOCAL)) {
                "Image unavailable. Relink it in Workspace."
            }
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(file.uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            require(bounds.outWidth in 1..32768 && bounds.outHeight in 1..32768) { "Unsupported image dimensions." }
            val sourceLimit = if (preview) 640 else 4096
            var sample = 1
            while (maxOf(bounds.outWidth / sample, bounds.outHeight / sample) > sourceLimit ||
                bounds.outWidth.toLong() * bounds.outHeight / sample / sample > MAX_PIXELS) sample *= 2
            coroutineContext.ensureActive()
            var working: Bitmap? = resolver.openInputStream(file.uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                })
            }
            checkNotNull(working) { "Image could not be decoded." }
            try {
                if (recipe.squareCrop) {
                    val current = requireNotNull(working)
                    val side = minOf(current.width, current.height)
                    val cropped = Bitmap.createBitmap(current, (current.width - side) / 2, (current.height - side) / 2, side, side)
                    if (cropped !== current) { current.recycle(); working = cropped }
                }
                if (recipe.quarterTurns != 0 || recipe.mirrorHorizontal) {
                    val current = requireNotNull(working)
                    val matrix = Matrix().apply {
                        if (recipe.mirrorHorizontal) postScale(-1f, 1f)
                        if (recipe.quarterTurns != 0) postRotate(recipe.quarterTurns * 90f)
                    }
                    val adjusted = Bitmap.createBitmap(current, 0, 0, current.width, current.height, matrix, true)
                    if (adjusted !== current) { current.recycle(); working = adjusted }
                }
                val current = requireNotNull(working)
                val (width, height) = recipe.outputDimensions(current.width, current.height,
                    if (preview) 640 else null)
                if (current.width != width || current.height != height) {
                    val resized = Bitmap.createScaledBitmap(current, width, height, true)
                    if (resized !== current) { current.recycle(); working = resized }
                }
                coroutineContext.ensureActive()
                val result = requireNotNull(working)
                working = null
                result
            } finally { working?.takeUnless { it.isRecycled }?.recycle() }
        }
}
