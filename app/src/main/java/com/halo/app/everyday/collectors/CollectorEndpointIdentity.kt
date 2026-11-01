package com.halo.app.everyday.collectors

/** Same normalized URL shares preview/polling budget even after remove/re-add or a duplicate source. */
object CollectorEndpointIdentity {
    fun fingerprint(url: String): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(CollectorUrlPolicy.validate(url).newBuilder().fragment(null).build().toString().toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    fun budgetKey(url: String) = "endpoint:${fingerprint(url)}"
}
