package dev.starve.infra

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

private const val LOGIN_URL = "http://localhost/login"

class DeletedUserSessionTest : SecurityTestBase() {
    private fun signedIn(username: String) = MockHttpSession().also { login(username, session = it) }

    @ParameterizedTest
    @ValueSource(strings = ["/whoami", "/account", "/feed", "/sources", "/settings/users/1/delete"])
    fun `a live session of a deleted user is sent to login on any authenticated page`(path: String) {
        withAccounts()
        val session = signedIn(MEMBER)
        users.remove(MEMBER)

        mvc.perform(get(path).session(session)).andExpect(status().isFound).andExpect(redirectedUrl(LOGIN_URL))
    }

    @Test
    fun `a deleted admin's session is sent to login too`() {
        withAccounts()
        val session = signedIn(ADMIN)
        users.remove(ADMIN)

        mvc.perform(get("/settings").session(session)).andExpect(status().isFound).andExpect(redirectedUrl(LOGIN_URL))
    }

    @Test
    fun `an htmx request of a deleted user is told to load the login page`() {
        withAccounts()
        val session = signedIn(MEMBER)
        users.remove(MEMBER)

        mvc
            .perform(get("/whoami").session(session).header("HX-Request", "true"))
            .andExpect(status().isUnauthorized)
            .andExpect(header().string("HX-Redirect", "/login"))
    }

    @Test
    fun `the deleted user's session is dropped and the login page is shown instead of looping to the timeline`() {
        withAccounts()
        val session = signedIn(MEMBER)
        users.remove(MEMBER)

        mvc.perform(get("/login").session(session)).andExpect(status().isOk)

        assertTrue(session.isInvalid)
    }

    @Test
    fun `a deleted user's remember-me cookie no longer signs in`() {
        withAccounts()
        val cookie = loginRememberingMe(MEMBER).response.getCookie("remember-me")!!
        users.remove(MEMBER)

        mvc.perform(get("/whoami").cookie(cookie)).andExpect(redirectedUrl(LOGIN_URL))
    }

    @Test
    fun `a user who still exists keeps their session`() {
        withAccounts()
        val session = signedIn(MEMBER)
        users.remove(ADMIN)

        mvc.perform(get("/whoami").session(session)).andExpect(status().isOk)
        assertEquals(false, session.isInvalid)
    }
}
