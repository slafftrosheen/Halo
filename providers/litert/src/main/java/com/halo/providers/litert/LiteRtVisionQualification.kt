package com.halo.providers.litert

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.io.ByteArrayOutputStream

enum class VisionQualification { DECLARED, PROBE_REQUIRED, PROVEN, FAILED }
object VisionQualificationKey {
    const val INPUT_CONTRACT_VERSION = "halo-image-v1"
    fun of(fingerprint: String, runtimeVersion: String): String = "$fingerprint:litertlm-$runtimeVersion:$INPUT_CONTRACT_VERSION"
}
data class VisionProbeResult(
    val qualificationKey: String?, val passed: Boolean, val backend: String?,
    val durationMs: Long, val expectedTokensPresent: Boolean, val failureStage: String?,
)

/** Halo-owned content only. Never reads a camera, file picker or remote image. */
internal object SyntheticVisionImage {
    fun create(): ByteArray {
        val bitmap = Bitmap.createBitmap(256, 160, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 44f; typeface = android.graphics.Typeface.DEFAULT_BOLD }
            canvas.drawText("HALO", 20f, 65f, paint)
            canvas.drawText("42", 20f, 125f, paint)
            return ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                output.toByteArray()
            }
        } finally { bitmap.recycle() }
    }
}
