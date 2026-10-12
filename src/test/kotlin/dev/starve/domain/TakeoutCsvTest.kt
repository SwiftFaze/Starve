package dev.starve.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TakeoutCsvTest {
    private val template = "https://yt.test/feed?channel_id={id}"
    private val idA = "UC" + "a".repeat(22)
    private val idB = "UC" + "B_-".repeat(7) + "x"

    private fun parse(text: String) = requireNotNull(TakeoutCsv.parse(text, template))

    @Test
    fun `each valid row becomes a feed url titled with the channel title`() {
        val parsed = parse("Channel Id,Channel Url,Channel Title\n$idA,http://x/$idA,Alpha\n$idB,http://x/$idB,Beta\n")

        assertEquals(
            listOf(
                ImportEntry("https://yt.test/feed?channel_id=$idA", "Alpha", null),
                ImportEntry("https://yt.test/feed?channel_id=$idB", "Beta", null),
            ),
            parsed.entries,
        )
        assertEquals(emptyList<String>(), parsed.failures)
    }

    @Test
    fun `a byte order mark, CRLF line endings and quoted titles with commas, quotes and line breaks are read`() {
        val text = "\uFEFFChannel Id,Channel Url,Channel Title\r\n$idA,u,\"Doe, \"\"Jane\"\"\nTwo\"\r\n$idB,u,Plain"

        assertEquals(listOf("Doe, \"Jane\"\nTwo", "Plain"), parse(text).entries.map { it.title })
    }

    @Test
    fun `columns are found by header name in any order`() {
        val parsed = parse("Channel Title,Channel Id\nAlpha,$idA\n")

        assertEquals(listOf("Alpha"), parsed.entries.map { it.title })
    }

    @Test
    fun `invalid rows are failed with their row number while the rest import`() {
        val bad = "UC" + "a".repeat(21)
        val text = "Channel Id,Channel Url,Channel Title\n$idA,u,A\nnot-an-id,u,B\n\n$bad,u,C\n$idB\n,u,D\n${idA}x,u,E"

        val parsed = parse(text)

        assertEquals(listOf("A", idB.let { "https://yt.test/feed?channel_id=$it" }), parsed.entries.map { it.title })
        assertEquals(
            listOf(3, 5, 7, 8).map { "row $it: invalid channel id" },
            parsed.failures,
        )
    }

    @Test
    fun `row numbers count each CRLF, bare CR and blank line as one line break`() {
        val header = "Channel Id,Channel Title"
        val text = header + "\r\nbad1,x\r\n\r\nbad2,x\rbad3,x\n\nbad4,x"

        assertEquals(
            listOf(2, 4, 5, 7).map { "row $it: invalid channel id" },
            parse(text).failures,
        )
    }

    @Test
    fun `a last row ending in an empty field without a line break is still read`() {
        val parsed = parse("Channel Id,Channel Title\n$idA,")

        assertEquals(listOf("https://yt.test/feed?channel_id=$idA"), parsed.entries.map { it.title })
    }

    @Test
    fun `a blank title falls back to the feed url`() {
        val parsed = parse("Channel Id,Channel Title\n$idA,  \n")

        assertEquals(listOf("https://yt.test/feed?channel_id=$idA"), parsed.entries.map { it.title })
    }

    @Test
    fun `text without a Channel Id header is not a takeout csv`() {
        assertNull(TakeoutCsv.parse("just some text\nwith no header", template))
        assertNull(TakeoutCsv.parse("", template))
    }
}
