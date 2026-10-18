package dev.starve.acceptance

import io.cucumber.java.en.Then
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse

/** The heading outline of a page or a fragment (heading-outline.feature). */
class HeadingSteps {
    @Then("the page has exactly one h1, reading {string}")
    fun oneH1(name: String) = assertEquals(listOf("h1 $name"), headings(body()).filter { it.startsWith("h1 ") })

    @Then("no other heading on the page repeats {string}")
    fun noRepeat(name: String) {
        val repeats = headings(body()).filter { !it.startsWith("h1 ") && it.substringAfter(' ').equals(name, true) }
        assertEquals(emptyList<String>(), repeats)
    }

    @Then("the wordmark is not a heading")
    fun wordmarkNotHeading() = assertFalse(Regex("<h[1-6] class=\"wordmark\"").containsMatchIn(body()), body())

    @Then("the reader's body has the headings {string}")
    fun readerBodyHeadings(expected: String) {
        val readerBody = requireNotNull(READER_BODY.find(body())) { "no reader body in ${body()}" }.groupValues[1]
        assertEquals(expected, headings(readerBody).joinToString(", "))
    }

    private fun body(): String = World.response.body()

    /** Each heading as "h<level> <text>", in document order, with tags stripped and space collapsed. */
    private fun headings(html: String): List<String> =
        HEADING
            .findAll(html)
            .map { "h${it.groupValues[1]} ${words(it.groupValues[2].replace(TAG, ""))}" }
            .toList()

    private fun words(text: String): String = text.split(WHITESPACE).filter { it.isNotEmpty() }.joinToString(" ")

    private companion object {
        val HEADING = Regex("<h([1-6])(?=[^0-9a-zA-Z])[^>]*>(.*?)</h[1-6]>", RegexOption.DOT_MATCHES_ALL)
        val READER_BODY = Regex("<div class=\"reader-body\"[^>]*>(.*)</div>", RegexOption.DOT_MATCHES_ALL)
        val TAG = Regex("<[^>]+>")
        val WHITESPACE = Regex("\\s+")
    }
}
