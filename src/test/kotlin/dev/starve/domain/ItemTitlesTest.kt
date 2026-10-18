package dev.starve.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ItemTitlesTest {
    @Test
    fun `a real title is kept whatever the text`() = assertEquals("Title", ItemTitles.of("Title", "Some text"))

    @Test
    fun `a blank title is taken from the text`() {
        assertEquals("Short post", ItemTitles.of("", "Short post"))
        assertEquals("Short post", ItemTitles.of("   ", "  Short\n\n post "))
    }

    @Test
    fun `a long text is cut at the last word boundary within the limit, with an ellipsis`() {
        val text =
            "Happy opening day of hockey season, NHL fans! Keep up with all 1,344 regular season games " +
                "right here on Bluesky."

        assertEquals("Happy opening day of hockey season, NHL fans! Keep up with all…", ItemTitles.of("", text))
    }

    @Test
    fun `a text of exactly the limit is kept whole, one character more is cut`() {
        val exact = "a".repeat(30) + " " + "b".repeat(33)
        assertEquals(64, exact.length)

        assertEquals(exact, ItemTitles.derived(exact))
        assertEquals("a".repeat(30) + "…", ItemTitles.derived(exact + "b"))
    }

    @Test
    fun `a word ending exactly at the limit is kept`() {
        val text = "a".repeat(30) + " " + "b".repeat(33) + " more"

        assertEquals("a".repeat(30) + " " + "b".repeat(33) + "…", ItemTitles.derived(text))
    }

    @Test
    fun `punctuation left at the cut is dropped before the ellipsis`() {
        val text = "a".repeat(40) + " " + "b".repeat(10) + ", " + "c".repeat(20)

        assertEquals("a".repeat(40) + " " + "b".repeat(10) + "…", ItemTitles.derived(text))
    }

    @Test
    fun `a first word longer than the limit is cut inside it`() =
        assertEquals("x".repeat(64) + "…", ItemTitles.derived("x".repeat(80) + " tail"))

    @Test
    fun `placeholders in square brackets are left out`() {
        assertEquals(
            "Look at this",
            ItemTitles.of("", "Look at this [contains quote post or other embedded content]"),
        )
        assertEquals("before after", ItemTitles.derived("before [image] after"))
    }

    @Test
    fun `no title and no text, or only placeholders, stay untitled`() {
        assertEquals(ItemTitles.UNTITLED, ItemTitles.of("", null))
        assertEquals(ItemTitles.UNTITLED, ItemTitles.of(" ", "  "))
        assertEquals(ItemTitles.UNTITLED, ItemTitles.of("", "[contains quote post or other embedded content]"))
    }
}
