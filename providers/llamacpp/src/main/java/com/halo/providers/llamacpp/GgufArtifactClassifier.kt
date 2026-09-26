package com.halo.providers.llamacpp

import java.io.File

object GgufArtifactClassifier {

    fun classifyFilename(filename: String): GgufArtifactRole {
        val name = filename.substringAfterLast('/').substringAfterLast('\\').lowercase()
        if (!name.endsWith(".gguf")) return GgufArtifactRole.UNKNOWN_AUXILIARY

        if (name.contains("mmproj") || name.contains("projector")) {
            return GgufArtifactRole.MULTIMODAL_PROJECTOR
        }

        if (name.contains("imatrix")) {
            return GgufArtifactRole.IMATRIX
        }

        if (Regex("-\\d{5}-of-\\d{5}\\.gguf$", RegexOption.IGNORE_CASE).containsMatchIn(filename) ||
            Regex("\\.part\\d+\\.gguf$", RegexOption.IGNORE_CASE).containsMatchIn(filename)) {
            return GgufArtifactRole.SPLIT_MODEL_SHARD
        }

        if (name.startsWith("mtp_") || name.contains("-mtp-") || name.contains("_mtp_")) {
            return GgufArtifactRole.MTP_AUXILIARY
        }

        if (name.contains("draft") || name.contains("speculative")) {
            return GgufArtifactRole.DRAFT_MODEL
        }

        return GgufArtifactRole.BASE_MODEL
    }

    fun classifyRemote(filename: String): GgufArtifactRole = classifyFilename(filename)

    fun classifyLocalFile(file: File): GgufArtifactRole = classifyFilename(file.name)

    fun isKnownAuxiliaryLocalFile(file: File): Boolean {
        val role = classifyLocalFile(file)
        return isDeterministicallyInvalidRole(role)
    }

    fun isRunnableBaseModel(filename: String): Boolean {
        return classifyFilename(filename) == GgufArtifactRole.BASE_MODEL
    }

    fun isDeterministicallyInvalidRole(role: GgufArtifactRole): Boolean {
        return role in setOf(
            GgufArtifactRole.IMATRIX,
            GgufArtifactRole.MTP_AUXILIARY,
            GgufArtifactRole.MULTIMODAL_PROJECTOR,
            GgufArtifactRole.DRAFT_MODEL,
            GgufArtifactRole.SPLIT_MODEL_SHARD,
            GgufArtifactRole.UNKNOWN_AUXILIARY
        )
    }

    object GgufArtifactClassificationInternal {
        fun isBaseModelMetadataPlausible(metadata: GgufModelMetadata?): Boolean {
            if (metadata == null) return false
            return metadata.architecture != null || (metadata.contextLength != null && metadata.contextLength > 0)
        }
    }
}
