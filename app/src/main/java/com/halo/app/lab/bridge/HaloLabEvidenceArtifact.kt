package com.halo.app.lab.bridge

import java.util.UUID

/** Allows only a complete fixed-smoke text output; model artifact bytes are never addressable. */
internal object HaloLabEvidenceArtifact {
    const val MAX_BYTES = 2048 // At most 512 Unicode code points encoded as UTF-8.

    fun resolve(
        attemptId: String,
        requestedArtifactId: String,
        terminal: Boolean,
        fixtureId: String?,
        response: String?,
        outputTruncated: Boolean,
    ): ByteArray? {
        if (!terminal || !isUuid(attemptId) || requestedArtifactId != "output-$attemptId") return null
        if (fixtureId != HaloLabProtocol.FAST_SMOKE_FIXTURE_ID || response == null || outputTruncated) return null
        if (response.codePointCount(0, response.length) > 512) return null
        val bytes = response.toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_BYTES || bytes.toString(Charsets.UTF_8) != response) return null
        return bytes
    }

    private fun isUuid(value: String): Boolean = runCatching { UUID.fromString(value).toString() == value.lowercase() }.getOrDefault(false)
}
