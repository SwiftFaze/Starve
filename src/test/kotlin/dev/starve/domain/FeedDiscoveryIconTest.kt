package dev.starve.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FeedDiscoveryIconTest {
    private val page = "http://a.test/blog/index.html"

    private fun icon(html: String) = FeedDiscovery.iconLink(html, page)

    @Test
    fun `an icon link is returned as its absolute href`() {
        assertEquals("https://cdn.test/icon.png", icon("""<link rel="icon" href="https://cdn.test/icon.png">"""))
    }

    @Test
    fun `a shortcut icon link counts too`() {
        assertEquals("http://a.test/blog/fav.png", icon("""<link rel="shortcut icon" href="fav.png">"""))
    }

    @Test
    fun `a relative href is resolved against the page`() {
        assertEquals("http://a.test/root.png", icon("""<link rel="icon" href="/root.png">"""))
    }

    @Test
    fun `other link relations are not icons`() {
        assertNull(icon("""<link rel="apple-touch-icon" href="touch.png">"""))
        assertNull(icon("""<link rel="stylesheet" href="site.css">"""))
    }

    @Test
    fun `no icon link gives null, and a non-http href is skipped`() {
        assertNull(icon("<html><head></head></html>"))
        assertNull(icon("""<link rel="icon" href="data:image/png;base64,AAAA">"""))
    }

    @Test
    fun `the first usable icon link wins`() {
        assertEquals(
            "http://a.test/blog/second.png",
            icon(
                """<link rel="icon" href="data:x"><link rel="icon" href="second.png">""" +
                    """<link rel="icon" href="third.png">""",
            ),
        )
    }
}
