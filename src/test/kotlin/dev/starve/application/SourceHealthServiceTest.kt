package dev.starve.application

import dev.starve.domain.Source
import dev.starve.domain.SourceActivity
import dev.starve.domain.Validators
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class SourceHealthServiceTest {
    private val now = Instant.parse("2026-10-18T12:00:00Z")

    @Test
    fun `each of the user's sources gets its counted activity, judged now`() {
        val asked = mutableListOf<Triple<Long, Instant, Instant>>()
        val stats =
            SourceStatsRepository { owner, windowStart, at ->
                asked += Triple(owner, windowStart, at)
                mapOf(1L to SourceActivity(now, 4, 2, 0, 0))
            }
        val sources =
            MemorySources(
                mutableListOf(
                    Source(1, "http://a.test/f", "A", Validators(), now, null),
                    Source(2, "http://b.test/f", "B", Validators(), now, null),
                    Source(3, "http://c.test/f", "C", Validators(), null, null),
                    Source(4, "http://d.test/f", "D", Validators(), now, null),
                ),
                mutableMapOf(4L to OTHER_OWNER),
            )

        val roster = SourceHealthService(sources, stats, Clock.fixed(now, ZoneOffset.UTC)).roster(OWNER)

        assertEquals(listOf(Triple(OWNER, now.minus(Duration.ofDays(28)), now)), asked)
        assertEquals(listOf(1L, 2L, 3L), roster.map { it.source.id })
        assertEquals(listOf(1.0, 0.0, 0.0), roster.map { it.health.postsPerWeek })
        assertEquals(listOf(false, true, false), roster.map { it.health.quiet })
    }
}
