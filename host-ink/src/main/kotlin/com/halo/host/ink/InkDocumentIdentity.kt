package com.halo.host.ink

/** Content-free receipt comparison; never use a previous saved document to acknowledge a new edit. */
object InkDocumentIdentity {
    fun encodedSha256(encoded: String): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(encoded.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
