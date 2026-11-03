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
                for (layer in document.layers.filter { it.parentId == null }) {
                    coroutineContext.ensureActive()
                    if (!layer.visible || layer.opacity == 0) continue
                    drawNode(canvas, output, document, layer, preview)
                }
                CanvasImageTools.transformOwnedBitmap(output, document.recipe, preview).also { owned = it }
            } }
            owned = null
            return result
        } catch(capacity:OutOfMemoryError) {throw IllegalStateException("Not enough memory to render this project. The saved layers are kept.",capacity)}
        finally { owned?.takeUnless { it.isRecycled }?.recycle() }
    }

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
    private suspend fun drawNode(canvas: Canvas, destination:Bitmap, document: StudioDocument, layer: StudioLayer, preview: Boolean) {
        coroutineContext.ensureActive()
        if (!layer.visible || layer.opacity == 0) return
        if (layer.kind != StudioLayerKind.GROUP && layer.effects.none { it.enabled } && layer.blend == StudioBlend.NORMAL) {
            drawLayer(canvas,layer,preview);return
        }
        // A compositing group/effect owns one bounded temporary buffer, independently of export dimensions.
        val ratio = min(1f,1600f/maxOf(canvas.width,canvas.height))
        val width=(canvas.width*ratio).roundToInt().coerceAtLeast(1)
        val height=(canvas.height*ratio).roundToInt().coerceAtLeast(1)
        val pixels=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
        try {
            val target=Canvas(pixels)
            if(layer.kind==StudioLayerKind.GROUP) {
                for(child in document.layers.filter { it.parentId==layer.id }) drawNode(target,pixels,document,child,preview)
                layer.mask?.let { mask ->
                    val bounds=layer.bounds.pixels(width,height)
                    val clip=RectF(bounds.left+mask.x*bounds.width()/1000,bounds.top+mask.y*bounds.height()/1000,
                        bounds.left+(mask.x+mask.width)*bounds.width()/1000,bounds.top+(mask.y+mask.height)*bounds.height()/1000)
                    val p=Paint().apply { xfermode=PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
                    val maskBitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
                    try { Canvas(maskBitmap).drawRect(clip,Paint().apply { color=Color.WHITE });target.drawBitmap(maskBitmap,0f,0f,p) }
                    finally { maskBitmap.recycle() }
                }
            } else drawLayer(target,layer.copy(opacity=1000,brightness=0,contrast=1000,saturation=1000),preview)
            StudioEffectRenderer.apply(pixels,layer.effects)
            if(layer.blend==StudioBlend.MULTIPLY && android.os.Build.VERSION.SDK_INT<29) {
                StudioBlendRenderer.multiply(destination,pixels,layer);return
            }
            val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                alpha=layer.opacity*255/1000;colorFilter=adjustmentFilter(layer)
                xfermode=PorterDuffXfermode(when(layer.blend) {
                    StudioBlend.NORMAL->PorterDuff.Mode.SRC_OVER;StudioBlend.MULTIPLY->PorterDuff.Mode.MULTIPLY
                    StudioBlend.SCREEN->PorterDuff.Mode.SCREEN;StudioBlend.OVERLAY->PorterDuff.Mode.OVERLAY
                    StudioBlend.DARKEN->PorterDuff.Mode.DARKEN;StudioBlend.LIGHTEN->PorterDuff.Mode.LIGHTEN;StudioBlend.ADD->PorterDuff.Mode.ADD
                })
                if(android.os.Build.VERSION.SDK_INT>=29) blendMode=when(layer.blend) {
                    StudioBlend.NORMAL->BlendMode.SRC_OVER;StudioBlend.MULTIPLY->BlendMode.MULTIPLY
                    StudioBlend.SCREEN->BlendMode.SCREEN;StudioBlend.OVERLAY->BlendMode.OVERLAY
                    StudioBlend.DARKEN->BlendMode.DARKEN;StudioBlend.LIGHTEN->BlendMode.LIGHTEN;StudioBlend.ADD->BlendMode.PLUS
                }
            }
            canvas.drawBitmap(pixels,null,RectF(0f,0f,canvas.width.toFloat(),canvas.height.toFloat()),paint)
        } finally { pixels.recycle() }
    }
    private fun adjustmentFilter(layer: StudioLayer): ColorMatrixColorFilter {
        val matrix=ColorMatrix().apply { setSaturation(layer.saturation/1000f) }
        val contrast=layer.contrast/1000f
        val offset=128f*(1f-contrast)+layer.brightness*255f/1000f
        matrix.postConcat(ColorMatrix(floatArrayOf(contrast,0f,0f,0f,offset,0f,contrast,0f,0f,offset,
            0f,0f,contrast,0f,offset,0f,0f,0f,1f,0f)))
        return ColorMatrixColorFilter(matrix)
    }
    private suspend fun drawLayer(canvas: Canvas, layer: StudioLayer, preview: Boolean) {
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
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = layer.color
                    if(layer.strokeOnly) {style=Paint.Style.STROKE;strokeWidth=layer.strokeWidth*canvas.width/1000f}
                }
                when (layer.kind) {
                    StudioLayerKind.IMAGE -> drawImage(canvas, layer, bounds, preview)
                    StudioLayerKind.RECTANGLE -> canvas.drawRect(RectF(bounds).apply {if(layer.strokeOnly) inset(min(paint.strokeWidth/2,width()/2),min(paint.strokeWidth/2,height()/2))}, paint)
                    StudioLayerKind.ELLIPSE -> canvas.drawOval(RectF(bounds).apply {if(layer.strokeOnly) inset(min(paint.strokeWidth/2,width()/2),min(paint.strokeWidth/2,height()/2))}, paint)
                    StudioLayerKind.LINE -> { paint.strokeWidth=layer.strokeWidth*canvas.width/1000f; canvas.drawLine(bounds.left,bounds.top,bounds.right,bounds.bottom,paint) }
                    StudioLayerKind.TEXTURE -> StudioTextureRenderer.draw(canvas,bounds,requireNotNull(layer.texture),layer.color,layer.textureSeed)
                    StudioLayerKind.GROUP -> error("Groups must be composited through their hierarchy")
                    StudioLayerKind.TEXT -> {
                        val textPaint = TextPaint(paint).apply {
                            textSize = (layer.fontSize * canvas.width / 1000f).coerceAtLeast(1f)
                            typeface=Typeface.create(when(layer.typography.family) {
                                StudioFont.SANS->"sans-serif";StudioFont.SERIF->"serif";StudioFont.MONOSPACE->"monospace"
                            }, (if(layer.typography.weight==700) Typeface.BOLD else Typeface.NORMAL) or
                                (if(layer.typography.italic) Typeface.ITALIC else Typeface.NORMAL))
                            letterSpacing=layer.typography.letterSpacing/1000f
                        }
                        val layout = StaticLayout.Builder.obtain(layer.text, 0, layer.text.length, textPaint,
                            bounds.width().toInt().coerceAtLeast(1)).setAlignment(when(layer.typography.alignment) {
                                StudioTextAlignment.START->Layout.Alignment.ALIGN_NORMAL;StudioTextAlignment.CENTER->Layout.Alignment.ALIGN_CENTER;StudioTextAlignment.END->Layout.Alignment.ALIGN_OPPOSITE
                            }).setLineSpacing(0f,layer.typography.lineSpacing/1000f)
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
            val crop=layer.imageCrop ?: StudioRect()
            val sourceRect=Rect(crop.x*image.width/1000,crop.y*image.height/1000,
                ((crop.x+crop.width)*image.width/1000).coerceAtLeast(crop.x*image.width/1000+1).coerceAtMost(image.width),
                ((crop.y+crop.height)*image.height/1000).coerceAtLeast(crop.y*image.height/1000+1).coerceAtMost(image.height))
            val scale = min(bounds.width() / sourceRect.width(), bounds.height() / sourceRect.height())
            val width = sourceRect.width() * scale; val height = sourceRect.height() * scale
            val target = RectF(bounds.centerX() - width / 2, bounds.centerY() - height / 2,
                bounds.centerX() + width / 2, bounds.centerY() + height / 2)
            canvas.drawBitmap(image, sourceRect, target, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            require(workspace.get(WorkspaceObjectId(source.objectId))?.lifecycle ==
                com.halo.app.data.notebook.WorkspaceObjectLifecycle.ACTIVE &&
                files.getFile(WorkspaceObjectId(source.objectId))?.version == source.version) { "Image source changed during preview" }
            coroutineContext.ensureActive()
        } finally { image.recycle() }
    }
    private fun drawInk(canvas: Canvas, layer: StudioLayer, target: RectF, paint: Paint) {
        val document = requireNotNull(InkCodec.decode(layer.ink))
        val rect = inkFrame(layer, target)
        com.halo.app.data.workspace.WorkspaceInkBitmapRenderer.draw(canvas, document, rect, paint,
            layer.strokeWidth * maxOf(canvas.width, canvas.height) / 1000f)
    }

    private fun StudioRect.pixels(width: Int, height: Int) = RectF(x * width / 1000f, y * height / 1000f,
        (x + this.width) * width / 1000f, (y + this.height) * height / 1000f)
}
