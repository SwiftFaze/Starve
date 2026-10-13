package dev.starve.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class FaviconRulesTest {
    private val now = Instant.parse("2026-10-13T12:00:00Z")

    @Test
    fun `a source never checked is due`() {
        assertTrue(FaviconRules.isDue(null, now))
    }

    @Test
    fun `a check less than seven days old is not due, and one older than seven days is`() {
        assertFalse(FaviconRules.isDue(now.minusSeconds(6 * DAY), now))
        assertFalse(FaviconRules.isDue(now.minusSeconds(7 * DAY), now))
        assertTrue(FaviconRules.isDue(now.minusSeconds(7 * DAY + 1), now))
        assertTrue(FaviconRules.isDue(now.minusSeconds(8 * DAY), now))
    }

    @Test
    fun `the site is the feed's site link when it is http or https`() {
        val site = FaviconRules.siteUrl(" https://blog.example/ ", "https://feeds.example/x")
        assertEquals("https://blog.example/", site)
    }

    @Test
    fun `the site falls back to the origin of the feed url when the link is absent or not http`() {
        assertEquals("https://feeds.example/", FaviconRules.siteUrl(null, "https://feeds.example/a/b.xml?x=1"))
        assertEquals("https://feeds.example/", FaviconRules.siteUrl("javascript:alert(1)", "https://feeds.example/a"))
    }

    @Test
    fun `the root icon sits at the favicon path of the site's origin`() {
        assertEquals("http://h.test:8080/favicon.ico", FaviconRules.rootIconUrl("http://h.test:8080/site/page"))
    }

    private companion object {
        const val DAY = 24 * 60 * 60L
    }
}
