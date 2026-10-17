package dev.starve.acceptance

import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

/** Steps for specs/features/confirm-destructive-actions.feature: the confirmation dialog and the add-source error. */
class ConfirmSteps {
    private val app = World.app

    // ---- actions -------------------------------------------------------------------------

    @When("I ask to remove the source {string}")
    fun askToRemove(title: String) = fetch("/sources?remove=${app.sourceId(title)}")

    @When("I ask to delete the group {string}")
    fun askToDeleteGroup(name: String) = fetch("/sources?delete-group=${app.groupId(name)}")

    // ---- the rows ------------------------------------------------------------------------

    @Then("the row of the {word} {string} has a {string} link to {string}")
    fun rowHasLink(
        kind: String,
        name: String,
        label: String,
        href: String,
    ) {
        val anchor = requireNotNull(Html.anchor(row(kind, name), label)) { "no '$label' link in ${row(kind, name)}" }
        assertTrue(anchor.contains("href=\"${Html.escape(withId(href, kind, name))}\""), anchor)
    }

    @Then("the row of the {word} {string} has no form that posts {string}")
    fun rowHasNoForm(
        kind: String,
        name: String,
        action: String,
    ) = assertFalse(posts(kind, name, action), row(kind, name))

    // ---- the confirmation ----------------------------------------------------------------

    @Then("the confirmation is titled {string}")
    fun confirmationTitled(title: String) = assertEquals(title, textOf(TITLE.find(confirmation())?.groupValues?.get(1)))

    @Then("the confirmation says {string}")
    fun confirmationSays(text: String) = assertEquals(text, textOf(BODY.find(confirmation())?.groupValues?.get(1)))

    @Then("the confirmation posts {string} for the {word} {string}")
    fun confirmationPosts(
        action: String,
        kind: String,
        name: String,
    ) {
        val form = requireNotNull(FORM.find(confirmation())) { "no form in ${confirmation()}" }.value
        assertTrue(form.contains("action=\"${withId(action, kind, name)}\"") && form.contains("method=\"post\""), form)
        assertTrue(form.contains("name=\"_csrf\""), "no CSRF token in $form")
    }

    @Then("the confirmation has a Cancel link to {string}")
    fun confirmationCancels(href: String) {
        val cancel = requireNotNull(Html.anchor(confirmation(), "Cancel")) { "no Cancel in ${confirmation()}" }
        assertTrue(cancel.contains("href=\"$href\""), cancel)
    }

    @Then("the page has no confirmation")
    fun noConfirmation() = assertFalse(World.response.body().contains("<dialog"), World.response.body())

    // ---- the add-source error ------------------------------------------------------------

    @Then("the add-source field shows the error {string}")
    fun addFieldShowsError(message: String) {
        val page = World.sourcesPage
        val error = requireNotNull(ADD_ERROR.find(page)) { "no add-source error in $page" }
        assertTrue(error.value.contains("role=\"alert\""), error.value)
        assertEquals(message, Html.unescape(error.groupValues[1]))
        val field = addField(page)
        assertTrue(field.contains("aria-invalid=\"true\""), field)
        assertTrue(field.contains("aria-describedby=\"add-url-error\""), field)
    }

    @Then("the add-source field keeps {string}")
    fun addFieldKeeps(url: String) = assertTrue(addField(World.sourcesPage).contains("value=\"${Html.escape(url)}\""))

    @Then("the sources page shows the error {string}")
    fun pageShowsError(message: String) {
        val box = requireNotNull(MESSAGE.find(World.sourcesPage)) { "no message in ${World.sourcesPage}" }.value
        assertTrue(box.contains("role=\"alert\"") && box.contains(">$message<"), box)
    }

    // ---- helpers -------------------------------------------------------------------------

    private fun fetch(path: String) {
        World.response = app.get(path)
    }

    private fun row(
        kind: String,
        name: String,
    ): String {
        val (tag, attribute) = if (kind == "group") "group" to "data-group" else "source" to "data-title"
        val page = World.response.body()
        val pattern = Regex("<li class=\"$tag\"[^>]*$attribute=\"${Regex.escape(Html.escape(name))}\".*?</li>", DOT_ALL)
        return requireNotNull(pattern.find(page)) { "no $kind row '$name'" }.value
    }

    private fun posts(
        kind: String,
        name: String,
        action: String,
    ) = Regex("<form[^>]*action=\"${Regex.escape(withId(action, kind, name))}\"").containsMatchIn(row(kind, name))

    private fun withId(
        path: String,
        kind: String,
        name: String,
    ) = path.replace("{id}", idOf(kind, name).toString())

    private fun idOf(
        kind: String,
        name: String,
    ): Long =
        when (kind) {
            "group" -> app.groupId(name)
            "user" -> app.userId(name)
            else -> app.sourceId(name)
        }

    private fun confirmation(): String =
        requireNotNull(DIALOG.find(World.response.body())) { "no confirmation in ${World.response.body()}" }.value

    private fun textOf(html: String?): String? {
        val text = html?.replace(TAG, "") ?: return null
        return Html.unescape(text).replace(SPACE, " ").trim()
    }

    private fun addField(page: String) = requireNotNull(ADD_FIELD.find(page)) { "no add-source field" }.value

    private companion object {
        val DOT_ALL = setOf(RegexOption.DOT_MATCHES_ALL)
        val DIALOG = Regex("<dialog class=\"confirm\".*?</dialog>", RegexOption.DOT_MATCHES_ALL)
        val TITLE = Regex("<h2 class=\"confirm-title\"[^>]*>(.*?)</h2>", RegexOption.DOT_MATCHES_ALL)
        val BODY = Regex("<p class=\"confirm-body\"[^>]*>(.*?)</p>", RegexOption.DOT_MATCHES_ALL)
        val FORM = Regex("<form[^>]*>.*?</form>", RegexOption.DOT_MATCHES_ALL)
        val ADD_ERROR = Regex("<p[^>]*id=\"add-url-error\"[^>]*>([^<]*)</p>")
        val ADD_FIELD = Regex("<input[^>]*id=\"add-url\"[^>]*>")
        val MESSAGE = Regex("<p[^>]*id=\"message\"[^>]*>[^<]*</p>")
        val TAG = Regex("<[^>]+>")
        val SPACE = Regex("\\s+")
    }
}
