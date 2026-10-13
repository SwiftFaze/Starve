package dev.starve.infra

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SearchTextTest {
    @Test
    fun `markup is dropped and only the text is kept`() {
        val html = "<p>Plain <a href='https://zzz.example/page' class='fancyclass'>words</a></p>"

        assertEquals("Plain words", SearchText.bodyText(html))
    }

    @Test
    fun `entities are decoded in the indexed text`() {
        assertEquals("Tom & Jerry", SearchText.bodyText("<p>Tom &amp; Jerry</p>"))
    }

    @Test
    fun `a missing body has no text`() {
        assertNull(SearchText.bodyText(null))
    }
}
