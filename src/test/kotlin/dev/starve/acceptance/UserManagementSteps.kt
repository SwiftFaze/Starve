package dev.starve.acceptance

import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import java.time.LocalDate
import java.time.ZoneOffset

/** Steps for specs/features/user-management.feature: the Settings user list, password reset and deletion. */
class UserManagementSteps {
    private val app = World.app

    @Given("the user {string} was created on {string}")
    fun createdOn(
        username: String,
        date: String,
    ) {
        val millis =
            LocalDate
                .parse(date)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
        app
            .jdbc()
            .sql("UPDATE users SET created_at = ? WHERE username = ?")
            .params(millis, username)
            .update()
    }

    // ---- the list ----------------------------------------------------------------------------

    @Then("the Settings user list is")
    fun userListIs(table: DataTable) {
        val expected = table.asMaps().map { Triple(it.getValue("username"), it.getValue("admin"), it.getValue("you")) }
        val actual = rows().map { Triple(nameOf(it), yesNo("Admin" in textOf(it)), yesNo("(you)" in it)) }
        assertEquals(expected, actual)
    }

    @Then("the Settings user list shows {string} created on {string}")
    fun listShowsCreated(
        username: String,
        date: String,
    ) {
        val created = requireNotNull(Regex("<time[^>]*>([^<]*)</time>").find(rowOf(username))) { "no date" }
        assertEquals(date, created.groupValues[1])
    }

    @Then("the user row of {string} has a {string} form that posts {string} with the CSRF token")
    fun rowHasForm(
        username: String,
        label: String,
        action: String,
    ) {
        val form = requireNotNull(FORM.find(rowOf(username))) { "no form in the row of $username" }.value
        assertTrue(label in form, form)
        assertTrue(form.contains("action=\"${withId(action, username)}\"") && form.contains("method=\"post\""), form)
        assertTrue(form.contains("name=\"_csrf\""), "no CSRF token in $form")
    }

    @Then("the user row of {string} has a {string} link to {string}")
    fun rowHasLink(
        username: String,
        label: String,
        href: String,
    ) = assertTrue(hasLink(rowOf(username), label, withId(href, username)), rowOf(username))

    @Then("the user row of {string} has no {string} control")
    fun rowHasNoControl(
        username: String,
        label: String,
    ) = assertFalse(rowOf(username).contains(">$label<"), rowOf(username))

    // ---- reset -------------------------------------------------------------------------------

    @When("I reset the password of {string} in Settings")
    fun resetPassword(username: String) {
        World.response = submitRowForm(app.get("/settings").body(), username)
        TEMPORARY.find(World.response.body())?.let { World.temporaryPasswords += it.groupValues[1] }
    }

    @Then("the page shows a temporary password for {string}")
    fun pageShowsTemporary(username: String) {
        val response = World.response
        assertEquals(200, response.statusCode())
        assertTrue(
            response
                .headers()
                .firstValue("Cache-Control")
                .orElse("")
                .contains("no-store"),
        )
        val shown = requireNotNull(TEMPORARY.find(response.body())) { "no temporary password: ${response.body()}" }
        assertTrue(TEMPORARY_SHAPE.matches(shown.groupValues[1]))
        assertEquals(World.temporaryPasswords.last(), shown.groupValues[1])
        assertTrue(response.body().contains(">$username<"), "does not name $username")
        assertTrue(response.body().contains("shown once"))
        assertTrue(response.body().contains("href=\"/settings\""))
    }

    @Then("the temporary password is not shown again")
    fun notShownAgain() = assertFalse(World.response.body().contains(World.temporaryPasswords.last()))

    @Then("the page does not contain the temporary password")
    fun pageLacksTemporary() = notShownAgain()

    @Then("the temporary password differs from the previous one")
    fun differsFromPrevious() {
        val all = World.temporaryPasswords
        assertTrue(all.size >= 2, "fewer than two resets")
        assertNotEquals(all[all.size - 2], all.last())
    }

    // ---- delete ------------------------------------------------------------------------------

    @When("I delete the user {string} in Settings")
    fun deleteUser(username: String) {
        val confirmation = app.get("/settings/users/${app.userId(username)}/delete")
        assertEquals(200, confirmation.statusCode(), "no confirmation page")
        World.response = submitForm(confirmation.body())
    }

    /** The step has five quoted values, in order: user, button, form action, cancel label, cancel link. */
    @Then("^the confirmation page names (.*)$")
    fun confirmationPage(rest: String) {
        val quoted = Regex("\"([^\"]*)\"").findAll(rest).map { it.groupValues[1] }.toList()
        val (username, button, action) = quoted
        val (cancel, href) = quoted.drop(3)
        val body = World.response.body()
        assertTrue(body.contains(">$username<"), "does not name $username")
        val form = requireNotNull(FORM.find(body)) { "no form" }.value
        assertTrue(button in form && form.contains("action=\"${withId(action, username)}\""), form)
        assertTrue(form.contains("method=\"post\"") && form.contains("name=\"_csrf\""), form)
        assertTrue(hasLink(body, cancel, href), "no $cancel link")
    }

    // ---- helpers -----------------------------------------------------------------------------

    private fun rows(): List<String> = ROW.findAll(World.response.body()).map { it.value }.toList()

    private fun nameOf(row: String): String =
        Html.unescape(requireNotNull(Regex("<strong class=\"name\">([^<]*)</strong>").find(row)).groupValues[1])

    private fun textOf(row: String): String = row.replace(Regex("<[^>]*>"), " ")

    private fun rowOf(username: String): String = rowIn(World.response.body(), username)

    private fun rowIn(
        page: String,
        username: String,
    ): String =
        requireNotNull(ROW.findAll(page).map { it.value }.firstOrNull { nameOf(it) == username }) {
            "no row for $username"
        }

    private fun hasLink(
        html: String,
        label: String,
        href: String,
    ) = Html.anchor(html, label)?.contains("href=\"$href\"") == true

    private fun yesNo(value: Boolean) = if (value) "yes" else "no"

    private fun withId(
        path: String,
        username: String,
    ) = path.replace("{id}", app.userId(username).toString())

    private fun submitRowForm(
        page: String,
        username: String,
    ) = submitForm(rowIn(page, username))

    /** Posts the first form in [html] as a browser would, with its hidden fields. */
    private fun submitForm(html: String): java.net.http.HttpResponse<String> {
        val form = requireNotNull(FORM.find(html)) { "no form" }.value
        val action = requireNotNull(Regex("action=\"([^\"]*)\"").find(form)).groupValues[1]
        val fields = HIDDEN.findAll(form).map { it.groupValues[1] to Html.unescape(it.groupValues[2]) }.toList()
        return app.postForm(action, *fields.toTypedArray())
    }

    private companion object {
        val ROW = Regex("<li class=\"user\".*?</li>", RegexOption.DOT_MATCHES_ALL)
        val FORM = Regex("<form[^>]*action=\"/settings/users/[^\"]*\".*?</form>", RegexOption.DOT_MATCHES_ALL)
        val HIDDEN = Regex("<input[^>]*type=\"hidden\"[^>]*name=\"([^\"]*)\"[^>]*value=\"([^\"]*)\"")
        val TEMPORARY_SHAPE = Regex("[ABCDEFGHJKMNPQRSTUVWXYZ2-9]{4}(-[ABCDEFGHJKMNPQRSTUVWXYZ2-9]{4}){3}")
        val TEMPORARY = Regex("id=\"temporary-password\"[^>]*>([^<]+)<")
    }
}
