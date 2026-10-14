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
}
