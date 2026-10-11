package dev.starve.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FeedDiscoveryTest {
    private val page = "http://a.test/blog/index.html"
    private val hosts = setOf("soundcloud.com", "m.soundcloud.com")

    private fun alt(html: String) = FeedDiscovery.alternateFeed(html, page)

    @Test
    fun `an absolute alternate href is returned as is`() {
        val html = """<link rel="alternate" type="application/rss+xml" href="http://b.test/f.xml">"""
        assertEquals("http://b.test/f.xml", alt(html))
    }

    @Test
    fun `a relative href is resolved against the page url`() {
        assertEquals(
            "http://a.test/blog/feed.xml",
            alt("""<link rel="alternate" type="application/rss+xml" href="feed.xml">"""),
        )
        assertEquals(
            "http://a.test/feed.xml",
            alt("""<link rel="alternate" type="application/rss+xml" href="/feed.xml">"""),
        )
    }

    @Test
    fun `attribute order case quotes and entities are tolerated`() {
        val html = "<LINK HREF='http://b.test/f?a=1&amp;b=2' TYPE=application/atom+xml REL=\"Alternate\" />"
        assertEquals("http://b.test/f?a=1&b=2", alt(html))
    }

    @Test
    fun `the first matching link wins and non feed links are skipped`() {
        val html =
            """
            <link rel="stylesheet" href="/s.css">
            <link rel="alternate" type="text/html" href="/other">
            <link rel="alternate" type="application/rss+xml" href="/one.xml">
            <link rel="alternate" type="application/rss+xml" href="/two.xml">
            """.trimIndent()
        assertEquals("http://a.test/one.xml", alt(html))
    }

    @Test
    fun `links without a usable href are skipped`() {
        val html =
            """
            <link rel="alternate" type="application/rss+xml">
            <link rel="alternate" type="application/rss+xml" href="">
            <link rel="alternate" type="application/rss+xml" href="ftp://b.test/f">
            <link rel="alternate" type="application/rss+xml" href="http://b.test/ bad">
            <link rel="alternate" type="application/rss+xml" href="/ok.xml">
            """.trimIndent()
        assertEquals("http://a.test/ok.xml", alt(html))
    }

    @Test
    fun `a page without a feed link has none`() {
        assertNull(alt("<html><head><title>x</title></head></html>"))
        val link = """<link rel="alternate" type="application/rss+xml" href="/f">"""
        assertNull(FeedDiscovery.alternateFeed(link, "bad url"))
    }

    @Test
    fun `a soundcloud profile page yields its numeric user id`() {
        val html = """<meta property="al:ios:url" content="soundcloud://users:78443">"""
        assertEquals("78443", FeedDiscovery.soundCloudUserId(html, "https://soundcloud.com/artist/", hosts))
        assertEquals("78443", FeedDiscovery.soundCloudUserId(html, "https://M.SoundCloud.com/artist", hosts))
    }

    @Test
    fun `only configured hosts and single segment paths are profiles`() {
        val html = "soundcloud://users:78443"
        assertNull(FeedDiscovery.soundCloudUserId(html, "https://other.com/artist", hosts))
        assertNull(FeedDiscovery.soundCloudUserId(html, "https://soundcloud.com/artist/tracks", hosts))
        assertNull(FeedDiscovery.soundCloudUserId(html, "https://soundcloud.com/", hosts))
        assertNull(FeedDiscovery.soundCloudUserId(html, "not a url", hosts))
    }

    @Test
    fun `a non numeric or missing marker yields no id`() {
        assertNull(FeedDiscovery.soundCloudUserId("soundcloud://users:abc", "https://soundcloud.com/a", hosts))
        assertNull(FeedDiscovery.soundCloudUserId("nothing", "https://soundcloud.com/a", hosts))
    }

    @Test
    fun `html is recognised by content type or by an html element in non xml content`() {
        assertTrue(FeedDiscovery.isHtml("text/html; charset=utf-8", "anything"))
        assertTrue(FeedDiscovery.isHtml("application/xhtml+xml", "<x/>"))
        assertTrue(FeedDiscovery.isHtml(null, "<!DOCTYPE html><HTML lang=en>"))
        assertTrue(FeedDiscovery.isHtml("text/plain", "<html>"))
        assertFalse(FeedDiscovery.isHtml("application/xml", "<html><body/></html>"))
        assertFalse(FeedDiscovery.isHtml("text/plain", "just text"))
    }
}
