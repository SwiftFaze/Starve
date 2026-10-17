package dev.starve.acceptance

import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

/** Steps for specs/features/confirm-destructive-actions.feature: the confirming rows and the add-source error. */
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

    @Then("the row of the {word} {string} has a form that posts {string}")
    fun rowHasForm(
        kind: String,
        name: String,
        action: String,
    ) = assertTrue(posts(kind, name, action), row(kind, name))

    @Then("the row of the {word} {string} has no form that posts {string}")
    fun rowHasNoForm(
        kind: String,
        name: String,
        action: String,
    ) = assertFalse(posts(kind, name, action), row(kind, name))

    @Then("the row of the {word} {string} asks {string}")
    fun rowAsks(
        kind: String,
        name: String,
        question: String,
    ) {
        val ask = requireNotNull(ASK.find(row(kind, name))) { "no question in ${row(kind, name)}" }.groupValues[1]
        assertEquals(question, Html.unescape(ask.replace(TAG, "")).replace(SPACE, " ").trim())
    }

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
    ) = path.replace("{id}", (if (kind == "group") app.groupId(name) else app.sourceId(name)).toString())

    private fun addField(page: String) = requireNotNull(ADD_FIELD.find(page)) { "no add-source field" }.value

    private companion object {
        val DOT_ALL = setOf(RegexOption.DOT_MATCHES_ALL)
        val ASK = Regex("<p class=\"row-confirm-ask\"[^>]*>(.*?)</p>", RegexOption.DOT_MATCHES_ALL)
        val ADD_ERROR = Regex("<p[^>]*id=\"add-url-error\"[^>]*>([^<]*)</p>")
        val ADD_FIELD = Regex("<input[^>]*id=\"add-url\"[^>]*>")
        val MESSAGE = Regex("<p[^>]*id=\"message\"[^>]*>[^<]*</p>")
        val TAG = Regex("<[^>]+>")
        val SPACE = Regex("\\s+")
    }
}
