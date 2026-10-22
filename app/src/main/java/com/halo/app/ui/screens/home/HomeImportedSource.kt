package com.halo.app.ui.screens.home

enum class HomeImportedSourceKind { TEXT, IMAGE, PDF, AUDIO, OTHER }

/** A transient receipt pointing to the existing durable Inbox object; never a second file store. */
data class HomeImportedSource(val objectId: String, val title: String, val kind: HomeImportedSourceKind,
    val attachmentFailed: Boolean = false) {
    val canAttach get() = kind in setOf(HomeImportedSourceKind.TEXT, HomeImportedSourceKind.IMAGE)
}

internal fun homeImportedSourceKind(mimeType: String): HomeImportedSourceKind = when {
    mimeType == "application/pdf" -> HomeImportedSourceKind.PDF
    mimeType in setOf("image/png", "image/jpeg", "image/webp") -> HomeImportedSourceKind.IMAGE
    mimeType.startsWith("text/") || mimeType in setOf("application/json", com.halo.app.study.StudyTextExtractor.DOCX) -> HomeImportedSourceKind.TEXT
    mimeType.startsWith("audio/") -> HomeImportedSourceKind.AUDIO
    else -> HomeImportedSourceKind.OTHER
}
