package com.halo.providers.llamacpp

import java.io.File

/**
 * GGUF artifact validation concepts.
 *
 * A complete GGUF container is not the same thing as a runnable model artifact.
 * Classification and metadata plausibility sit between container integrity and
 * native load compatibility.
 */
object GgufArtifactValidation {

    /**
     * Container integrity only.
     *
     * True when the file exists, matches the expected byte count, and is a
     * readable GGUF header/metadata container. This does not imply the file
     * is a base inference model, or that native llama.cpp can load it.
     */
    fun isCompleteArtifactDownload(file: File, expectedBytes: Long): Boolean {
        if (expectedBytes <= 0L || !file.isFile || file.length() != expectedBytes) return false
        return GgufMetadataReader.isCompleteDownload(file, expectedBytes)
    }

    /**
     * Conservative runnable-base-artifact gate.
     *
     * True only when the artifact role is BASE_MODEL and the metadata is
     * structurally plausible enough to be treated as a model selection candidate.
     * Native compatibility remains separate and is decided by llama.cpp at load
     * time.
     */
    fun isRunnableBaseArtifact(
        role: GgufArtifactRole,
        metadata: GgufModelMetadata?,
        sourceFilename: String,
    ): Boolean {
        // Only base-model candidates can be runnable.
        if (role != GgufArtifactRole.BASE_MODEL) return false
        if (sourceFilename.isBlank() || !sourceFilename.endsWith(".gguf", ignoreCase = true)) return false

        // Metadata plausibility is advisory. Native compatibility is the real gate.
        return true
    }

    internal fun isBaseModelMetadataPlausible(metadata: GgufModelMetadata?): Boolean {
        if (metadata == null) return false
        return metadata.contextLength != null ||
            metadata.blockCount != null ||
            metadata.embeddingLength != null ||
            metadata.architecture?.isNotBlank() == true
    }

    internal fun isArchiveFileName(filename: String): Boolean {
        return filename.endsWith(".tar", ignoreCase = true) ||
            filename.endsWith(".tar.gz", ignoreCase = true) ||
            filename.endsWith(".tar.zst", ignoreCase = true) ||
            filename.endsWith(".zip", ignoreCase = true)
    }
}
