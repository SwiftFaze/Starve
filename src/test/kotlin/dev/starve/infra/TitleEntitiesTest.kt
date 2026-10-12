package dev.starve.infra

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TitleEntitiesTest {
    @Test
    fun `decimal, hex and named references become the characters they name`() {
        assertEquals("There’s", TitleEntities.decode("There&#8217;s"))
        assertEquals("a — b", TitleEntities.decode("a &#x2014; b"))
        assertEquals("Rock & Roll", TitleEntities.decode("Rock &amp; Roll"))
        assertEquals("café", TitleEntities.decode("caf&eacute;"))
    }

    @Test
    fun `a bare ampersand and an unknown reference are left unchanged`() {
        assertEquals("Tom & Jerry", TitleEntities.decode("Tom & Jerry"))
        assertEquals("Tom & Jerry &notareal;", TitleEntities.decode("Tom & Jerry &notareal;"))
    }

    @Test
    fun `decoding is a single pass`() {
        assertEquals("Fish &amp; Chips", TitleEntities.decode("Fish &amp;amp; Chips"))
    }

    @Test
    fun `text without references is returned as it is`() {
        assertEquals("Plain title", TitleEntities.decode("Plain title"))
    }
}
