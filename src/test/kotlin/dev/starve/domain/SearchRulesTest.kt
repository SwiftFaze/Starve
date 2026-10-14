package dev.starve.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SearchRulesTest {
    @Test
    fun `each word is quoted and the last one is a prefix`() {
        assertEquals("\"rust\"*", SearchRules.matchExpression("rust"))
        assertEquals("\"rust\" \"async\"*", SearchRules.matchExpression("rust async"))
    }

    @Test
    fun `punctuation separates words and is never an operator`() {
        assertEquals("\"cats\" \"OR\" \"dogs\"*", SearchRules.matchExpression("cats OR dogs"))
        assertEquals("\"a\" \"b\" \"c\"*", SearchRules.matchExpression("a\"b'c"))
        assertEquals("\"world\" \"test\"*", SearchRules.matchExpression("'world' -test"))
        assertEquals("\"title\" \"cats\"*", SearchRules.matchExpression("title:cats"))
    }

    @Test
    fun `combining marks and digits are part of a word`() {
        assertEquals("\"café\"*", SearchRules.matchExpression("café"))
        assertEquals("\"Episode\" \"12\"*", SearchRules.matchExpression("Episode 12"))
    }

    @Test
    fun `text with no letters or digits has no expression`() {
        assertNull(SearchRules.matchExpression("*"))
        assertNull(SearchRules.matchExpression("~!@#$%&()"))
        assertNull(SearchRules.matchExpression("\"'"))
    }

    @Test
    fun `blank text has no expression either`() {
        assertNull(SearchRules.matchExpression(""))
        assertNull(SearchRules.matchExpression("   "))
    }
}
