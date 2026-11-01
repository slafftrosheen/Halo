package com.halo.app.everyday.collectors

import java.net.InetAddress
import java.net.UnknownHostException
import java.time.Instant
import okhttp3.Dns
import org.junit.Assert.*
import org.junit.Test

class CollectorParsingTest {
    private val source = CollectorSource("news", "News", "Publisher", CollectorKind.FEED, SourceCategory.TECHNOLOGY, "en", "https://example.org/feed")
    @Test fun rssAndAtomDecodeEntitiesSanitizeHtmlAndKeepPublisherDatesAndStableIdentity() {
        val rss = """<rss version="2.0"><channel><title>Feed</title><item><guid>one</guid><title>Rain &amp; sun</title><link>/one</link><pubDate>Sun, 01 Nov 2026 10:00:00 GMT</pubDate><description><![CDATA[<p>Read <b>this</b>.</p><script>alert(1)</script><img src="https://tracker.test/a">]]></description></item><item><guid>one</guid><title>Duplicate</title></item></channel></rss>"""
        val result = FeedParser.parse(rss.toByteArray(), source)
        assertEquals(1, result.articles.size); assertEquals(2, result.itemCount)
        val fact = result.articles.single()
        assertEquals("Rain & sun", fact.title); assertEquals("Read this.", fact.text)
        assertEquals("https://example.org/one", fact.url); assertEquals("Publisher", fact.publisher)
        assertEquals(Instant.parse("2026-11-01T10:00:00Z"), fact.publishedAt)
        val atom = """<feed xmlns="http://www.w3.org/2005/Atom"><entry xml:lang="lv"><id>one</id><title>Ziņas</title><link rel="self" href="/ignored"/><link rel="alternate" href="/one"/><published>2026-11-01T12:00:00+02:00</published><content type="xhtml"><div xmlns="http://www.w3.org/1999/xhtml"><p>Viena</p><p>ziņa</p></div></content></entry></feed>"""
        val alternate = FeedParser.parse(atom.toByteArray(), source).articles.single()
        assertEquals(fact.id, alternate.id); assertEquals("lv", alternate.language); assertEquals(fact.publishedAt, alternate.publishedAt)
        assertEquals("https://example.org/one", alternate.url); assertTrue(alternate.text.contains("Viena ziņa"))
    }
    @Test fun parserRejectsEntityDeclarationsOversizeAndExcessiveNesting() {
        val entity = """<!DOCTYPE rss [<!ENTITY payload SYSTEM "file:///secret">]><rss><channel><item><title>&payload;</title></item></channel></rss>"""
        assertThrows(IllegalArgumentException::class.java) { FeedParser.parse(entity.toByteArray(), source) }
        assertThrows(IllegalArgumentException::class.java) { FeedParser.parse(ByteArray(CollectorRefreshPolicy.MAX_BODY_BYTES + 1), source) }
        val deep = "<rss><channel><item><description>" + "<x>".repeat(70) + "body" + "</x>".repeat(70) + "</description></item></channel></rss>"
        assertThrows(org.xml.sax.SAXException::class.java) { FeedParser.parse(deep.toByteArray(), source) }
    }
    @Test fun linksCannotCarryJavascriptCredentialsOrLocalServiceSchemes() {
        listOf("javascript:alert(1)", "file:///secret", "https://user:password@example.org/private", "http://example.org/").forEach { url ->
            val body = "<rss><channel><item><guid>one</guid><title>News</title><link>$url</link></item></channel></rss>"
            assertNull(FeedParser.parse(body.toByteArray(), source).articles.single().url)
        }
    }
    @Test fun dnsRejectsPrivateMixedAndSpecialAddressesWithoutSecondResolution() {
        listOf("127.0.0.1", "10.0.0.2", "172.16.0.1", "192.168.1.1", "169.254.169.254", "100.64.0.1", "198.18.0.1", "192.0.2.1", "224.0.0.1", "::1", "fe80::1", "fd00::1", "2001:db8::1", "2002:7f00:1::1").forEach { address ->
            assertFalse(address, CollectorUrlPolicy.publicAddress(InetAddress.getByName(address)))
        }
        var lookups = 0
        val delegate = object : Dns { override fun lookup(hostname: String): List<InetAddress> {
            lookups++; return listOf(InetAddress.getByName("8.8.8.8"), InetAddress.getByName("127.0.0.1"))
        } }
        assertThrows(UnknownHostException::class.java) { CollectorUrlPolicy.dns(delegate).lookup("example.org") }
        assertEquals(1, lookups)
        assertTrue(CollectorUrlPolicy.publicAddress(InetAddress.getByName("2606:4700:4700::1111")))
    }
    @Test fun localCachedSnapshotIsStaleAndFailureBackoffNeverChangesSuccessfulRetrievalTime() {
        val now = Instant.parse("2026-11-01T10:00:00Z")
        val snapshot = CollectorSnapshot(source.id, source.version, CollectorPayload.Articles(emptyList()), "revision", now, now, now.plusSeconds(3600))
        assertFalse(snapshot.stale(now, external = true, power = true))
        assertTrue(snapshot.stale(now, external = true, power = false))
        val first = CollectorRefreshPolicy.nextAttempt(source, now, 1)
        val later = CollectorRefreshPolicy.nextAttempt(source, now, 5)
        assertTrue(later > first); assertTrue(later <= now.plusSeconds(86400)); assertEquals(now, snapshot.retrievedAt)
    }
}
