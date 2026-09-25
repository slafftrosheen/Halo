package com.halo.host.android.accessibility

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

    fun isPackageBlocked(packageName: String?): Boolean {
        if (packageName == null) return false
        val pkg = packageName.lowercase()
        if (pkg in BLOCKED_PACKAGES) return true
        if (pkg.contains("bank") || pkg.contains("wallet") || pkg.contains("pass") || pkg.contains("auth")) return true
        return false
    }
}
