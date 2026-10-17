package dev.starve.acceptance

import io.cucumber.java.en.Then
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

/** The timeline's keyboard and screen-reader paths, in the page and in deck.js (keyboard-screen-reader-paths). */
class KeyboardPathSteps {
    @Then("the first focusable element is the link {string} to {string}")
    fun firstFocusable(
        text: String,
        href: String,
    ) {
        val page = body().substring(body().indexOf("<body"))
        val first = requireNotNull(FOCUSABLE.find(page)) { "nothing focusable" }.value
        assertTrue(first.startsWith("<a ") && first.contains("href=\"$href\""), first)
        assertTrue(page.contains("$first$text</a>"), "the first link does not read '$text': $first")
    }

    @Then("the row {string} is labelled by its title link")
    fun rowLabelled(name: String) {
        val article = Regex("<article [^>]*data-title=\"${Regex.escape(name)}\"[^>]*>")
        val tag = requireNotNull(article.find(body())) { "no row $name" }.value
        val id = requireNotNull(LABELLED_BY.find(tag)) { "no label on $tag" }.groupValues[1]
        val title = Regex("<a class=\"title\" id=\"$id\"[^>]*>([^<]*)</a>")
        val link = requireNotNull(title.find(body())) { "no title $id" }
        assertEquals(Html.escape(name), link.groupValues[1])
    }

    @Then("the page has one polite status line for announcements")
    fun statusLine() = assertEquals(1, Regex("role=\"status\"").findAll(body()).count() - messages())

    @Then("the reader pane is not a live region")
    fun readerNotLive() {
        val reader = requireNotNull(Regex("<aside id=\"reader\"[^>]*>").find(body())).value
        assertFalse(reader.contains("aria-live"), reader)
    }

    @Then("the script focuses an opened post's title only when the visitor opened it")
    fun focusesTitle() {
        assertTrue(body().contains("readerOpened(Boolean(config && config.triggeringEvent))"))
        assertTrue(body().contains("if (title && byVisitor)"))
    }

    @Then("the script makes the bar, the obi and the list inert behind a phone post and gives them back on close")
    fun inertBehind() {
        assertTrue(body().contains("el.inert = inert"))
        assertTrue(body().contains("behindReader(phone());"))
        assertTrue(body().contains("behindReader(false);"))
    }

    @Then("the script takes a phone post's Previous and Next out of the tab order")
    fun postNavUntabbable() = assertTrue(body().contains("if (phone()) link.setAttribute('tabindex', '-1');"))

    @Then("the script announces {string}, {string} and the number of results")
    fun announces(
        markRead: String,
        allRead: String,
    ) {
        assertTrue(body().contains("announce('$markRead' + unreadNote())"))
        assertTrue(body().contains("announce('$allRead')"))
        assertTrue(body().contains("announce(resultsNote())"))
    }

    private fun body(): String = World.response.body()

    /** Status lines that are page messages ("Subscribed to ..."), not the announcement line. */
    private fun messages(): Int = Regex("id=\"message\"[^>]*role=\"status\"").findAll(body()).count()

    private companion object {
        val LABELLED_BY = Regex("aria-labelledby=\"([^\"]+)\"")
        val FOCUSABLE = Regex("<(a|button|input|select|textarea)[ >][^>]*>")
    }
}
