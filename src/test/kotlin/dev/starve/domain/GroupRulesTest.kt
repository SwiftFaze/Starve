package dev.starve.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GroupRulesTest {
    @Test
    fun `names are trimmed`() = assertEquals(NameCheck.Valid("News"), GroupRules.checkName("  News  "))

    @Test
    fun `a blank name is rejected`() = assertEquals(NameCheck.Blank, GroupRules.checkName("   "))

    @Test
    fun `fifty characters are allowed and fifty-one are not`() {
        assertEquals(NameCheck.Valid("x".repeat(50)), GroupRules.checkName("x".repeat(50)))
        assertEquals(NameCheck.TooLong, GroupRules.checkName("x".repeat(51)))
    }
}
