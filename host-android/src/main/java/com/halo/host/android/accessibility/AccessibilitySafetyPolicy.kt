package com.halo.host.android.accessibility

/** Why Halo refuses to read or act on a package. */
enum class PackageBlockReason {
    /** Named outright as a package whose screen must never enter Halo's context. */
    BLOCKED_PACKAGE,

    /** Matched a sensitive-app marker such as `bank`, `wallet` or `auth`. */
    SENSITIVE_APP_MARKER,
}

object AccessibilitySafetyPolicy {
    private val BLOCKED_PACKAGES = setOf(
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.settings",
        "com.google.android.keepass",
        "com.lastpass.lpandroid",
        "com.1password.android",
        "com.bitwarden.authenticator",
        "com.authy.authy",
        "com.google.android.apps.authenticator2",
        "com.android.vending", // Play Store package installer operations
        "com.google.android.packageinstaller",
        "com.android.packageinstaller"
    )

    private val SENSITIVE_MARKERS = listOf("bank", "wallet", "pass", "auth")

    /** The reason a package is off limits, or null when it is readable. */
    fun blockReason(packageName: String?): PackageBlockReason? {
        if (packageName == null) return null
        val pkg = packageName.lowercase()
        if (pkg in BLOCKED_PACKAGES) return PackageBlockReason.BLOCKED_PACKAGE
        if (SENSITIVE_MARKERS.any { pkg.contains(it) }) return PackageBlockReason.SENSITIVE_APP_MARKER
        return null
    }

    fun isPackageBlocked(packageName: String?): Boolean = blockReason(packageName) != null
}
