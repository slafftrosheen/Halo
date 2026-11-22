package com.halo.app.everyday.collectors

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class OfflineKnowledgeTest {
    @get:Rule val folder = TemporaryFolder()
    private val clock = Clock.fixed(Instant.parse("2026-11-23T12:00:00Z"), ZoneOffset.UTC)

    @Test fun downloadedWikiSurvivesRestartWithAnExactCitationAndSearchNeedsNoNetwork() = runTest {
        val files = folder.newFolder()
        val wiki = """{"query":{"pages":[{"pageid":10,"lastrevid":73,"title":"Rīga","extract":"Capital of Latvia."}]}}"""
        val owner = OfflineKnowledge(files, { url, accept ->
            assertTrue(url.startsWith("https://en.wikipedia.org/w/api.php?"))
            assertEquals("application/json", accept)
            ArticleDownload(wiki.toByteArray(), "application/json", url)
        }, { {} }, clock)
        val installed = owner.download("wikibaltics")
        val reopened = OfflineKnowledge(files, { _, _ -> error("Search attempted a download") }, { {} }, clock)
        val hit = reopened.search("Riga", "wikibaltics", installed.revision).single()
        assertEquals("Rīga", hit.entry.title)
        assertEquals("https://en.wikipedia.org/w/index.php?oldid=73", hit.entry.url)
        assertEquals("2026-11-23T12:00:00Z", hit.retrievedAt)
        assertFalse(reopened.catalogue().first { it.pack.id == "kiwix-wiki-en" }.pack.searchable)
    }

    @Test fun interruptedOrRevokedUpdateKeepsThePreviouslyInstalledRevision() = runTest {
        val files = folder.newFolder()
        var permitted = true
        var response = "LV\t+5657+02406\tEurope/Riga\n"
        val owner = OfflineKnowledge(files, { url, _ ->
            if (response.contains("Tallinn")) permitted = false
            ArticleDownload(response.toByteArray(), "text/plain", url)
        }, { { check(permitted) } }, clock)
        val initial = owner.download("iana-zones")
        response = "EE\t+5925+02445\tEurope/Tallinn\n"
        try {
            owner.download("iana-zones")
            fail("Revoked acquisition must fail")
        } catch (_: IllegalStateException) { }
        val reopened = OfflineKnowledge(files, { _, _ -> error("Search attempted a download") }, { {} }, clock)
        assertEquals(initial.revision, reopened.search("Riga", "iana-zones", initial.revision).single().revision)
        assertTrue(reopened.search("Tallinn", "iana-zones").isEmpty())
    }
}
