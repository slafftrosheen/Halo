package com.halo.drivers.heycyan.media.repository

import com.halo.drivers.heycyan.media.HeyCyanMedia
import java.security.MessageDigest

/**
 * Priority-based media identity resolver.
 *
 * Priority:
 * 1. Authoritative remote media ID (if not blank and not default filename)
 * 2. Deterministic hash of filename | sizeBytes | createdAt
 */
interface HeyCyanMediaIdentityResolver {
    fun identity(media: HeyCyanMedia): String
}

/**
 * Default implementation of [HeyCyanMediaIdentityResolver].
 */
class DefaultHeyCyanMediaIdentityResolver : HeyCyanMediaIdentityResolver {

    override fun identity(media: HeyCyanMedia): String {
        if (media.id.isNotBlank()) {
            return media.id
        }

        // Fallback deterministic identity string
        val key = buildString {
            append(media.filename)
            append('|')
            append(media.sizeBytes ?: -1)
            append('|')
            append(media.createdAt ?: -1)
        }
        return sha256(key)
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
