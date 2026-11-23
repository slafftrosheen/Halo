package com.halo.app.data.studio

import android.content.Context
import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.halo.app.data.notebook.WorkspaceObjectId
import com.halo.app.data.workspace.ContentOwnership
import com.halo.app.data.workspace.CanvasImageTools
import com.halo.app.data.workspace.WorkspaceFileRepository
import com.halo.app.data.workspace.WorkspaceFileAvailability
import com.halo.sdk.runtime.ink.InkCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.min
import kotlin.math.roundToInt

/** Serial local composition; only the output and one bounded image layer are decoded at once. */
class StudioRenderer(private val context: Context, private val files: WorkspaceFileRepository,
    private val workspace: com.halo.app.data.workspace.WorkspaceRepository) {
    private val rendering = Semaphore(1)
    suspend fun render(document: StudioDocument, preview: Boolean): Bitmap {
        document.requireValid()
        var owned: Bitmap? = null
        try {
            val result = rendering.withPermit { withContext(Dispatchers.IO) {
                val edge = min(document.recipe.maxEdge, if (preview) 640 else 4096)
                val scale = min(1f, edge.toFloat() / maxOf(document.width, document.height))
                val width = (document.width * scale).roundToInt().coerceAtLeast(1)
                val height = (document.height * scale).roundToInt().coerceAtLeast(1)
                val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                owned = output
                output.eraseColor(document.background)
                val canvas = Canvas(output)
                for (layer in document.layers) {
                    coroutineContext.ensureActive()
                    if (!layer.visible || layer.opacity == 0) continue
                    drawLayer(canvas, layer, preview, document)
                }
                CanvasImageTools.transformOwnedBitmap(output, document.recipe, preview).also { owned = it }
            } }
            owned = null
            return result
        } finally { owned?.takeUnless { it.isRecycled }?.recycle() }
    }

    internal suspend fun exportPdf(document: StudioDocument): ByteArray = rendering.withPermit { withContext(Dispatchers.IO) {
        document.requireValid()
        require(document.layers.none { it.source != null })
        val recipe = document.recipe
        require(recipe.crop == null && !recipe.squareCrop && !recipe.mirrorHorizontal && recipe.quarterTurns == 0)
        val width = (document.width * 72.0 / document.dpi).roundToInt().coerceAtLeast(1)
        val height = (document.height * 72.0 / document.dpi).roundToInt().coerceAtLeast(1)
        val pdf = android.graphics.pdf.PdfDocument()
        try {
            val page = pdf.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(width, height, 1).create())
            try {
                if (document.background ushr 24 != 0) page.canvas.drawColor(document.background)
                for (layer in document.layers) {
                    coroutineContext.ensureActive()
                    if (layer.visible && layer.opacity > 0) drawLayer(page.canvas, layer, false, document)
                }
            } finally { pdf.finishPage(page) }
            java.io.ByteArrayOutputStream().use { output -> pdf.writeTo(output); output.toByteArray() }
        } finally { pdf.close() }
    } }

    /** Shows the other layers in the exact coordinate rectangle used by this Ink layer. */
    suspend fun inkBackdrop(document: StudioDocument, layer: StudioLayer): Bitmap {
        require(layer.kind == StudioLayerKind.INK && document.layers.any { it == layer })
        val background = render(document.copy(layers = document.layers.filterNot { it.id == layer.id }, selectedLayerId = null,
            recipe = document.recipe.copy(quarterTurns = 0, mirrorHorizontal = false, squareCrop = false, crop = null)), true)
        var owned: Bitmap? = null
        try {
            val result = withContext(Dispatchers.IO) {
                val bounds = layer.bounds.pixels(background.width, background.height)
                val frame = inkFrame(layer, bounds)
                val renderScale = 640f / maxOf(frame.width(), frame.height()).coerceAtLeast(.001f)
                val bitmap = Bitmap.createBitmap((frame.width() * renderScale).roundToInt().coerceIn(1, 640),
                    (frame.height() * renderScale).roundToInt().coerceIn(1, 640), Bitmap.Config.ARGB_8888)
                owned = bitmap
                Canvas(bitmap).apply {
                    scale(bitmap.width / frame.width(), bitmap.height / frame.height())
                    translate(-frame.left, -frame.top)
                    rotate(-layer.rotation.toFloat(), bounds.centerX(), bounds.centerY())
                    drawBitmap(background, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                }
                coroutineContext.ensureActive()
                bitmap
            }
            owned = null
            return result
        } finally { background.recycle(); owned?.takeUnless { it.isRecycled }?.recycle() }
    }
    internal fun inkWidthFraction(document: StudioDocument, layer: StudioLayer): Float {
        val frame = inkFrame(layer, layer.bounds.pixels(document.width, document.height))
        return layer.strokeWidth * maxOf(document.width, document.height) / 1000f / frame.width()
    }
    private fun inkFrame(layer: StudioLayer, target: RectF): RectF {
        val document = requireNotNull(InkCodec.decode(layer.ink))
        if (document.aspectMilli == 0) return target
        val aspect = document.aspectMilli / 1000f
        val width = min(target.width(), target.height() * aspect)
        val height = width / aspect
        return RectF(target.centerX() - width / 2, target.centerY() - height / 2,
            target.centerX() + width / 2, target.centerY() + height / 2)
    }
    private suspend fun drawLayer(canvas: Canvas, layer: StudioLayer, preview: Boolean, document: StudioDocument) {
        val bounds = layer.bounds.pixels(canvas.width, canvas.height)
        val transform = canvas.save()
        try {
            canvas.rotate(layer.rotation.toFloat(), bounds.centerX(), bounds.centerY())
            canvas.clipRect(bounds)
            layer.mask?.let { mask ->
                canvas.clipRect(bounds.left + mask.x * bounds.width() / 1000,
                    bounds.top + mask.y * bounds.height() / 1000,
                    bounds.left + (mask.x + mask.width) * bounds.width() / 1000,
                    bounds.top + (mask.y + mask.height) * bounds.height() / 1000)
            }
            val matrix = ColorMatrix().apply { setSaturation(layer.saturation / 1000f) }
            val contrast = layer.contrast / 1000f
            val offset = 128f * (1f - contrast) + layer.brightness * 255f / 1000f
            matrix.postConcat(ColorMatrix(floatArrayOf(contrast,0f,0f,0f,offset, 0f,contrast,0f,0f,offset,
                0f,0f,contrast,0f,offset, 0f,0f,0f,1f,0f)))
            val layerPaint = Paint().apply { alpha = layer.opacity * 255 / 1000; colorFilter = ColorMatrixColorFilter(matrix) }
            val saved = canvas.saveLayer(bounds, layerPaint)
            try {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = layer.color }
                when (layer.kind) {
                    StudioLayerKind.IMAGE -> drawImage(canvas, layer, bounds, preview)
                    StudioLayerKind.RECTANGLE, StudioLayerKind.ELLIPSE -> {
                        val vector = layer.vector
                        if (vector == null) {
                            if (layer.kind == StudioLayerKind.RECTANGLE) canvas.drawRect(bounds, paint) else canvas.drawOval(bounds, paint)
                        } else {
                            if (vector.outlineOnly) {
                                paint.style = Paint.Style.STROKE
                                paint.strokeWidth = min(bounds.width(), bounds.height()) * vector.stroke
                                paint.strokeCap = Paint.Cap.ROUND
                                if (vector.dash) paint.pathEffect = DashPathEffect(floatArrayOf(paint.strokeWidth * 4, paint.strokeWidth * 3), 0f)
                            }
                            canvas.drawPath(vector.path(bounds), paint)
                            if (vector.shape == StudioShape.DIMENSION) {
                                paint.style = Paint.Style.FILL
                                paint.pathEffect = null
                                paint.textAlign = Paint.Align.CENTER
                                paint.textSize = min(bounds.width(), bounds.height()) * .18f
                                val mm = StudioLengthUnit.MM.fromPixels(layer.bounds.width * document.width * .0008, document.dpi)
                                canvas.drawText(String.format(java.util.Locale.ROOT, "%.2f mm", mm),
                                    bounds.centerX(), bounds.top + bounds.height() * .56f, paint)
                            }
                        }
                    }
                    StudioLayerKind.TEXT -> {
                        val textPaint = TextPaint(paint).apply {
                            textSize = (layer.fontSize * canvas.width / 1000f).coerceAtLeast(1f)
                        }
                        val layout = StaticLayout.Builder.obtain(layer.text, 0, layer.text.length, textPaint,
                            bounds.width().toInt().coerceAtLeast(1)).setAlignment(Layout.Alignment.ALIGN_NORMAL)
                            .setIncludePad(false).setMaxLines(200).setEllipsize(TextUtils.TruncateAt.END).build()
                        canvas.translate(bounds.left, bounds.top)
                        layout.draw(canvas)
                    }
                    StudioLayerKind.INK -> drawInk(canvas, layer, bounds, paint)
                }
            } finally { canvas.restoreToCount(saved) }
        } finally { canvas.restoreToCount(transform) }
    }
    private suspend fun drawImage(canvas: Canvas, layer: StudioLayer, bounds: RectF, preview: Boolean) {
        val source = requireNotNull(layer.source)
        require(workspace.get(WorkspaceObjectId(source.objectId))?.lifecycle ==
            com.halo.app.data.notebook.WorkspaceObjectLifecycle.ACTIVE) { "Image source unavailable" }
        val file = requireNotNull(files.getFile(WorkspaceObjectId(source.objectId))) { "Image source unavailable" }
        require(file.version == source.version && file.sha256 != null && file.ownership == ContentOwnership.HALO_OWNED && file.mimeType.startsWith("image/") &&
            file.availability in setOf(WorkspaceFileAvailability.AVAILABLE, WorkspaceFileAvailability.OWNED_LOCAL)) {
            "Image source changed. Reopen it before editing."
        }
        val image = CanvasImageTools.stitchSource(context, file, preview)
        try {
            val scale = min(bounds.width() / image.width, bounds.height() / image.height)
            val width = image.width * scale; val height = image.height * scale
            val target = RectF(bounds.centerX() - width / 2, bounds.centerY() - height / 2,
                bounds.centerX() + width / 2, bounds.centerY() + height / 2)
            canvas.drawBitmap(image, null, target, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            require(workspace.get(WorkspaceObjectId(source.objectId))?.lifecycle ==
                com.halo.app.data.notebook.WorkspaceObjectLifecycle.ACTIVE &&
                files.getFile(WorkspaceObjectId(source.objectId))?.version == source.version) { "Image source changed during preview" }
            coroutineContext.ensureActive()
        } finally { image.recycle() }
    }
    private fun drawInk(canvas: Canvas, layer: StudioLayer, target: RectF, paint: Paint) {
        val document = requireNotNull(InkCodec.decode(layer.ink))
        val rect = inkFrame(layer, target)
        val thickness = layer.strokeWidth * maxOf(canvas.width, canvas.height) / 1000f
        val symmetry = layer.symmetry
        repeat(symmetry?.repeats ?: 1) { index ->
            val restore = canvas.save()
            try {
                if (symmetry != null) canvas.clipRect(rect)
                if (index > 0 && symmetry != null) when (symmetry.axis) {
                    StudioSymmetryAxis.VERTICAL -> canvas.scale(-1f, 1f, rect.centerX(), rect.centerY())
                    StudioSymmetryAxis.HORIZONTAL -> canvas.scale(1f, -1f, rect.centerX(), rect.centerY())
                    StudioSymmetryAxis.RADIAL -> canvas.rotate(index * 360f / symmetry.repeats, rect.centerX(), rect.centerY())
                }
                com.halo.app.data.workspace.WorkspaceInkBitmapRenderer.draw(canvas, document, rect, paint, thickness, hardPencil = layer.pencil)
            } finally { canvas.restoreToCount(restore) }
        }
    }

    private fun StudioRect.pixels(width: Int, height: Int) = RectF(x * width / 1000f, y * height / 1000f,
        (x + this.width) * width / 1000f, (y + this.height) * height / 1000f)
}
