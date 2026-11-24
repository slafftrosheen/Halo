package com.halo.app.ui.sources.v13

/**
 * halo 13.0 · adding a source in plain steps: browse · paste a link · follow a topic · add a city · add a calendar · add a book.
 * A link is checked on the phone first: https only, find the site's news feed (rel="alternate"), show 3 recent items,
 * how often it updates and what halo will use it for – THEN the person adds it.
 */
sealed interface LinkCheck {
    data class Found(val feedUrl: String, val title: String, val sample: List<String>) : LinkCheck
    data class NoFeed(val page: String) : LinkCheck          // offer "Save this page to read" instead
    data object NotHttps : LinkCheck
    data object TooBig : LinkCheck                            // > 2 MB feed
}

object AddSource {
    private val ALT = Regex("<link[^>]+rel=[\"']alternate[\"'][^>]+type=[\"']application/(rss|atom)\\+xml[\"'][^>]*>", RegexOption.IGNORE_CASE)
    private val HREF = Regex("href=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
    const val MAX_FEED_BYTES = 2 * 1024 * 1024

    fun looksLikeFeed(contentType: String?, head: String) =
        contentType?.contains("xml") == true || head.trimStart().startsWith("<?xml") || head.contains("<rss") || head.contains("<feed")

    /** Returns feed links declared by an HTML page, absolute. */
    fun discover(pageUrl: String, html: String): List<String> = ALT.findAll(html)
        .mapNotNull { HREF.find(it.value)?.groupValues?.get(1) }
        .map { if (it.startsWith("http")) it else java.net.URI(pageUrl).resolve(it).toString() }
        .filter { it.startsWith("https://") }.distinct().toList()

    /** A source added by a grown-up is NOT kids-safe until they say so with the grown-up PIN. */
    fun kidsSafeAllowed(addedByGrownUp: Boolean, pinConfirmed: Boolean) = addedByGrownUp && pinConfirmed
}
