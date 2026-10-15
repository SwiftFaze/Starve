package dev.starve.infra

import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

private const val NEW_NAME = "friend"
private const val NEW_PASSWORD = "correct-horse-2"
private const val FAILURES_TO_BLOCK = 5

class SignupFlowTest : SecurityTestBase() {
    private fun signup(
        username: String = NEW_NAME,
        password: String = NEW_PASSWORD,
        session: MockHttpSession = MockHttpSession(),
        from: String = "203.0.113.7",
    ): MvcResult =
        mvc
            .perform(
                post("/signup")
                    .session(session)
                    .with(clientAt(from))
                    .param("username", username)
                    .param("password", password),
            ).andReturn()

    private fun MvcResult.page() = response.contentAsString

    private fun open() {
        withAccounts()
        signups.set(true)
    }

    @Test
    fun `while signups are on the page shows a form with a username and a password only`() {
        open()

        mvc
            .perform(get("/signup"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("action=\"/signup\"")))
            .andExpect(content().string(containsString("name=\"username\"")))
            .andExpect(content().string(containsString("name=\"password\"")))
            .andExpect(content().string(containsString("name=\"_csrf\"")))
            .andExpect(content().string(containsString("href=\"/login\"")))
            .andExpect(content().string(not(containsString("Signups are closed"))))
    }

    @Test
    fun `while signups are off the page says so, without a form`() {
        withAccounts()

        mvc
            .perform(get("/signup"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Signups are closed")))
            .andExpect(content().string(not(containsString("action=\"/signup\""))))
            .andExpect(content().string(containsString("href=\"/login\"")))
    }

    @Test
    fun `a signup creates a regular user and signs them in with a fresh session`() {
        open()
        val session = MockHttpSession()
        val before = session.id

        val result = signup(session = session)

        assertEquals("/feed", result.response.redirectedUrl)
        assertNotEquals(before, session.id)
        assertFalse(users.findByUsername(NEW_NAME)!!.admin)
        val whoami =
            mvc
                .perform(get("/whoami").session(session))
                .andReturn()
                .response.contentAsString
        assertEquals("$NEW_NAME:ROLE_USER", whoami)
    }

    @Test
    fun `a signup stores a hash, not the password`() {
        open()
        signup()

        val stored = users.findByUsername(NEW_NAME)!!.passwordHash

        assertNotEquals(NEW_PASSWORD, stored)
        assertTrue(stored.startsWith("\$2"))
    }

    @Test
    fun `the new user can log in again later`() {
        open()
        signup()

        assertEquals("/feed", login(NEW_NAME, NEW_PASSWORD).response.redirectedUrl)
    }

    @Test
    fun `a signup while signups are off is refused and creates nothing`() {
        withAccounts()

        val result = signup()

        assertEquals(403, result.response.status)
        assertNull(users.findByUsername(NEW_NAME))
    }

    @Test
    fun `closing signups takes effect on the next request`() {
        open()
        signup(username = "first")
        signups.set(false)

        assertEquals(403, signup(username = "second").response.status)
        assertNull(users.findByUsername("second"))
        assertNotNull(users.findByUsername("first"))
    }

    @Test
    fun `a rule-breaking username shows its message and creates nothing`() {
        open()

        val result = signup(username = "x")

        assertEquals(200, result.response.status)
        assertTrue(result.page().contains("Username must be 3-32"))
        assertNull(users.findByUsername("x"))
    }

    @Test
    fun `a short password shows its message and keeps the form`() {
        open()

        val result = signup(password = "short")

        assertTrue(result.page().contains("Password must be at least 10 characters"))
        assertTrue(result.page().contains("action=\"/signup\""))
    }

    @Test
    fun `a taken username shows its message whatever the case`() {
        open()

        val result = signup(username = ADMIN.uppercase())

        assertTrue(result.page().contains("That username is already taken"))
        assertTrue(users.findByUsername(ADMIN)!!.admin)
    }

    @Test
    fun `a signup without the CSRF token is refused`() {
        open()

        mvc
            .perform(postWithoutCsrf("/signup").param("username", NEW_NAME).param("password", NEW_PASSWORD))
            .andExpect(status().isForbidden)

        assertNull(users.findByUsername(NEW_NAME))
    }

    @Test
    fun `a signed-in visitor to the signup page is sent to the timeline whether signups are on or off`() {
        withAccounts()
        val session = MockHttpSession().also { login(ADMIN, session = it) }

        mvc.perform(get("/signup").session(session)).andExpect(status().isFound).andExpect(redirectedUrl("/feed"))
        signups.set(true)
        mvc.perform(get("/signup").session(session)).andExpect(status().isFound).andExpect(redirectedUrl("/feed"))
    }

    @Test
    fun `a signed-in visitor posting a signup is sent to the timeline and creates nothing`() {
        open()
        val session = MockHttpSession().also { login(ADMIN, session = it) }

        val result = signup(session = session)

        assertEquals("/feed", result.response.redirectedUrl)
        assertNull(users.findByUsername(NEW_NAME))
    }

    @Test
    fun `five failed signups block the client with the too-many-attempts message`() {
        open()
        repeat(FAILURES_TO_BLOCK) { signup(username = "ab") }

        val result = signup()

        assertTrue(result.page().contains("Too many failed attempts. Try again later."))
        assertNull(users.findByUsername(NEW_NAME))
    }

    @Test
    fun `another client is not blocked`() {
        open()
        repeat(FAILURES_TO_BLOCK) { signup(username = "ab") }

        assertEquals("/feed", signup(from = "198.51.100.2").response.redirectedUrl)
    }

    @Test
    fun `the login page links to signup only while signups are on`() {
        withAccounts()
        mvc.perform(get("/login")).andExpect(content().string(not(containsString("/signup"))))

        signups.set(true)
        mvc.perform(get("/login")).andExpect(content().string(containsString("href=\"/signup\"")))
    }
}
