package com.halo.app.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

private val photoIdentity = Regex("[0-9a-f]{32}")
internal fun validWallpaperPhotoId(value: String) = photoIdentity.matches(value)

internal class WallpaperPhotoStore(context: Context) {
    private val app = context.applicationContext
    private val directory = File(app.noBackupFilesDir, "wallpaper_photos")

    suspend fun importPhoto(uri: Uri): WallpaperPhoto = withContext(Dispatchers.IO) {
        require(uri.scheme == "content")
        check(directory.isDirectory || directory.mkdirs())
        val bitmap = if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(app.contentResolver, uri)) { decoder, info, _ ->
                val edge = maxOf(info.size.width, info.size.height)
                require(edge in 1..48_000)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetSampleSize((edge + 1599) / 1600)
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            app.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val edge = maxOf(bounds.outWidth, bounds.outHeight)
            require(edge in 1..48_000)
            var sample = 1
            while (edge / sample > 1600) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            requireNotNull(app.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) })
        }
        val photo = WallpaperPhoto(UUID.randomUUID().toString().replace("-", ""))
        val temporary = File(directory, photo.id + ".tmp")
        try {
            ensureActive()
            temporary.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it)) }
            ensureActive()
            check(temporary.renameTo(file(photo.id)))
            photo
        } finally {
            bitmap.recycle()
            temporary.delete()
        }
    }

    suspend fun render(photo: WallpaperPhoto?): Bitmap? = withContext(Dispatchers.IO) {
        photo ?: return@withContext null
        val source = BitmapFactory.decodeFile(file(photo.id).path) ?: return@withContext null
        val edge = when (photo.blur) {
            WallpaperBlur.LIGHT -> 300
            WallpaperBlur.SOFT -> 120
            WallpaperBlur.STRONG -> 56
        }
        try {
            val ratio = minOf(1f, edge.toFloat() / maxOf(source.width, source.height))
            val sized = Bitmap.createScaledBitmap(source, maxOf(1, (source.width * ratio).toInt()),
                maxOf(1, (source.height * ratio).toInt()), true)
            try {
                boxBlur(sized, if (photo.blur == WallpaperBlur.LIGHT) 3 else 5)
            } finally {
                if (sized !== source) sized.recycle()
            }
        } finally {
            source.recycle()
        }
    }

    fun delete(id: String) { file(id).delete() }

    /** Retain the active and Undo photos. Leave recent files for an in-progress picker draft. */
    fun trim(keep: Set<String>) {
        val cutoff = System.currentTimeMillis() - 3_600_000
        directory.listFiles()?.forEach { candidate ->
            if (candidate.lastModified() < cutoff && candidate.nameWithoutExtension !in keep &&
                validWallpaperPhotoId(candidate.nameWithoutExtension) && candidate.extension in setOf("jpg", "tmp")) {
                candidate.delete()
            }
        }
    }

    private fun file(id: String): File {
        require(validWallpaperPhotoId(id))
        return File(directory, "$id.jpg")
    }

    private fun boxBlur(source: Bitmap, radius: Int): Bitmap {
        val width = source.width
        val height = source.height
        val input = IntArray(width * height)
        val output = IntArray(width * height)
        source.getPixels(input, 0, width, 0, 0, width, height)
        fun average(x: Int, y: Int, horizontal: Boolean, pixels: IntArray): Int {
            var red = 0
            var green = 0
            var blue = 0
            for (delta in -radius..radius) {
                val sx = if (horizontal) (x + delta).coerceIn(0, width - 1) else x
                val sy = if (horizontal) y else (y + delta).coerceIn(0, height - 1)
                val value = pixels[sy * width + sx]
                red += (value shr 16) and 255
                green += (value shr 8) and 255
                blue += value and 255
            }
            val count = radius * 2 + 1
            return android.graphics.Color.rgb(red / count, green / count, blue / count)
        }
        for (y in 0 until height) for (x in 0 until width) output[y * width + x] = average(x, y, true, input)
        for (y in 0 until height) for (x in 0 until width) input[y * width + x] = average(x, y, false, output)
        return Bitmap.createBitmap(input, width, height, Bitmap.Config.ARGB_8888)
    }
}
