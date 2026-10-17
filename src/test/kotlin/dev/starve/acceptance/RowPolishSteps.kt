package dev.starve.acceptance

import io.cucumber.java.en.Then
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

/** The toggles' labels and the stylesheet rules of row-reader-polish.feature. */
class RowPolishSteps {
    @Then("the star of the row {string} is labelled {string}")
    fun rowStarLabel(
        title: String,
        label: String,
    ) = assertEquals(label, tip(form(row(title), "star")))

    @Then("the Later toggle of the row {string} is labelled {string}")
    fun rowLaterLabel(
        title: String,
        label: String,
    ) = assertEquals(label, tip(form(row(title), "later")))

    @Then("the returned star is labelled {string}")
    fun returnedStar(label: String) = assertEquals(label, tip(form(body(), "star")))

    @Then("the returned Later toggle is labelled {string}")
    fun returnedLater(label: String) = assertEquals(label, tip(form(body(), "later")))

    @Then("the reader's running text is capped at 68ch")
    fun readerMeasure() {
        val measure = rule(".reader-body :is(p, li, dd, blockquote, h3, h4, h5, h6)")
        assertTrue(measure.contains("max-width: 68ch"), measure)
    }

    @Then("a row's thumbnail is drawn over the sunk surface")
    fun thumbPlaceholder() = assertTrue(rule(".thumb").contains("var(--sunk)"), rule(".thumb"))

    @Then("only a desktop pointer hides a row's unpressed star and clock until hover or focus")
    fun desktopOnlyHide() {
        val media = "@media (hover: hover) and (pointer: fine) and (min-width: 60.0625rem) {"
        val start = body().indexOf(media)
        assertTrue(start >= 0, "no desktop-pointer block")
        val hide = ".item:not(:hover):not(:focus-within) .actions $UNPRESSED"
        val at = body().indexOf(hide)
        assertTrue(at > start, "the unpressed toggles are not hidden inside the desktop-pointer block")
        val hides = Regex(Regex.escape(hide)).findAll(body()).count()
        assertEquals(1, hides, "hidden outside the desktop-pointer block too")
    }

    private fun body(): String = World.response.body()

    private fun row(title: String): String {
        val start = body().indexOf("data-title=\"${Html.escape(title)}\"")
        assertTrue(start >= 0, "no row $title")
        return body().substring(start, body().indexOf("</article>", start))
    }

    private fun form(
        html: String,
        kind: String,
    ): String {
        val start = html.indexOf("<form method=\"post\" class=\"$kind\"")
        assertTrue(start >= 0, "no $kind toggle in $html")
        return html.substring(start, html.indexOf("</form>", start))
    }

    private fun tip(form: String): String = requireNotNull(Regex("data-tip=\"([^\"]*)\"").find(form)) { "no label in $form" }.groupValues[1]

    private companion object {
        const val UNPRESSED = ":is(.star, .later):has(> button[aria-pressed=\"false\"])"
    }

    /** The first rule whose selector is exactly [selector], as written. */
    private fun rule(selector: String): String {
        val start = body().indexOf("$selector {")
        assertTrue(start >= 0, "no rule $selector")
        return body().substring(start, body().indexOf('}', start))
    }
}
