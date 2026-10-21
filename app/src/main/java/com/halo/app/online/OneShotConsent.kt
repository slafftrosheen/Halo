package com.halo.app.online

import java.security.MessageDigest
import java.util.UUID

/** Process-only approval challenge. It is never serialized, exported or restored. */
data class OnlineConsentChallenge internal constructor(val id: String)
class OneShotConsent(private val nowNanos: () -> Long = System::nanoTime) {
    private data class Entry(val fingerprint: String, val issued: Long)
    private val entries = LinkedHashMap<String, Entry>()
    @Synchronized fun prepare(fingerprint: String): OnlineConsentChallenge {
        val now = nowNanos()
        entries.entries.removeAll { now - it.value.issued !in 0..TTL_NANOS }
        while (entries.size >= 16) entries.remove(entries.keys.first())
        val id = UUID.randomUUID().toString()
        entries[id] = Entry(fingerprint, now)
        return OnlineConsentChallenge(id)
    }
    @Synchronized fun consume(id: String?, fingerprint: String) {
        val key = id ?: throw OnlineOperationRejected(OnlineProblem.CONSENT_REQUIRED)
        val entry = entries.remove(key) ?: throw OnlineOperationRejected(OnlineProblem.CONSENT_REQUIRED)
        if (nowNanos() - entry.issued !in 0..TTL_NANOS || entry.fingerprint != fingerprint)
            throw OnlineOperationRejected(OnlineProblem.CONSENT_EXPIRED)
    }
    @Synchronized fun discard(id: String?) { if (id != null) entries.remove(id) }
    companion object {
        const val TTL_NANOS = 120_000_000_000L
        fun fingerprint(vararg fields: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256")
            for (field in fields) {
                digest.update(java.nio.ByteBuffer.allocate(4).putInt(field.size).array())
                digest.update(field)
            }
            return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        }
    }
}
