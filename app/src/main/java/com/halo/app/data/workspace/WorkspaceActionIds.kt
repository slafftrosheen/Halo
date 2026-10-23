package com.halo.app.data.workspace

/** Canonical legacy/derived IDs can contain a colon (for example Canvas review notes). Never paths or URLs. */
object WorkspaceActionIds {
    const val PATTERN = "^[A-Za-z0-9_:-]{1,100}$"
    private val pattern = Regex(PATTERN)
    fun isValid(value: String) = pattern.matches(value)
}
