package dev.starve.acceptance

import dev.starve.application.SignupSettings
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue

/** Steps for specs/features/self-signup.feature: the signup switch in Settings, the signup page and its throttle. */
class SignupSteps {
    private val app = World.app

    // ---- the switch ----------------------------------------------------------------------

    @Given("signups are {word}")
    fun signupsAre(state: String) = app.bean(SignupSettings::class.java).set(onOff(state))

    @Then("signups are still {word}")
    fun signupsAreStill(state: String) = assertEquals(onOff(state), app.bean(SignupSettings::class.java).enabled())

    @Then("the Settings page shows the {string} switch {word}")
    fun settingsShowsSwitch(
        label: String,
        state: String,
    ) {
        val page = app.get("/settings")
        assertEquals(200, page.statusCode(), "settings page: ${page.statusCode()}")
        assertTrue(page.body().contains(label), "no '$label' on the Settings page")
        assertEquals(onOff(state), switchIsOn(page.body()), "the switch is not $state")
    }

    @Then("the {string} form posts {string} with the CSRF token")
    fun formPosts(
        label: String,
        action: String,
    ) {
        val form = settingsForm(World.response.body())
        assertTrue(label in form, "no '$label' in $form")
        assertTrue(form.contains("action=\"$action\"") && form.contains("method=\"post\""), form)
        assertTrue(form.contains("name=\"_csrf\""), "no CSRF token in $form")
    }

    /** Drives the real Settings form: loads the page, checks the switch is not yet there, posts the form as found. */
    @When("I turn signups {word} in Settings")
    fun turnSignups(state: String) {
        val page = app.get("/settings").body()
        assertNotEquals(onOff(state), switchIsOn(page), "signups are already $state")
        val form = settingsForm(page)
        val fields = HIDDEN.findAll(form).map { it.groupValues[1] to Html.unescape(it.groupValues[2]) }.toList()
        World.response = app.postForm("/settings/signups", *fields.toTypedArray())
    }

    @Then("posting {string} with the CSRF token also returns status {int}")
    fun postingReturns(
        path: String,
        status: Int,
    ) {
        World.response = app.postForm(path, "allow" to "true")
        assertEquals(status, World.response.statusCode())
    }

    @Then("posting {string} with the CSRF token also redirects to {string}")
    fun postingRedirects(
        path: String,
        target: String,
    ) {
        World.response = app.postForm(path, "allow" to "true")
        assertRedirectTo(World.response, target)
    }

    @Then("requesting {string} returns status {int}")
    fun requestingReturns(
        path: String,
        status: Int,
    ) {
        World.response = app.get(path)
        assertEquals(status, World.response.statusCode())
    }

    // ---- the top bar ---------------------------------------------------------------------

    @Then("the top bar has a {string} link to {string}")
    fun barHasLink(
        label: String,
        href: String,
    ) = assertTrue(Html.anchor(bar(), label)?.contains("href=\"$href\"") == true, "no '$label' link in the top bar")

    @Then("the top bar has no {string} link")
    fun barHasNoLink(label: String) = assertEquals(null, Html.anchor(bar(), label), "the top bar has a '$label' link")

    @Then("the response contains the top bar with the {string} link")
    fun responseBarHasLink(label: String) = assertTrue(Html.anchor(bar(), label) != null, "no '$label' link in the bar")

    // ---- signing up ----------------------------------------------------------------------

    @When("I sign up as {string} with password {string}")
    fun signUp(
        username: String,
        password: String,
    ) = signUpFrom(app.browser, username, password)

    @When("the client {string} signs up as {string} with password {string}")
    fun clientSignsUp(
        address: String,
        username: String,
        password: String,
    ) = signUpFrom(app.browserAt(address), username, password)

    @When("I submit signup as {string} with password {string} {int} times")
    fun submitRepeatedly(
        username: String,
        password: String,
        times: Int,
    ) = repeat(times) { signUpFrom(app.browser, username, password) }

    @When("the client {string} submits signup as {string} with password {string} {int} times")
    fun clientSubmitsRepeatedly(
        address: String,
        username: String,
        password: String,
        times: Int,
    ) = repeat(times) { signUpFrom(app.browserAt(address), username, password) }

    @Then("the signup form asks for a username and a password and posts {string} with the CSRF token")
    fun signupFormFields(action: String) {
        val form = signupForm()
        assertTrue(form.contains("action=\"$action\"") && form.contains("method=\"post\""), form)
        assertTrue(form.contains("name=\"username\""), "no username field in $form")
        assertTrue(Regex("<input[^>]*type=\"password\"[^>]*name=\"password\"").containsMatchIn(form), form)
        assertTrue(form.contains("name=\"_csrf\""), "no CSRF token in $form")
    }

    @Then("the signup form has no password confirmation field")
    fun noConfirmation() = assertEquals(1, Regex("type=\"password\"").findAll(signupForm()).count())

    @Then("the page has no signup form")
    fun noSignupForm() = assertFalse(World.response.body().contains("action=\"/signup\""), World.response.body())

    @Then("the signup form is shown again with the error {string}")
    fun signupShowsError(error: String) {
        val body = World.response.body()
        assertTrue(body.contains("action=\"/signup\""), "not the signup form: $body")
        assertTrue(body.contains(Html.escape(error)), "no '$error' on the signup form: $body")
    }

    // ---- accounts made by signup ---------------------------------------------------------

    @Then("the user {string} exists and is not an admin")
    fun userIsNotAdmin(username: String) = assertEquals(0, adminFlag(username))

    @Then("the user {string} does not exist")
    fun userDoesNotExist(username: String) =
        assertEquals(
            0L,
            app
                .jdbc()
                .sql("SELECT COUNT(*) FROM users WHERE username = ?")
                .param(username)
                .query(Long::class.java)
                .single(),
        )

    // ---- helpers -------------------------------------------------------------------------

    private fun signUpFrom(
        from: ApplicationHarness.Browser,
        username: String,
        password: String,
    ) {
        val fields = listOf("username" to username, "password" to password)
        World.response = from.postForm("/signup", emptyMap(), fields)
    }

    private fun adminFlag(username: String): Int =
        app
            .jdbc()
            .sql("SELECT admin FROM users WHERE username = ?")
            .param(username)
            .query(Int::class.java)
            .optional()
            .orElseThrow { AssertionError("no user $username") }

    private fun onOff(state: String): Boolean {
        require(state == "on" || state == "off") { "signups are on or off, not $state" }
        return state == "on"
    }

    private fun switchIsOn(page: String): Boolean =
        requireNotNull(SWITCH.find(settingsForm(page))) { "no switch on the Settings page" }.groupValues[1] == "true"

    private fun settingsForm(page: String): String = requireNotNull(SETTINGS_FORM.find(page)) { "no form" }.value

    private fun signupForm(): String = requireNotNull(SIGNUP_FORM.find(World.response.body())) { "no form" }.value

    private fun bar(): String =
        World.response
            .body()
            .substringAfter("<header")
            .substringBefore("</header>")

    private companion object {
        val SETTINGS_FORM = Regex("<form[^>]*action=\"/settings/signups\".*?</form>", RegexOption.DOT_MATCHES_ALL)
        val SIGNUP_FORM = Regex("<form[^>]*action=\"/signup\".*?</form>", RegexOption.DOT_MATCHES_ALL)
        val SWITCH = Regex("aria-checked=\"(true|false)\"")
        val HIDDEN = Regex("<input[^>]*type=\"hidden\"[^>]*name=\"([^\"]*)\"[^>]*value=\"([^\"]*)\"")
    }
}
