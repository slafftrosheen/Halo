package com.halo.app.ui.toolkit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.FaceDetectorYN
import org.opencv.objdetect.FaceRecognizerSF
import kotlin.math.sqrt

internal fun readAlbumExif(context: Context, uri: Uri): ExifInterface? =
    runCatching { context.contentResolver.openInputStream(uri)?.use(::ExifInterface) }.getOrNull()

/** Bounds memory and uses EXIF orientation for face landmarks and exported images. */
internal fun loadAlbumBitmap(context: Context, uri: Uri, maxEdge: Int): Bitmap? {
    val resolver = context.contentResolver
    if (Build.VERSION.SDK_INT >= 28) {
        return ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
            val ratio = (maxEdge.toFloat() / maxOf(info.size.width, info.size.height)).coerceAtMost(1f)
            if (ratio < 1f) decoder.setTargetSize((info.size.width * ratio).toInt().coerceAtLeast(1),
                (info.size.height * ratio).toInt().coerceAtLeast(1))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxEdge) sample *= 2
    val decoded = resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        })
    } ?: return null
    val orientation = readAlbumExif(context, uri)?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
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
    if (matrix.isIdentity) return decoded
    return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        .also { if (it !== decoded) decoded.recycle() }
}

internal data class AlbumPerson(val id: String, val name: String, val referenceId: String, val referenceVersion: String)

private class AlbumFaceEngine(context: Context) {
    private val detector: FaceDetectorYN
    private val recognizer: FaceRecognizerSF

    init {
        check(OpenCVLoader.initLocal()) { "On-device face matcher unavailable" }
        val detectorModel = installedAsset(context, "face_detection_yunet_2023mar.onnx",
            "8f2383e4dd3cfbb4553ea8718107fc0423210dc964f9f4280604804ed2552fa4")
        val recognizerModel = installedAsset(context, "face_recognition_sface_2021dec_int8.onnx",
            "2b0e941e6f16cc048c20aee0c8e31f569118f65d702914540f7bfdc14048d78a")
        detector = FaceDetectorYN.create(detectorModel.absolutePath, "", Size(320.0, 320.0), .89f, .3f, 128)
        recognizer = FaceRecognizerSF.create(recognizerModel.absolutePath, "")
    }

    /** Embeddings are short-lived local values. Neither enrolled nor unknown vectors are saved. */
    fun descriptors(bitmap: Bitmap): List<FloatArray> {
        val source = Mat()
        val bgr = Mat()
        val detections = Mat()
        try {
            Utils.bitmapToMat(bitmap, source)
            Imgproc.cvtColor(source, bgr, Imgproc.COLOR_RGBA2BGR)
            detector.setInputSize(bgr.size())
            detector.detect(bgr, detections)
            return (0 until detections.rows().coerceAtMost(12)).mapNotNull { index ->
                val face = detections.row(index)
                val cropped = Mat()
                val feature = Mat()
                try {
                    val bounds = FloatArray(15)
                    face.get(0, 0, bounds)
                    if (bounds[2] < 32 || bounds[3] < 32) return@mapNotNull null
                    recognizer.alignCrop(bgr, face, cropped)
                    recognizer.feature(cropped, feature)
                    val values = FloatArray(feature.total().toInt())
                    if (values.size != 128) return@mapNotNull null
                    feature.get(0, 0, values)
                    val norm = sqrt(values.sumOf { (it * it).toDouble() })
                    if (!norm.isFinite() || norm <= 0) null else FloatArray(values.size) { (values[it] / norm).toFloat() }
                } finally { feature.release(); cropped.release(); face.release() }
            }
        } finally { detections.release(); bgr.release(); source.release() }
    }
}

internal object HaloAlbumFaceMatcher {
    private val mutex = Mutex()
    private var engine: AlbumFaceEngine? = null

    suspend fun descriptors(context: Context, uri: Uri): List<FloatArray> = withContext(Dispatchers.Default) {
        mutex.withLock {
            val matcher = engine ?: AlbumFaceEngine(context.applicationContext).also { engine = it }
            val color = loadAlbumBitmap(context, uri, maxEdge = 720) ?: return@withLock emptyList()
            val bitmap = if (color.config == Bitmap.Config.ARGB_8888) color else color.copy(Bitmap.Config.ARGB_8888, false)
            try { if (bitmap == null) emptyList() else matcher.descriptors(bitmap) }
            finally { if (bitmap !== color) bitmap?.recycle(); color.recycle() }
        }
    }

    fun match(descriptors: List<FloatArray>, enrolled: Map<AlbumPerson, FloatArray>): Set<String> = buildSet {
        descriptors.forEach { candidate ->
            val ranked = enrolled.map { (person, reference) ->
                person.id to reference.indices.sumOf { (reference[it] * candidate[it]).toDouble() }
            }.sortedByDescending { it.second }
            val strongest = ranked.firstOrNull() ?: return@forEach
            val runnerUp = ranked.getOrNull(1)?.second
            // Use a conservative candidate threshold and reject near ties. These are search suggestions.
            if (strongest.second >= .60 && (runnerUp == null || strongest.second - runnerUp >= .10)) add(strongest.first)
        }
    }
}

private fun installedAsset(context: Context, name: String, hash: String): File {
    val directory = File(context.noBackupFilesDir, "album-models-v1")
    check(directory.isDirectory || directory.mkdirs())
    val model = File(directory, name)
    fun verified(file: File): Boolean {
        if (!file.isFile) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val data = ByteArray(8192)
            while (true) {
                val size = input.read(data)
                if (size < 0) break
                digest.update(data, 0, size)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) } == hash
    }
    if (!verified(model)) {
        val temporary = File.createTempFile("model-", ".part", directory)
        try {
            context.assets.open("album/models/$name").use { input -> temporary.outputStream().use { input.copyTo(it) } }
            check(verified(temporary))
            if (model.exists()) check(model.delete())
            check(temporary.renameTo(model))
        } finally { temporary.delete() }
    }
    return model
}
