package dev.starve.acceptance

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import java.net.URI
import java.net.http.HttpResponse
import java.time.Duration

/** Asserts that [response] redirects, and to which path. */
fun assertRedirectTo(
    response: HttpResponse<String>,
    path: String,
) {
    assertTrue(response.statusCode() in 300..399, "expected a redirect, got ${response.statusCode()}")
    val location = response.headers().firstValue("Location").orElseThrow()
    val target = URI.create(location)
    val actual = if (path.contains('?')) target.rawPath + "?" + target.rawQuery else target.path
    assertEquals(path, actual)
}

/** Steps for specs/features/accounts-login.feature: setup, login, throttling, remember-me and logout. */
class AccountSteps {
    private val app = World.app
    private var remembered: String? = null
    private var lastBrowser: ApplicationHarness.Browser? = null

    /** The browser that most recently logged in or out, which is the one the following steps continue with. */
    private fun browser(): ApplicationHarness.Browser = lastBrowser ?: app.browser

    // ---- Arrangement ------------------------------------------------------------------------

    /**
     * A running application over an empty database. A Background that already started the app gets a fresh
     * database first.
     */
    @Given("the application is running with no users yet")
    fun runningWithNoUsers() {
        if (app.isRunning()) app.freshDatabase()
        app.start()
    }

    @Given("the application is running with the user {string} with password {string}")
    fun runningWithUser(
        username: String,
        password: String,
    ) {
        app.start()
        app.completeSetup(username, password)
    }

    @Given("I am not signed in")
    fun notSignedIn() {
        app.browser.clearCookies()
        app.forgetSignIn()
    }

    @Given("I remember the logged setup code")
    fun rememberSetupCode() {
        remembered = requireNotNull(app.loggedSetupCode()) { "no setup code logged" }
    }

    @Given("I have requested {string}")
    fun haveRequested(path: String) {
        World.response = app.get(path)
    }

    // ---- First-run setup ----------------------------------------------------------------------

    @Then("the application log contains a setup code matching {string}")
    fun logContainsCode(pattern: String) {
        val line = Regex("Starve setup code: ($pattern)")
        assertTrue(app.loggedLines().any { line.containsMatchIn(it) }, "no setup code logged: ${app.loggedLines()}")
    }

    @Then("the application log of this run contains no setup code")
    fun noCodeLogged() {
        assertTrue(app.loggedLines().none { it.contains("setup code") }, "logged: ${app.loggedLines()}")
    }

    @Then("the application log contains a setup code different from the remembered one")
    fun newCodeLogged() {
        assertNotEquals(remembered, requireNotNull(app.loggedSetupCode()) { "no setup code logged" })
    }

    @When("I complete setup with the logged code as {string} with password {string}")
    fun completeWithLoggedCode(
        username: String,
        password: String,
    ) = submitSetup(loggedCode(), username, password)

    @When("I submit setup with the logged code as {string} with password {string}")
    fun submitWithLoggedCode(
        username: String,
        password: String,
    ) = submitSetup(loggedCode(), username, password)

    @When("I complete setup with the logged code in lower case without its hyphen as {string} with password {string}")
    fun completeWithLowerCaseCode(
        username: String,
        password: String,
    ) = submitSetup(loggedCode().lowercase().replace("-", ""), username, password)

    @When("I submit setup with the remembered code as {string} with password {string}")
    fun submitRememberedCode(
        username: String,
        password: String,
    ) = submitSetup(requireNotNull(remembered) { "no code remembered" }, username, password)

    @When("I submit setup with the code {string} as {string} with password {string}")
    fun submitCode(
        code: String,
        username: String,
        password: String,
    ) = submitSetup(code, username, password)

    @When("I submit setup with the code {string} {int} times")
    fun submitCodeRepeatedly(
        code: String,
        times: Int,
    ) = repeat(times) { submitSetup(code, "rob", "correct-horse-1") }

    @Then("submitting setup with any code returns status {int}")
    fun anyCodeStatus(status: Int) {
        submitSetup("AAAA-AAAA", "rob", "correct-horse-1")
        assertEquals(status, World.response.statusCode())
    }

    @Then("the setup form is shown again with the error {string}")
    fun setupShowsError(error: String) {
        val body = World.response.body()
        assertTrue(body.contains("name=\"code\""), "not the setup form: $body")
        assertTrue(body.contains(Html.escape(error)), "no '$error' on the setup form: $body")
    }

    @Then("no user exists")
    fun noUserExists() = assertEquals(emptyList<String>(), usernames())

    @Then("only the user {string} exists")
    fun onlyUserExists(username: String) = assertEquals(listOf(username), usernames())

    @Then("the user {string} exists and is an admin")
    fun userIsAdmin(username: String) {
        assertTrue(usernames().contains(username), "no user $username in ${usernames()}")
        val admin =
            app
                .jdbc()
                .sql("SELECT admin FROM users WHERE username = ?")
                .param(username)
                .query(Int::class.java)
                .single()
        assertEquals(1, admin)
    }

    @Then("the stored password of {string} is a BCrypt hash, not the temporary password")
    fun storedPasswordIsHashedNotTemporary(username: String) {
        storedPasswordIsHashed(username, World.temporaryPasswords.last())
    }

    @Then("the stored password of {string} is a BCrypt hash, not {string}")
    fun storedPasswordIsHashed(
        username: String,
        password: String,
    ) {
        val hash =
            app
                .jdbc()
                .sql("SELECT password_hash FROM users WHERE username = ?")
                .param(username)
                .query(String::class.java)
                .single()
        assertTrue(hash.startsWith("\$2"), "not a BCrypt hash: $hash")
        assertNotEquals(password, hash)
    }

    // ---- Requests and redirects -----------------------------------------------------------------

    @Then("requesting {string} redirects to {string}")
    fun requestingRedirects(
        requested: String,
        path: String,
    ) = redirectedTo(requested, path)

    @Then("requesting {string} also redirects to {string}")
    fun requestingAlsoRedirects(
        requested: String,
        path: String,
    ) = redirectedTo(requested, path)

    @Then("requesting {string} still redirects to {string}")
    fun requestingStillRedirects(
        requested: String,
        path: String,
    ) = redirectedTo(requested, path)

    @Then("requesting {string} now returns status {int}")
    fun requestingReturns(
        path: String,
        status: Int,
    ) {
        World.response = app.get(path)
        assertEquals(status, World.response.statusCode())
    }

    @Then("requesting the stylesheet returns status {int}")
    fun stylesheetReturns(status: Int) = requestingReturns("/starve.css", status)

    @Then("that client can request {string} with status {int}")
    fun clientCanRequest(
        path: String,
        status: Int,
    ) {
        World.response = browser().get(path)
        assertEquals(status, World.response.statusCode())
    }

    @Then("the response tells htmx to load {string} as a full page")
    fun htmxReloadsPage(path: String) {
        assertEquals(
            path,
            World.response
                .headers()
                .firstValue("HX-Redirect")
                .orElse(null),
        )
    }

    @Then("the response does not contain the login form")
    fun noLoginForm() = assertFalse(World.response.body().contains("name=\"password\""), World.response.body())

    @Then("the page carries the theme switch")
    fun carriesThemeSwitch() = assertTrue(World.response.body().contains("theme-switch"), World.response.body())

    // ---- Login -----------------------------------------------------------------------------------

    @When("I log in as {string} with password {string}")
    fun logIn(
        username: String,
        password: String,
    ) = logInWith(app.browser, username, password, rememberMe = false)

    @When("I log in as {string} with password {string} ticking {string}")
    fun logInTicking(
        username: String,
        password: String,
        label: String,
    ) {
        assertEquals("Remember me", label)
        logInWith(app.browser, username, password, rememberMe = true)
    }

    @When("I log in as {string} with the temporary password")
    fun logInWithTemporaryPassword(username: String) {
        logInWith(app.browser, username, World.temporaryPasswords.last(), rememberMe = false)
    }

    @When("I log in as {string} with password {string} {int} times")
    fun logInRepeatedly(
        username: String,
        password: String,
        times: Int,
    ) = repeat(times) { logInWith(app.browser, username, password, rememberMe = false) }

    @When("the client {string} logs in as {string} with password {string}")
    fun clientLogsIn(
        address: String,
        username: String,
        password: String,
    ) = logInWith(app.browserAt(address), username, password, rememberMe = false)

    @When("the client {string} logs in as {string} with password {string} {int} times")
    fun clientLogsInRepeatedly(
        address: String,
        username: String,
        password: String,
        times: Int,
    ) = repeat(times) { logInWith(app.browserAt(address), username, password, rememberMe = false) }

    @When("I log out")
    fun logOut() {
        World.response = browser().postForm("/logout", emptyMap(), emptyList())
        app.forgetSignIn()
    }

    @When("I post the {string} form without its CSRF token")
    fun postWithoutCsrf(label: String) {
        assertEquals("Mark all read", label)
        World.response = app.postFormWithoutCsrfToken("/read-all", "before" to "0")
    }

    @When("15 minutes pass")
    fun fifteenMinutesPass() = app.clock.advance(Duration.ofMinutes(15))

    @When("the browser session ends")
    fun browserSessionEnds() = browser().endSession()

    @Then("the login form is shown again with the error {string}")
    fun loginShowsError(error: String) {
        val body = followedPage()
        assertTrue(body.contains("name=\"password\""), "not the login form: $body")
        assertTrue(body.contains(Html.escape(error)), "no '$error' on the login form: $body")
    }

    @Then("no persistent login cookie is set")
    fun noPersistentCookie() = assertFalse(setCookies().any { it.startsWith("remember-me=") })

    @Then("a persistent login cookie is set that expires in 30 days")
    fun persistentCookieFor30Days() {
        val cookie = setCookies().firstOrNull { it.startsWith("remember-me=") }
        assertTrue(cookie?.contains("Max-Age=2592000") == true, "${setCookies()}")
    }

    @Then("the persistent login cookie is deleted")
    fun persistentCookieDeleted() {
        assertTrue(setCookies().any { it.startsWith("remember-me=") && it.contains("Max-Age=0") }, "${setCookies()}")
    }

    // ---- helpers ----------------------------------------------------------------------------------

    private fun loggedCode(): String = requireNotNull(app.loggedSetupCode()) { "no setup code logged" }

    private fun submitSetup(
        code: String,
        username: String,
        password: String,
    ) {
        World.response = app.postSetup(code, username, password)
    }

    private fun logInWith(
        from: ApplicationHarness.Browser,
        username: String,
        password: String,
        rememberMe: Boolean,
    ) {
        lastBrowser = from
        // An explicit login is not the setup sign-in the harness restores after a restart.
        if (from === app.browser) app.forgetSignIn()
        val fields =
            buildList<Pair<String, String>> {
                add("username" to username)
                add("password" to password)
                if (rememberMe) add("remember-me" to "on")
            }
        World.response = from.postForm("/login", emptyMap(), fields)
    }

    private fun redirectedTo(
        requested: String,
        path: String,
    ) {
        World.response = app.get(requested)
        assertRedirectTo(World.response, path)
    }

    /** The page a login or logout redirect leads to, fetched the way a browser would follow it. */
    private fun followedPage(): String {
        val response = World.response
        if (response.statusCode() in 300..399) {
            val target = URI.create(response.headers().firstValue("Location").orElseThrow())
            World.response = browser().get(target.rawPath + target.rawQuery?.let { "?$it" }.orEmpty())
        }
        return World.response.body()
    }

    private fun setCookies(): List<String> = World.response.headers().allValues("Set-Cookie")

    private fun usernames(): List<String> =
        app
            .jdbc()
            .sql("SELECT username FROM users ORDER BY id")
            .query(String::class.java)
            .list()
}
