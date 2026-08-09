package com.halo.drivers.heycyan.codec

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Persists packet annotations independently from raw capture JSON documents.
 * Analysis files are safe to revise as interpretations improve; captures remain
 * immutable evidence.
 */
class JsonCaptureAnalysisStore(
    private val directory: File,
    private val captureStore: JsonCaptureSessionStore,
    moshi: Moshi = defaultMoshi()
) {
    private val adapter = moshi.adapter(CaptureAnalysisJson::class.java)

    fun save(analysis: CaptureAnalysis): File {
        val captures = analysis.captureIds.map { captureStore.loadCapture(it) }
        CaptureAnalysisValidator.validate(analysis, captures)
        check(directory.exists() || directory.mkdirs()) {
            "Unable to create analysis directory: ${directory.absolutePath}"
        }

        val target = File(directory, "analysis-${safeFileName(analysis.analysisId)}.json")
        val temporary = File(directory, "${target.name}.tmp")
        temporary.writeText(adapter.toJson(analysis.toJsonModel()))
        moveAtomically(temporary, target)
        return target
    }

    fun load(file: File): CaptureAnalysis {
        require(file.isFile) { "Analysis file does not exist: ${file.absolutePath}" }
        val analysis = adapter.fromJson(file.readText())?.toDomain()
            ?: throw IllegalArgumentException("Analysis file is empty: ${file.absolutePath}")
        validate(analysis)
        return analysis
    }

    fun listAnalyses(): List<File> = directory.listFiles { file ->
        file.isFile && file.extension == "json" && file.name.startsWith("analysis-")
    }?.sortedBy { it.name } ?: emptyList()

    fun validate(analysis: CaptureAnalysis) {
        val captures = analysis.captureIds.map { captureStore.loadCapture(it) }
        CaptureAnalysisValidator.validate(analysis, captures)
    }

    private fun CaptureAnalysis.toJsonModel() = CaptureAnalysisJson(
        schemaVersion = schemaVersion,
        analysisId = analysisId,
        captureIds = captureIds,
        annotations = annotations
    )

    private fun CaptureAnalysisJson.toDomain() = CaptureAnalysis(
        schemaVersion = schemaVersion,
        analysisId = analysisId,
        captureIds = captureIds,
        annotations = annotations
    )

    private fun safeFileName(value: String): String = value.replace(
        Regex("[^A-Za-z0-9._-]"),
        "_"
    )

    private fun moveAtomically(temporary: File, target: File) {
        try {
            Files.move(
                temporary.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: Exception) {
            check(temporary.renameTo(target)) {
                "Unable to finalize analysis: ${target.absolutePath}"
            }
        }
    }

    private companion object {
        fun defaultMoshi(): Moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }
}

@JsonClass(generateAdapter = false)
private data class CaptureAnalysisJson(
    val schemaVersion: Int,
    val analysisId: String,
    val captureIds: List<String>,
    val annotations: List<PacketAnnotation>
)