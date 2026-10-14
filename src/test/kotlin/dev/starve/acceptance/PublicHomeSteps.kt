package dev.starve.acceptance

import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

/** Steps for specs/features/public-home-page.feature: the landing page, the timeline at /feed and sign-out. */
class PublicHomeSteps {
    private val app = World.app
    private val htmx = mapOf("HX-Request" to "true")

    // ---- the landing page ----------------------------------------------------------------

    @Then("the landing page has a {string} link to {string}")
    fun landingHasLink(
        label: String,
        href: String,
    ) = assertHasLink(body(), label, href)

    @Then("requesting {string} returns the landing page with a {string} link to {string}")
    fun requestingReturnsLanding(
        path: String,
        label: String,
        href: String,
    ) {
        World.response = app.get(path)
        assertEquals(200, World.response.statusCode())
        assertHasLink(body(), label, href)
    }

    @Then("the page contains no images")
    fun containsNoImages() = assertFalse(body().contains("<img"), body())

    // ---- sign-out ------------------------------------------------------------------------

    @Then("the page has no sign-out control")
    fun hasNoSignOut() = assertNoSignOut(body())

    @Then("the page {string} has no sign-out control")
    fun pageHasNoSignOut(path: String) = assertNoSignOut(app.get(path).body())

    @Then("the top bar has a {string} button that posts {string} with the CSRF token")
    fun barHasSignOut(
        label: String,
        action: String,
    ) = assertSignOutIn(body(), label, action)

    @Then("the response contains the top bar with the {string} button")
    fun responseHasBarWithSignOut(label: String) = assertSignOutIn(body(), label, "/logout")

    @Then("the {string} form works without JavaScript")
    fun signOutWorksWithoutJavaScript(label: String) {
        val form = signOutForm(body())
        assertTrue(form.contains("method=\"post\"") && form.contains("action=\"/logout\""), form)
        assertTrue(label in form, form)
    }

    /** Posts the form the top bar carries, with the fields it holds, as a browser submitting it would. */
    @When("I sign out with the top bar control")
    fun signOut() {
        val form = signOutForm(app.get("/feed").body())
        val fields = HIDDEN.findAll(form).map { it.groupValues[1] to Html.unescape(it.groupValues[2]) }.toList()
        World.response = app.postForm("/logout", *fields.toTypedArray())
        app.forgetSignIn()
    }

    @When("I post {string} without its CSRF token")
    fun postWithoutCsrf(path: String) {
        World.response = app.postFormWithoutCsrfToken(path)
    }

    // ---- links on the timeline pages -----------------------------------------------------

    @Then("the wordmark links to {string}")
    fun wordmarkLinksTo(href: String) = assertEquals(href, wordmarkHref(body()))

    @Then("the wordmark on {string} links to {string}")
    fun wordmarkOnLinksTo(
        path: String,
        href: String,
    ) = assertEquals(href, wordmarkHref(app.get(path).body()))

    @Then("the Timeline tab links to {string} and requests {string} via htmx")
    fun timelineTabRequests(
        href: String,
        requested: String,
    ) {
        val tab = timelineTab()
        assertEquals(href, attribute(tab, "href"), tab)
        assertEquals(requested, attribute(tab, "hx-get"), tab)
    }

    @Then("the Timeline tab links to {string}")
    fun timelineTabLinks(href: String) = assertEquals(href, attribute(timelineTab(), "href"))

    @Then("the search form submits to {string}")
    fun searchFormSubmitsTo(path: String) {
        val form = body().substringAfter("<form class=\"search\"").substringBefore("</form>")
        assertTrue(form.contains("action=\"$path\""), form)
        assertTrue(form.contains("hx-get=\"$path\""), form)
    }

    // ---- paging and plain posts ----------------------------------------------------------

    @Then("the {string} link requests a {string} URL")
    fun linkRequestsUrl(
        label: String,
        prefix: String,
    ) {
        val url = Html.unescape(attribute(requireNotNull(Html.anchor(body(), label)) { "no $label link" }, "hx-get"))
        assertTrue(url.startsWith(prefix), url)
    }

    @When("I follow the {string} link via htmx")
    fun followLinkViaHtmx(label: String) {
        val tag = requireNotNull(Html.anchor(body(), label)) { "no $label link" }
        World.response = app.get(Html.unescape(attribute(tag, "hx-get")), htmx)
    }

    @Then("the response is only the next page of items, not a full document")
    fun onlyNextPage() {
        assertFalse(body().contains("<!DOCTYPE"), "a full document")
        assertTrue(body().contains("<article"), "no items")
    }

    @When("I submit {string} for {string} without htmx")
    fun submitWithoutHtmx(
        action: String,
        title: String,
    ) {
        val id =
            app
                .jdbc()
                .sql("SELECT id FROM items WHERE title = ?")
                .param(title)
                .query(Long::class.java)
                .single()
        val endpoint = requireNotNull(ACTIONS[action]) { "unknown action $action" }
        World.response = app.postForm("/items/$id/$endpoint")
    }

    // ---- helpers -------------------------------------------------------------------------

    private fun body(): String = World.response.body()

    private fun assertHasLink(
        page: String,
        label: String,
        href: String,
    ) = assertTrue(Html.anchor(page, label)?.contains("href=\"$href\"") == true, "no '$label' link to $href")

    private fun assertNoSignOut(page: String) = assertFalse(page.contains("action=\"/logout\""), "has a sign-out form")

    private fun assertSignOutIn(
        page: String,
        label: String,
        action: String,
    ) {
        val bar = page.substringAfter("<header").substringBefore("</header>")
        val form = signOutForm(bar)
        assertTrue(form.contains("action=\"$action\"") && form.contains("method=\"post\""), form)
        assertTrue(Regex("<button[^>]*>\\s*$label\\s*</button>").containsMatchIn(form), form)
        assertTrue(form.contains("name=\"_csrf\""), "no CSRF token in $form")
    }

    private fun signOutForm(page: String): String = requireNotNull(SIGN_OUT.find(page)) { "no sign-out form" }.value

    private fun wordmarkHref(page: String): String {
        val tag = requireNotNull(WORDMARK.find(page)) { "no wordmark" }.value
        return attribute(tag, "href")
    }

    private fun timelineTab(): String =
        requireNotNull(Regex("<a class=\"tab\"[^>]*>\\s*Timeline\\s*</a>").find(body())) { "no Timeline tab" }.value

    private fun attribute(
        tag: String,
        name: String,
    ): String = requireNotNull(Regex("""\s$name="([^"]*)"""").find(tag)) { "no $name in $tag" }.groupValues[1]

    private companion object {
        val ACTIONS = mapOf("mark read" to "read", "favourite" to "favourite", "read later" to "later")
        val SIGN_OUT = Regex("<form[^>]*action=\"/logout\".*?</form>", RegexOption.DOT_MATCHES_ALL)
        val HIDDEN = Regex("<input[^>]*type=\"hidden\"[^>]*name=\"([^\"]*)\"[^>]*value=\"([^\"]*)\"")
        val WORDMARK = Regex("<(?:h1|p) class=\"wordmark\"><a [^>]*>")
    }
}
