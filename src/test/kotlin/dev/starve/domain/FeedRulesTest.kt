package dev.starve.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class FeedRulesTest {
    @Test
    fun `item key prefers guid then link`() {
        assertEquals("g", FeedRules.itemKey("g", "l"))
        assertEquals("l", FeedRules.itemKey(" ", "l"))
        assertEquals("l", FeedRules.itemKey(null, "l"))
        assertNull(FeedRules.itemKey(null, ""))
    }

    @Test
    fun `only http and https urls with a host are accepted`() {
        assertTrue(FeedRules.isHttpUrl("http://a.example/feed"))
        assertTrue(FeedRules.isHttpUrl(" HTTPS://a.example/feed "))
        assertFalse(FeedRules.isHttpUrl("ftp://a.example/feed"))
        assertFalse(FeedRules.isHttpUrl("file:///etc/passwd"))
        assertFalse(FeedRules.isHttpUrl("not a url"))
        assertFalse(FeedRules.isHttpUrl("http://"))
    }

    @Test
    fun `retention cutoff is now minus max age`() {
        val now = Instant.parse("2026-10-11T00:00:00Z")
        assertEquals(Instant.parse("2026-07-13T00:00:00Z"), FeedRules.retentionCutoff(now, Duration.ofDays(90)))
    }
}
