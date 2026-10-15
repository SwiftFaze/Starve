package dev.starve.acceptance

import io.cucumber.java.Before
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

/** Steps for specs/features/account-password.feature, and the second browser shared with user-management. */
class AccountPasswordSteps {
    private val app = World.app

    @Before
    fun forgetEarlierScenario() {
        World.secondBrowser = null
        World.temporaryPasswords.clear()
    }

    // ---- changing the password ------------------------------------------------------------

    @When("I change my password from {string} to {string}")
    fun changePassword(
        current: String,
        new: String,
    ) = submitChange(current, new)

    @When("I change my password from the temporary password to {string}")
    fun changeFromTemporary(new: String) = submitChange(World.temporaryPasswords.last(), new)

    @When("I submit the password change from {string} to {string} {int} times")
    fun submitRepeatedly(
        current: String,
        new: String,
        times: Int,
    ) = repeat(times) { submitChange(current, new) }

    @When("I post {string} with a CSRF token")
    fun postWithCsrf(path: String) {
        World.response = app.postForm(path)
    }

    @Then("the Account page shows the error {string}")
    fun accountShowsError(error: String) {
        val body = World.response.body()
        assertTrue(body.contains("action=\"/account/password\""), "not the Account form: $body")
        assertTrue(body.contains(Html.escape(error)), "no '$error' on the Account page: $body")
    }

    @Then(
        "the Account page has a form that posts {string} with the CSRF token asking for a current password " +
            "and a new password and no confirmation",
    )
    fun accountFormStructure(action: String) {
        val pattern = Regex("<form[^>]*action=\"$action\".*?</form>", RegexOption.DOT_MATCHES_ALL)
        val form =
            requireNotNull(pattern.find(World.response.body())) {
                "no form posting $action"
            }.value
        assertTrue(form.contains("method=\"post\""), form)
        assertTrue(form.contains("name=\"_csrf\""), "no CSRF token in $form")
        val passwordFields =
            Regex("<input[^>]*type=\"password\"[^>]*name=\"([^\"]*)\"").findAll(form).map { it.groupValues[1] }
        assertEquals(listOf("currentPassword", "newPassword"), passwordFields.toList())
    }

    // ---- the second browser ---------------------------------------------------------------

    @Given("{string} has logged in with Remember me in a second browser")
    fun secondBrowserRemembers(username: String) {
        World.secondBrowser = app.rememberedBrowser(username)
    }

    @Then("the second browser requesting {string} now returns status {int}")
    fun secondBrowserReturns(
        path: String,
        status: Int,
    ) = assertEquals(status, secondBrowserGet(path).statusCode())

    @Then("the second browser requesting {string} redirects to {string}")
    fun secondBrowserRedirects(
        path: String,
        target: String,
    ) = assertRedirectTo(secondBrowserGet(path), target)

    /** Each request is a reopened browser: only the persistent remember-me cookie survives, never a session. */
    private fun secondBrowserGet(path: String) =
        requireNotNull(World.secondBrowser) { "no second browser" }.let {
            it.endSession()
            it.get(path).also { response -> World.response = response }
        }

    private fun submitChange(
        current: String,
        new: String,
    ) {
        World.response = app.postForm("/account/password", "currentPassword" to current, "newPassword" to new)
    }
}
