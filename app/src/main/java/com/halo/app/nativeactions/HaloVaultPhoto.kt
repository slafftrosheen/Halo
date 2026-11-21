package com.halo.app.nativeactions

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import com.halo.app.ui.toolkit.scanFrameText
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.Locale

internal data class VaultPhotoDraft(val paper: VaultPaper, val bytes: ByteArray, val evidence: Map<String, String>)

internal suspend fun readVaultPhoto(context: Context, uri: Uri): VaultPhotoDraft {
    val resolver = context.contentResolver
    val bitmap = if (Build.VERSION.SDK_INT >= 28) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = maxOf(info.size.width, info.size.height).coerceAtLeast(1)
            if (longest > 1800) {
                val ratio = 1800.0 / longest
                decoder.setTargetSize((info.size.width * ratio).toInt().coerceAtLeast(1),
                    (info.size.height * ratio).toInt().coerceAtLeast(1))
            }
        }
    } else {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        require(options.outWidth > 0 && options.outHeight > 0)
        options.inSampleSize = 1
        while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 1800) options.inSampleSize *= 2
        options.inJustDecodeBounds = false
        requireNotNull(resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) })
    }
    try {
        val lines = scanFrameText(bitmap).map { it.text.trim().take(180) }.filter(String::isNotBlank).take(120)
        val name = lines.firstOrNull { line -> line.any(Char::isLetter) }.orEmpty()
        val kind = when {
            lines.any { it.contains("warranty", true) || it.contains("garantija", true) } -> VaultPaperKind.WARRANTY
            lines.any { it.contains("receipt", true) || it.contains("čeks", true) } -> VaultPaperKind.RECEIPT
            lines.any { it.contains("policy", true) || it.contains("polise", true) } -> VaultPaperKind.POLICY
            else -> VaultPaperKind.DOCUMENT
        }
        val dateFormat = Regex("""\b(20\d{2}|19\d{2})[-/.](\d{1,2})[-/.](\d{1,2})\b|\b(\d{1,2})[.](\d{1,2})[.](20\d{2}|19\d{2})\b""")
        fun findDate(labels: List<String>): Pair<LocalDate, String>? {
            return lines.asSequence().filter { raw -> labels.any { raw.lowercase(Locale.ROOT).contains(it) } }
                .mapNotNull { line ->
                    val parts = dateFormat.find(line)?.groupValues ?: return@mapNotNull null
                    val date = runCatching {
                        if (parts[1].isNotEmpty()) LocalDate.of(parts[1].toInt(), parts[2].toInt(), parts[3].toInt())
                        else LocalDate.of(parts[6].toInt(), parts[5].toInt(), parts[4].toInt())
                    }.getOrNull() ?: return@mapNotNull null
                    date to line
                }.firstOrNull()
        }
        val bought = findDate(listOf("purchase", "bought", "date of sale", "pirkuma", "pirkts"))
        val ends = findDate(listOf("expiry", "expires", "ends", "valid until", "until", "derīgs līdz", "beidzas"))
            ?.takeIf { bought == null || it.first >= bought.first }
        val remind = ends?.first?.minusMonths(1)?.takeIf { it > LocalDate.now() }
        val evidence = buildMap {
            if (name.isNotBlank()) put("what", name)
            bought?.let { put("bought", it.second) }
            ends?.let { put("ends", it.second) }
        }
        val bytes = ByteArrayOutputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 86, it))
            it.toByteArray()
        }
        return VaultPhotoDraft(VaultPaper(name, kind, bought?.first, ends?.first, remind), bytes, evidence)
    } finally { bitmap.recycle() }
}
