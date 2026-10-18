package dev.starve.domain

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class ArticleRulesTest {
    private val now = Instant.parse("2026-10-12T12:00:00Z")

    @Test
    fun `a body of 499 characters is a teaser and one of 500 is not`() {
        assertTrue(ArticleRules.isTeaser(499))
        assertFalse(ArticleRules.isTeaser(500))
    }

    @Test
    fun `a never checked item is due`() {
        assertTrue(ArticleRules.isDue(null, now))
    }

    @Test
    fun `a check exactly a day old is not yet due and one a moment older is`() {
        assertFalse(ArticleRules.isDue(now.minus(ArticleRules.RECHECK_AFTER), now))
        assertTrue(ArticleRules.isDue(now.minus(ArticleRules.RECHECK_AFTER).minusMillis(1), now))
    }

    @Test
    fun `a feed-only host covers its links exactly, ignoring case and spaces in the list`() {
        val hosts = FeedOnlyHosts(listOf(" GitHub.com ", "bsky.app", ""))

        assertTrue(hosts.covers("https://github.com/torvalds/linux/commit/abc"))
        assertTrue(hosts.covers("https://BSKY.app/profile/x/post/1"))
        assertTrue(hosts.covers(" https://github.com/a "))
        assertFalse(hosts.covers("https://gist.github.com/a"))
        assertFalse(hosts.covers("https://notgithub.com/a"))
        assertFalse(hosts.covers("https://blog.example/github.com"))
    }

    @Test
    fun `a link without a host or that does not parse is not covered`() {
        val hosts = FeedOnlyHosts(listOf("github.com"))

        assertFalse(hosts.covers("not a url"))
        assertFalse(hosts.covers("mailto:a@github.com"))
        assertFalse(hosts.covers("https://github.com/a b"))
    }
}
