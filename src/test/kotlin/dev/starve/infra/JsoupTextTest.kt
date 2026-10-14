package dev.starve.infra

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class JsoupTextTest {
    @Test
    fun `tags are dropped and whitespace collapses`() {
        assertEquals("Hello big world", JsoupText.plainText("<p>Hello  <b>big</b>\n</p><p>world</p>"))
    }
}
