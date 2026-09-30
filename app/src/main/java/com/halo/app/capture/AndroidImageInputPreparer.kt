package com.halo.app.capture

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import com.halo.sdk.runtime.multimodal.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.math.min
import kotlin.math.sqrt

class AndroidImageInputPreparer : ImageInputPreparer {
    @Volatile var lastSummary: ImagePreparationSummary? = null
        private set
    override suspend fun prepare(content: MediaAssetContent, sourceRef: MediaAssetRef?, limits: ImageInputLimits): PreparedImageInput = withContext(Dispatchers.Default) {
        if (content.mimeType !in setOf("image/jpeg", "image/png", "image/webp")) throw MediaAcquisitionException(MediaFailure.UNSUPPORTED_FORMAT)
        if (content.bytes.isEmpty() || content.bytes.size > limits.maxSourceBytes) throw MediaAcquisitionException(MediaFailure.TOO_LARGE)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(content.bytes, 0, content.bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outMimeType != content.mimeType) throw MediaAcquisitionException(MediaFailure.DECODE_FAILED)
        val orientation = run {
            try { ExifInterface(ByteArrayInputStream(content.bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
            catch (_: java.io.IOException) { throw MediaAcquisitionException(MediaFailure.DECODE_FAILED) }
        }
        val swap = orientation in setOf(ExifInterface.ORIENTATION_TRANSPOSE, ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSVERSE, ExifInterface.ORIENTATION_ROTATE_270)
        val width = if (swap) bounds.outHeight else bounds.outWidth
        val height = if (swap) bounds.outWidth else bounds.outHeight
        val ratio = min(1.0, min(min(limits.maxWidth.toDouble() / width, limits.maxHeight.toDouble() / height), sqrt(limits.maxPixels.toDouble() / (width.toLong() * height))))
        val targetWidth = (width * ratio).toInt().coerceAtLeast(1)
        val targetHeight = (height * ratio).toInt().coerceAtLeast(1)
        var sample = 1
        while (width / (sample * 2) >= targetWidth && height / (sample * 2) >= targetHeight) sample *= 2
        // Bound the decoded allocation even for malicious headers/extreme aspect ratios.
        while ((bounds.outWidth.toLong() / sample) * (bounds.outHeight / sample) > limits.maxPixels * 4) sample *= 2
        var bitmap = BitmapFactory.decodeByteArray(content.bytes, 0, content.bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 })
            ?: throw MediaAcquisitionException(MediaFailure.DECODE_FAILED)
        try {
            val matrix = Matrix().apply {
                when (orientation) {
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                    ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(270f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
                }
            }
            fun replace(next: Bitmap) { if (next !== bitmap) { bitmap.recycle(); bitmap = next } }
            if (!matrix.isIdentity) replace(Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true))
            replace(Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true))
            val mime = limits.preferredMimeTypes.firstOrNull { it in setOf("image/jpeg", "image/png") }
                ?: throw MediaAcquisitionException(MediaFailure.UNSUPPORTED_FORMAT)
            var encoded: ByteArray
            var quality = 90
            while (true) {
                val output = ByteArrayOutputStream()
                if (!bitmap.compress(if (mime == "image/png") Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, quality, output)) throw MediaAcquisitionException(MediaFailure.DECODE_FAILED)
                encoded = output.toByteArray()
                if (encoded.size <= limits.maxBytes) break
                if (mime == "image/jpeg" && quality > 50) quality -= 10
                else {
                    if (bitmap.width <= 1 || bitmap.height <= 1) throw MediaAcquisitionException(MediaFailure.TOO_LARGE)
                    replace(Bitmap.createScaledBitmap(bitmap, (bitmap.width * .75).toInt().coerceAtLeast(1), (bitmap.height * .75).toInt().coerceAtLeast(1), true))
                }
            }
            val summary = ImagePreparationSummary(sourceRef, bounds.outWidth, bounds.outHeight,
                bitmap.width, bitmap.height, content.bytes.size, encoded.size, mime, true, "orientation/resize/metadata stripping/re-encode")
            lastSummary = summary
            PreparedImageInput(MediaAssetContent(encoded, mime), summary)
        } finally { bitmap.recycle() }
    }
}
