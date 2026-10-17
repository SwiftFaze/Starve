package dev.starve.acceptance

import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

/** The styled error page (error-page.feature), requested the way a browser asks for a page: Accept text/html. */
class ErrorPageSteps {
    private val app = World.app

    @When("I open {string} in a browser")
    fun openInBrowser(path: String) {
        World.response = app.get(path, BROWSER)
    }

    @When("I open the detail view of the item {string} in a browser")
    fun openDetailInBrowser(title: String) = openInBrowser("/items/${app.itemId(title)}/detail")

    @When("I post {string} without its CSRF token in a browser")
    fun postWithoutCsrf(path: String) {
        World.response = app.browser.postForm(path, BROWSER, emptyList(), csrf = false)
    }

    @Then("the error page names the status {string} and says {string}")
    fun namesStatus(
        code: String,
        heading: String,
    ) {
        assertEquals(code, textOf(group(OBI_NAME)), body())
        assertEquals(heading, textOf(group(SHEET_TITLE)), body())
    }

    @Then("the error page links back to {string} at {string}")
    fun linksBack(
        label: String,
        href: String,
    ) {
        val link = requireNotNull(BACK.find(body())) { "no back link in ${body()}" }
        assertEquals(label, link.groupValues[2].trim())
        assertTrue(link.groupValues[1].contains("href=\"$href\""), link.value)
    }

    @Then("the error page has the signed-in bar")
    fun signedInBar() {
        assertTrue(bar().contains("class=\"signout\""), bar())
        assertEquals(1, BAR.findAll(body()).count(), body())
    }

    @Then("the error page has the public bar")
    fun publicBar() {
        assertTrue(bar().contains(">Sign in</a>") || bar().contains(">Docs</a>"), bar())
        assertFalse(bar().contains("class=\"signout\""), bar())
        assertEquals(1, BAR.findAll(body()).count(), body())
    }

    @Then("the error page shows no framework details")
    fun noFrameworkDetails() {
        val page = body()
        listOf("Whitelabel", "Exception", "explicit mapping", "trace", "at org.").forEach {
            assertFalse(page.contains(it), "'$it' on the error page: $page")
        }
    }

    @Then("the error page matches the one for {string}")
    fun matchesPage(path: String) {
        val mine = body()
        val other = app.get(path, BROWSER)
        assertEquals(other.statusCode(), World.response.statusCode())
        // The sign-out form's CSRF token is masked afresh on every response, so it is the one part allowed to differ.
        assertEquals(other.body().replace(CSRF, ""), mine.replace(CSRF, ""))
    }

    private fun body(): String = World.response.body()

    private fun bar(): String = requireNotNull(BAR.find(body())) { "no bar in ${body()}" }.value

    private fun group(pattern: Regex): String = requireNotNull(pattern.find(body())) { "no $pattern in ${body()}" }.groupValues[1]

    private fun textOf(html: String): String = html.replace(Regex("<[^>]+>"), "").trim()

    private companion object {
        val BROWSER = mapOf("Accept" to "text/html,application/xhtml+xml")
        val OBI_NAME = Regex("<h1 class=\"series[^\"]*\"[^>]*>(.*?)</h1>", RegexOption.DOT_MATCHES_ALL)
        val SHEET_TITLE = Regex("<h2 class=\"sheet-title\"[^>]*>(.*?)</h2>", RegexOption.DOT_MATCHES_ALL)
        val BACK = Regex("(<a class=\"read-toggle filled\"[^>]*>)([^<]*)</a>")
        val CSRF = Regex("name=\"_csrf\" value=\"[^\"]*\"")
        val BAR = Regex("<header class=\"bar\".*?</header>", RegexOption.DOT_MATCHES_ALL)
    }
}
