package dev.starve.infra

import jakarta.servlet.http.Cookie
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.Base64

private const val THIRTY_DAYS = 30 * 24 * 60 * 60
private const val BLOCKING_FAILURES = 5
private const val LOGIN_PAGE = "/login"

class LoginFlowTest : SecurityTestBase() {
    private fun MvcResult.redirect() = response.redirectedUrl

    private fun whoami(session: MockHttpSession) =
        mvc
            .perform(get("/whoami").session(session))
            .andReturn()
            .response.contentAsString

    @Test
    fun `the right password signs in and goes to the timeline`() {
        withAccounts()
        val session = MockHttpSession()

        val result = login(ADMIN, session = session)

        assertEquals("/feed", result.redirect())
        mvc.perform(get("/whoami").session(session)).andExpect(status().isOk)
    }

    @Test
    fun `the username is matched ignoring case`() {
        withAccounts()

        assertEquals("/feed", login("ROB").redirect())
    }

    @Test
    fun `a wrong password goes back to the login page with the error flag`() {
        withAccounts()

        assertEquals("/login?error", login(ADMIN, WRONG_PASSWORD).redirect())
    }

    @Test
    fun `an unknown username fails the same way as a wrong password`() {
        withAccounts()

        assertEquals("/login?error", login("nobody").redirect())
    }

    @Test
    fun `the error flag shows the generic message`() {
        withAccounts()

        mvc
            .perform(get("/login").param("error", ""))
            .andExpect(content().string(containsString("Invalid username or password")))
            .andExpect(content().string(not(containsString("Too many failed attempts"))))
    }

    @Test
    fun `the blocked flag shows the too many attempts message`() {
        withAccounts()

        mvc
            .perform(get("/login").param("blocked", ""))
            .andExpect(content().string(containsString("Too many failed attempts. Try again later.")))
            .andExpect(content().string(not(containsString("Invalid username or password"))))
    }

    @Test
    fun `the login page shows no message when nothing went wrong`() {
        withAccounts()

        mvc.perform(get("/login")).andExpect(content().string(not(containsString("id=\"message\""))))
    }

    @Test
    fun `a signed in visitor to the login page is sent to the timeline`() {
        withAccounts()
        val session = MockHttpSession()
        login(ADMIN, session = session)

        mvc.perform(get("/login").session(session)).andExpect(redirectedUrl("/feed"))
    }

    @Test
    fun `an administrator holds the admin role`() {
        withAccounts()
        val session = MockHttpSession()
        login(ADMIN, session = session)

        assertEquals("$ADMIN:ROLE_ADMIN", whoami(session))
    }

    @Test
    fun `any other account holds the user role`() {
        withAccounts()
        val session = MockHttpSession()
        login(MEMBER, session = session)

        assertEquals("$MEMBER:ROLE_USER", whoami(session))
    }

    @Test
    fun `five failures block the account even with the right password`() {
        withAccounts()
        failLogin(ADMIN, BLOCKING_FAILURES)

        assertEquals("/login?blocked", login(ADMIN).redirect())
    }

    @Test
    fun `four failures do not block`() {
        withAccounts()
        failLogin(ADMIN, BLOCKING_FAILURES - 1)

        assertEquals("/feed", login(ADMIN).redirect())
    }

    @Test
    fun `a blocked attempt does not sign in`() {
        withAccounts()
        failLogin(ADMIN, BLOCKING_FAILURES)
        val session = MockHttpSession()

        login(ADMIN, session = session)

        mvc.perform(get("/whoami").session(session)).andExpect(redirectedUrl(LOGIN_PAGE))
    }

    @Test
    fun `a block on one username leaves another username at the same address free`() {
        withAccounts()
        failLogin(ADMIN, BLOCKING_FAILURES)

        assertEquals("/feed", login(MEMBER).redirect())
    }

    @Test
    fun `a block on one address leaves the same username at another address free`() {
        withAccounts()
        failLogin(ADMIN, BLOCKING_FAILURES, from = "203.0.113.7")

        assertEquals("/feed", login(ADMIN, from = "198.51.100.9").redirect())
    }

    @Test
    fun `failures count against a username whatever its case`() {
        withAccounts()
        failLogin("ROB", BLOCKING_FAILURES)

        assertEquals("/login?blocked", login("rob").redirect())
    }

    @Test
    fun `failures against an unknown username block it too`() {
        withAccounts()
        failLogin("nobody", BLOCKING_FAILURES)

        assertEquals("/login?blocked", login("nobody").redirect())
    }

    @Test
    fun `a successful login clears the failures counted so far`() {
        withAccounts()
        failLogin(ADMIN, BLOCKING_FAILURES - 1)
        login(ADMIN)

        failLogin(ADMIN, BLOCKING_FAILURES - 1)

        assertEquals("/feed", login(ADMIN).redirect())
    }

    @Test
    fun `ticking remember me sets a thirty day remember-me cookie`() {
        withAccounts()

        val cookie = loginRememberingMe(ADMIN).response.getCookie("remember-me")

        assertNotNull(cookie)
        assertEquals(THIRTY_DAYS, cookie!!.maxAge)
    }

    @Test
    fun `leaving remember me unticked sets no remember-me cookie`() {
        withAccounts()

        assertNull(login(ADMIN).response.getCookie("remember-me"))
    }

    @Test
    fun `the remember-me cookie alone authenticates a new session`() {
        withAccounts()
        val cookie = loginRememberingMe(ADMIN).response.getCookie("remember-me")!!

        val body =
            mvc
                .perform(get("/whoami").cookie(cookie))
                .andReturn()
                .response.contentAsString

        assertEquals("$ADMIN:ROLE_ADMIN", body)
    }

    @Test
    fun `a tampered remember-me cookie does not authenticate`() {
        withAccounts()
        val unsigned = Base64.getEncoder().encodeToString("$ADMIN:99999999999999:not-the-signature".toByteArray())
        val forged = Cookie("remember-me", unsigned)

        mvc.perform(get("/whoami").cookie(forged)).andExpect(redirectedUrl(LOGIN_PAGE))
    }

    @Test
    fun `logging out ends the session and clears both cookies`() {
        withAccounts()
        val session = MockHttpSession()
        loginRememberingMe(ADMIN, session)

        val result = mvc.perform(post("/logout").session(session)).andReturn()

        assertEquals("/", result.redirect())
        assertTrue(session.isInvalid)
        assertEquals(0, result.response.getCookie("JSESSIONID")?.maxAge)
        assertEquals(0, result.response.getCookie("remember-me")?.maxAge)
    }

    @Test
    fun `logging out needs the CSRF token`() {
        withAccounts()
        val session = MockHttpSession()
        login(ADMIN, session = session)

        mvc.perform(postWithoutCsrf("/logout").session(session)).andExpect(status().isForbidden)

        assertFalse(session.isInvalid)
    }
}
