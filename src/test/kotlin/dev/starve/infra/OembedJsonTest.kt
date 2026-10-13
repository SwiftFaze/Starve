package dev.starve.infra

import dev.starve.application.Oembed
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class OembedJsonTest {
    @Test
    fun `stored details decode to the details that were encoded`() {
        val oembed = Oembed("Artist", "https://i.test/a.jpg", "<p>Hi \"there\"</p>")

        assertEquals(oembed, OembedJson.decode(OembedJson.encode(oembed)))
    }

    @Test
    fun `missing fields survive the round trip as missing`() {
        val oembed = Oembed(null, null, null)

        assertEquals(oembed, OembedJson.decode(OembedJson.encode(oembed)))
    }

    @Test
    fun `nothing stored, or text that is not JSON, decodes to no details`() {
        assertNull(OembedJson.decode(null))
        assertNull(OembedJson.decode("not json"))
    }
}
