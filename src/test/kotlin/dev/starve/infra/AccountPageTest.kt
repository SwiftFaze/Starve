package dev.starve.infra

import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

private const val NEW_PASSWORD = "battery staple two"
private const val LOGIN_URL = "/login"
private const val BLOCKING_FAILURES = 5

class AccountPageTest : SecurityTestBase() {
    private fun signedIn(username: String) = MockHttpSession().also { login(username, session = it) }

    private fun changeForm(
        session: MockHttpSession,
        current: String = PASSWORD,
        new: String = NEW_PASSWORD,
        from: String = "203.0.113.7",
    ): MockHttpServletRequestBuilder =
        post("/account/password")
            .session(session)
            .with(clientAt(from))
            .param("currentPassword", current)
            .param("newPassword", new)

    private fun String.matchesStored(username: String) = BCryptPasswordEncoder().matches(this, users.hashOf(username))

    @Test
    fun `an anonymous visitor to the account page is sent to login`() {
        withAccounts()

        mvc.perform(get("/account")).andExpect(status().isFound).andExpect(redirectedUrl(LOGIN_URL))
    }

    @Test
    fun `an anonymous post of the form is sent to login and changes nothing`() {
        withAccounts()
        val before = users.hashOf(MEMBER)

        mvc
            .perform(post("/account/password").param("currentPassword", PASSWORD).param("newPassword", NEW_PASSWORD))
            .andExpect(status().isFound)
            .andExpect(redirectedUrl(LOGIN_URL))

        assertEquals(before, users.hashOf(MEMBER))
    }

    @ParameterizedTest
    @ValueSource(strings = [ADMIN, MEMBER])
    fun `every signed-in user sees the form, their name and the Account and sign-out controls`(username: String) {
        withAccounts()

        mvc
            .perform(get("/account").session(signedIn(username)))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("action=\"/account/password\"")))
            .andExpect(content().string(containsString("name=\"currentPassword\"")))
            .andExpect(content().string(containsString("name=\"newPassword\"")))
            .andExpect(content().string(containsString("name=\"_csrf\"")))
            .andExpect(content().string(containsString(">$username<")))
            .andExpect(content().string(containsString("href=\"/account\"")))
            .andExpect(content().string(containsString("action=\"/logout\"")))
            .andExpect(content().string(not(containsString("Password changed"))))
    }

    @Test
    fun `the form has exactly two password fields`() {
        withAccounts()

        val page =
            mvc
                .perform(get("/account").session(signedIn(MEMBER)))
                .andReturn()
                .response.contentAsString

        assertEquals(2, Regex("type=\"password\"").findAll(page).count())
    }

    @Test
    fun `the Account link is in the bar for a regular user, who has no Settings link`() {
        withAccounts()

        mvc
            .perform(get("/account").session(signedIn(MEMBER)))
            .andExpect(content().string(not(containsString("href=\"/settings\""))))
    }

    @Test
    fun `the changed flag shows the confirmation`() {
        withAccounts()

        mvc
            .perform(get("/account").param("changed", "").session(signedIn(MEMBER)))
            .andExpect(content().string(containsString("Password changed")))
    }

    @Test
    fun `the right current password changes the password, keeps the session and redirects`() {
        withAccounts()
        val session = signedIn(MEMBER)

        mvc.perform(changeForm(session)).andExpect(status().isFound).andExpect(redirectedUrl("/account?changed"))

        assertTrue(NEW_PASSWORD.matchesStored(MEMBER))
        mvc.perform(get("/whoami").session(session)).andExpect(status().isOk)
    }

    @Test
    fun `the new password logs in and the old one does not`() {
        withAccounts()
        mvc.perform(changeForm(signedIn(ADMIN)))

        assertEquals("/login?error", login(ADMIN, PASSWORD).response.redirectedUrl)
        assertEquals("/feed", login(ADMIN, NEW_PASSWORD).response.redirectedUrl)
    }

    @Test
    fun `a wrong current password shows its error with status 200 and changes nothing`() {
        withAccounts()
        val before = users.hashOf(MEMBER)

        mvc
            .perform(changeForm(signedIn(MEMBER), current = WRONG_PASSWORD))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Current password is incorrect")))
            .andExpect(content().string(containsString("action=\"/account/password\"")))

        assertEquals(before, users.hashOf(MEMBER))
    }

    @ParameterizedTest
    @ValueSource(strings = ["short-1", "long"])
    fun `a new password that breaks the rules is refused with the rule's message`(kind: String) {
        withAccounts()
        val before = users.hashOf(MEMBER)

        val new = if (kind == "long") "a".repeat(73) else kind
        val message = if (new.length < 10) "Password must be at least 10 characters" else "Password is too long"
        mvc
            .perform(changeForm(signedIn(MEMBER), new = new))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString(message)))

        assertEquals(before, users.hashOf(MEMBER))
    }

    @Test
    fun `five wrong current passwords block even the right one with the throttle message`() {
        withAccounts()
        val session = signedIn(MEMBER)
        repeat(BLOCKING_FAILURES) { mvc.perform(changeForm(session, current = WRONG_PASSWORD)) }

        mvc
            .perform(changeForm(session))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Too many failed attempts. Try again later.")))

        assertTrue(PASSWORD.matchesStored(MEMBER))
    }

    @Test
    fun `the block is per client address and apart from the login throttle`() {
        withAccounts()
        val session = signedIn(MEMBER)
        repeat(BLOCKING_FAILURES) { mvc.perform(changeForm(session, current = WRONG_PASSWORD)) }

        mvc.perform(changeForm(session, from = "198.51.100.9")).andExpect(redirectedUrl("/account?changed"))
        assertEquals("/feed", login(MEMBER, NEW_PASSWORD).response.redirectedUrl)
    }

    @Test
    fun `failed logins do not block a password change`() {
        withAccounts()
        val session = signedIn(MEMBER)
        failLogin(MEMBER, BLOCKING_FAILURES)

        mvc.perform(changeForm(session)).andExpect(redirectedUrl("/account?changed"))
    }

    @Test
    fun `the change needs the CSRF token`() {
        withAccounts()

        mvc
            .perform(
                postWithoutCsrf("/account/password")
                    .session(signedIn(MEMBER))
                    .param("currentPassword", PASSWORD)
                    .param("newPassword", NEW_PASSWORD),
            ).andExpect(status().isForbidden)

        assertTrue(PASSWORD.matchesStored(MEMBER))
    }

    @Test
    fun `changing the password ends the remember-me cookie of that user and keeps the changer's session`() {
        withAccounts()
        val session = MockHttpSession()
        val cookie = loginRememberingMe(MEMBER, session).response.getCookie("remember-me")!!

        mvc.perform(changeForm(session)).andExpect(redirectedUrl("/account?changed"))

        mvc.perform(get("/whoami").session(session)).andExpect(status().isOk)
        mvc
            .perform(get("/whoami").cookie(cookie))
            .andExpect(status().isFound)
            .andExpect(redirectedUrl(LOGIN_URL))
        assertNotEquals(PASSWORD, users.hashOf(MEMBER))
    }
}
