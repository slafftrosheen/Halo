package com.halo.app.data.workspace

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * OneBox-inspired non-destructive composition. An output bitmap plus at most one
 * decoded source are resident concurrently; input streams and buffers close promptly.
 */
object CanvasImageStitcher {
    suspend fun preview(
        context: Context, sources: List<WorkspaceFile>, recipe: CanvasStitchRecipe,
    ): Bitmap = render(context, sources, recipe, preview = true)

    suspend fun export(
        context: Context, sources: List<WorkspaceFile>, recipe: CanvasStitchRecipe,
    ): ByteArray = withContext(Dispatchers.IO) {
        val bitmap = render(context, sources, recipe, preview = false)
        try {
            coroutineContext.ensureActive()
            val buffer = ByteArrayOutputStream()
            val compression = if (recipe.format == CanvasImageFormat.JPEG) {
                Bitmap.CompressFormat.JPEG
            } else Bitmap.CompressFormat.PNG
            check(bitmap.compress(compression, 86, buffer)) { "Could not encode combined image." }
            val bytes = buffer.toByteArray()
            require(bytes.size in 1..20_000_000) { "Combined image is too large." }
            coroutineContext.ensureActive()
            bytes
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun render(
        context: Context, sources: List<WorkspaceFile>, recipe: CanvasStitchRecipe, preview: Boolean,
    ): Bitmap = withContext(Dispatchers.IO) {
        require(sources.size in 2..6 && sources.map { it.objectId.value }.distinct().size == sources.size) {
            "Select between 2 and 6 different image files."
        }
        val dimensions = sources.map { source ->
            require(source.mimeType.startsWith("image/")) { "Only image files can be combined." }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(source.uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            CanvasImageSize(bounds.outWidth, bounds.outHeight)
        }
        val layout = CanvasStitchLayout.plan(dimensions, recipe, if (preview) 640 else null)
        val result = Bitmap.createBitmap(layout.width, layout.height, Bitmap.Config.ARGB_8888)
        try {
            result.eraseColor(if (recipe.background == CanvasStitchBackground.WHITE ||
                recipe.format == CanvasImageFormat.JPEG) Color.WHITE else Color.TRANSPARENT)
            val canvas = Canvas(result)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            sources.forEachIndexed { index, source ->
                coroutineContext.ensureActive()
                val image = CanvasImageTools.stitchSource(context, source, preview)
                try {
                    val position = layout.placements[index]
                    val destination = RectF(position.x.toFloat(), position.y.toFloat(),
                        (position.x + position.width).toFloat(), (position.y + position.height).toFloat())
                    canvas.drawBitmap(image, Rect(0, 0, image.width, image.height), destination, paint)
                } finally {
                    image.recycle()
                }
            }
            coroutineContext.ensureActive()
            result
        } catch (failure: Throwable) {
            result.recycle()
            throw failure
        }
    }
}
