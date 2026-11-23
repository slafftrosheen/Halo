package com.halo.app.data.workspace

/** Internal presentation routes contain only a versioned reference and adult authority. */
internal enum class AdaptivePageDestination(val path: String) { OPEN("open"), WIDGET("widget"), ROUTINE("routine") }

internal data class AdaptivePageEntry(val destination: AdaptivePageDestination, val page: AdaptivePageRef, val familyRevision: Long) {
    init { require(familyRevision >= 0) }

    fun route() = "$ROOT/${destination.path}/${page.id}/${page.revision}/$familyRevision"

    companion object {
        const val ROOT = "home/pages"
        const val PATTERN = "$ROOT/{destination}/{pageId}/{pageRevision}/{familyRevision}"
        val arguments = listOf("destination", "pageId", "pageRevision", "familyRevision")

        fun parse(route: String): AdaptivePageEntry? {
            if (!route.startsWith("$ROOT/")) return null
            val parts = route.removePrefix("$ROOT/").split('/')
            if (parts.size != arguments.size) return null
            val destination = AdaptivePageDestination.entries.find { it.path == parts[0] } ?: return null
            val version = parts[2].toLongOrNull() ?: return null
            val familyRevision = parts[3].toLongOrNull() ?: return null
            return runCatching { AdaptivePageEntry(destination, AdaptivePageRef(parts[1], version), familyRevision) }
                .getOrNull()?.takeIf { it.route() == route }
        }
    }
}
