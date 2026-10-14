package dev.starve.domain

import java.time.Duration
import java.time.Instant

/** When a post's article page is fetched: only teaser-only posts, and each attempt is repeated after a day. */
object ArticleRules {
    /** A stored body with fewer plain-text characters than this is a teaser. */
    const val TEASER_LENGTH = 500

    private const val RECHECK_HOURS = 24L

    /** A checked item is fetched again once its last check is older than this. */
    val RECHECK_AFTER: Duration = Duration.ofHours(RECHECK_HOURS)

    fun isTeaser(textLength: Int): Boolean = textLength < TEASER_LENGTH

    /** Due when never checked, or when the last check is older than [RECHECK_AFTER]. */
    fun isDue(
        checkedAt: Instant?,
        now: Instant,
    ): Boolean = checkedAt == null || checkedAt.isBefore(now.minus(RECHECK_AFTER))
}
