package dev.starve.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ResumeRulesTest {
    @Test
    fun `a position in the middle is kept, with or without a known length`() {
        assertEquals(2472L, ResumeRules.kept(2472, 7200))
        assertEquals(2472L, ResumeRules.kept(2472, null))
    }

    @Test
    fun `five seconds in is kept and four is not`() {
        assertEquals(5L, ResumeRules.kept(5, 100))
        assertNull(ResumeRules.kept(4, 100))
        assertNull(ResumeRules.kept(0, null))
    }

    @Test
    fun `eleven seconds from the end is kept and ten is finished`() {
        assertEquals(89L, ResumeRules.kept(89, 100))
        assertNull(ResumeRules.kept(90, 100))
        assertNull(ResumeRules.kept(100, 100))
        assertNull(ResumeRules.kept(120, 100))
    }
}
