package dev.starve.acceptance

import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import java.net.URI

/** Steps for sources-keeps-playing.feature: page tabs and Sources forms that swap around the dock. */
class SourcesPlayingSteps {
    private val app = World.app
    private val htmx = mapOf("HX-Request" to "true")

    // ---- actions -------------------------------------------------------------------------

    @When("I request the page {string} via htmx")
    fun requestPageViaHtmx(path: String) {
        World.response = app.get(path, htmx)
        World.pages = listOf(World.response.body())
    }

    @When("I add the feed URL {string} via htmx")
    fun addFeedViaHtmx(url: String) {
        World.response = app.postForm("/sources", htmx, "url" to url)
        World.sourcesPage = World.response.body()
    }

    @When("I create the group {string} via htmx")
    fun createGroupViaHtmx(name: String) {
        World.response = app.postForm("/groups", htmx, "name" to name)
        World.sourcesPage = World.response.body()
    }

    /** Posts the removal, then follows the redirect the way the browser's request under htmx does. */
    @When("I remove the source {string} via htmx")
    fun removeSourceViaHtmx(title: String) {
        val id =
            app
                .jdbc()
                .sql("SELECT id FROM sources WHERE title = ?")
                .param(title)
                .query(Long::class.java)
                .single()
        val redirect = app.postForm("/sources/$id/delete", htmx)
        assertTrue(redirect.statusCode() in 300..399, "no redirect: ${redirect.statusCode()}")
        World.response = app.get(URI.create(redirect.headers().firstValue("Location").orElseThrow()).path, htmx)
    }

    // ---- page tabs -----------------------------------------------------------------------

    @Then("every page tab requests its page via htmx without swapping the dock")
    fun tabsSwapPage() =
        tabs().forEach { tab ->
            val href = attribute(tab, "href")
            assertEquals(href, attribute(tab, "hx-get"), tab)
            assertSwapsPage(tab)
        }

    @Then("every page tab pushes its URL to the browser history")
    fun tabsPushUrl() = tabs().forEach { assertTrue(it.contains("hx-push-url=\"true\""), it) }

    @Then("every page tab still works without JavaScript")
    fun tabsHaveHref() = tabs().forEach { assertTrue(HREF.containsMatchIn(it), it) }

    @Then("the link to {string} does not request its page via htmx")
    fun linkIsPlain(href: String) {
        val link = requireNotNull(Regex("<a [^>]*href=\"$href\"[^>]*>").find(body())) { "no link to $href" }.value
        assertFalse(link.contains("hx-"), link)
    }

    // ---- the dock ------------------------------------------------------------------------

    @Then("the page has a player dock")
    fun hasDock() = assertTrue(body().contains("id=\"dock\""), "no dock in ${body()}")

    @Then("the response is a complete HTML document")
    fun isCompleteDocument() {
        assertTrue(body().trimStart().startsWith("<!DOCTYPE html>"), body())
        assertTrue(body().contains("</html>"), body())
    }

    @Then("the page loads the htmx script")
    fun loadsHtmxScript() = assertTrue(body().contains("<script src=\"/htmx.min.js\">"), body())

    @Then("no page tab or form on the page swaps a region containing the dock")
    fun noRegionHoldsDock() {
        val swappers = tabs() + FORM.findAll(body()).map { it.value }
        val regions = swappers.flatMap { regionIds(it) }.toSet()
        assertTrue(regions.isNotEmpty(), "nothing swaps anything")
        regions.forEach { assertFalse(region(it).contains("id=\"dock\""), "#$it holds the dock") }
    }

    // ---- Sources forms -------------------------------------------------------------------

    @Then("the add-a-source form submits via htmx into the page content")
    fun addFormSwaps() = formsTo { it == "/sources" }.forEach { assertSwapsPage(it, "hx-post") }

    @Then("every source row's set-group and remove forms submit via htmx into the page content")
    fun sourceRowFormsSwap() = rowForms(SOURCE_ROW).forEach { assertSwapsPage(it, "hx-post") }

    @Then("the create-group form submits via htmx into the page content")
    fun createFormSwaps() = formsTo { it == "/groups" }.forEach { assertSwapsPage(it, "hx-post") }

    @Then("every group row's rename and delete forms submit via htmx into the page content")
    fun groupRowFormsSwap() = rowForms(GROUP_ROW).forEach { assertSwapsPage(it, "hx-post") }

    @Then("every Sources form still works without JavaScript")
    fun formsWorkWithoutJavaScript() =
        sourcesForms().forEach {
            assertTrue(it.contains("method=\"post\"") && ACTION.containsMatchIn(it), it)
        }

    @Then("the message is inside the page content a Sources form swaps")
    fun messageInSwappedContent() {
        val swapped = regionIds(formsTo { it == "/sources" || it == "/groups" }.first())
        assertTrue(swapped.any { region(it, World.sourcesPage).contains("class=\"message\"") }, World.sourcesPage)
    }

    // ---- helpers -------------------------------------------------------------------------

    private fun assertSwapsPage(
        tag: String,
        verb: String? = null,
    ) {
        val expected =
            listOf(
                "hx-target=\"#sheet\"",
                "hx-select=\"#sheet\"",
                "hx-select-oob=\"#bar,#series,#panel\"",
                "hx-swap=\"outerHTML\"",
            )
        expected.forEach { assertTrue(tag.contains(it), "no $it in $tag") }
        verb?.let { assertTrue(tag.contains("$it=\""), "no $it in $tag") }
    }

    private fun attribute(
        tag: String,
        name: String,
    ): String = requireNotNull(Regex("""\s$name="([^"]*)"""").find(tag)) { "no $name in $tag" }.groupValues[1]

    private fun tabs(): List<String> =
        TAB
            .findAll(body())
            .map { it.value }
            .toList()
            .also { assertEquals(2, it.size, body()) }

    private fun sourcesForms(): List<String> =
        FORM
            .findAll(body())
            .map { it.value }
            .toList()
            .also { assertTrue(it.isNotEmpty()) }

    private fun formsTo(accept: (String) -> Boolean): List<String> =
        sourcesForms().filter { accept(attribute(it, "action")) }.also { assertTrue(it.isNotEmpty()) }

    private fun rowForms(row: Regex): List<String> =
        sourcesForms().filter { row.matches(attribute(it, "action")) }.also { assertTrue(it.isNotEmpty()) }

    /** The ids a tab or form takes its content from or puts it into: `hx-target`, `hx-select`, `hx-select-oob`. */
    private fun regionIds(tag: String): List<String> =
        listOf("hx-target", "hx-select", "hx-select-oob")
            .mapNotNull { Regex("""\s$it="([^"]*)"""").find(tag)?.groupValues?.get(1) }
            .flatMap { it.split(",") }
            .map { it.trim() }
            .filter { it.startsWith("#") }
            .map { it.removePrefix("#") }

    /** The markup of the element with the given id, to its balanced closing tag. */
    private fun region(
        id: String,
        page: String = body(),
    ): String {
        val open = requireNotNull(Regex("<(\\w+)[^>]*\\sid=\"$id\"[^>]*>").find(page)) { "no #$id in $page" }
        val name = open.groupValues[1]
        val tags = Regex("</?$name\\b[^>]*>").findAll(page, open.range.first).iterator()
        var depth = 0
        while (tags.hasNext()) {
            val tag = tags.next()
            depth += if (tag.value.startsWith("</")) -1 else 1
            if (depth == 0) return page.substring(open.range.first, tag.range.last + 1)
        }
        error("#$id is never closed in $page")
    }

    private fun body(): String = World.response.body()

    private companion object {
        val TAB = Regex("<a class=\"tab\"[^>]*>")
        val FORM = Regex("<form[^>]*>")
        val HREF = Regex("href=\"/[^\"]*\"")
        val ACTION = Regex("action=\"/[^\"]*\"")
        val SOURCE_ROW = Regex("/sources/\\d+/(group|delete)")
        val GROUP_ROW = Regex("/groups/\\d+/(rename|delete)")
    }
}
