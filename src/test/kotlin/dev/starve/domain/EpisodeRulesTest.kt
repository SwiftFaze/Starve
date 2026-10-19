package dev.starve.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class EpisodeRulesTest {
    @Test
    fun `plain seconds, minutes and seconds, and hours are read`() {
        assertEquals(3723L, EpisodeRules.durationSeconds("3723"))
        assertEquals(62L * 60 + 3, EpisodeRules.durationSeconds("62:03"))
        assertEquals(3723L, EpisodeRules.durationSeconds(" 1:02:03 "))
        assertEquals(3723L, EpisodeRules.durationSeconds("01:02:03.500"))
    }

    @Test
    fun `blank, malformed, too many parts or zero is no duration`() {
        listOf(null, "", "  ", "abc", "1:xx", "1::2", "-5", "1:2:3:4", "0", "00:00").forEach {
            assertNull(EpisodeRules.durationSeconds(it), "for '$it'")
        }
    }
}
