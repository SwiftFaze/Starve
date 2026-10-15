package dev.starve.infra

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

private const val NEW_PASSWORD = "a brand new password"
private const val WRONG_CODE = "AAAA-AAAA"
private const val BLOCKING_FAILURES = 5

class SetupFlowTest : SecurityTestBase() {
    private fun setupForm(
        code: String,
        username: String = "alice",
        password: String = NEW_PASSWORD,
        from: String = "203.0.113.7",
    ) = post("/setup")
        .with(clientAt(from))
        .param("code", code)
        .param("username", username)
        .param("password", password)

    private fun submit(
        code: String,
        username: String = "alice",
        password: String = NEW_PASSWORD,
        from: String = "203.0.113.7",
    ): MvcResult = mvc.perform(setupForm(code, username, password, from)).andReturn()

    private fun MvcResult.page() = response.contentAsString

    private fun loggedBy(action: () -> Unit): List<String> {
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        val logger = LoggerFactory.getLogger(SetupCodeLogger::class.java) as Logger
        logger.addAppender(appender)
        try {
            action()
        } finally {
            logger.detachAppender(appender)
        }
        return appender.list.map { it.formattedMessage }
    }

    @Test
    fun `the right code creates the admin and signs them in`() {
        val code = setup.startRun()!!
        val session = MockHttpSession()

        val result = mvc.perform(setupForm(code).session(session)).andReturn()

        assertEquals("/feed", result.response.redirectedUrl)
        val body =
            mvc
                .perform(get("/whoami").session(session))
                .andReturn()
                .response.contentAsString
        assertEquals("alice:ROLE_ADMIN", body)
    }

    @Test
    fun `the admin created at setup can later sign in with the chosen password`() {
        val code = setup.startRun()!!
        submit(code)

        val result = login("alice", NEW_PASSWORD)

        assertEquals("/feed", result.response.redirectedUrl)
    }

    @Test
    fun `setup stores a hash of the password, not the password`() {
        val code = setup.startRun()!!
        submit(code)

        val stored = users.findByUsername("alice")!!.passwordHash

        assertNotEquals(NEW_PASSWORD, stored)
        assertTrue(BCryptPasswordEncoder().matches(NEW_PASSWORD, stored))
    }

    @Test
    fun `the code is accepted in lower case without its hyphen`() {
        val code = setup.startRun()!!

        val result = submit(code.lowercase().replace("-", ""))

        assertEquals("/feed", result.response.redirectedUrl)
    }

    @Test
    fun `signing in at setup gives the session a fresh id`() {
        val code = setup.startRun()!!
        val session = MockHttpSession()
        val before = session.id

        mvc.perform(setupForm(code).session(session))

        assertNotEquals(before, session.id)
    }

    @Test
    fun `a wrong code shows the invalid code message and creates nothing`() {
        setup.startRun()

        val result = submit(WRONG_CODE)

        assertEquals(200, result.response.status)
        assertTrue(result.page().contains("Invalid setup code"))
        assertTrue(setup.pending())
    }

    @Test
    fun `a rule-breaking username shows its message and creates nothing`() {
        val code = setup.startRun()!!

        val result = submit(code, username = "x")

        assertTrue(result.page().contains("Username must be 3-32"))
        assertTrue(setup.pending())
    }

    @Test
    fun `a short password shows its message`() {
        val code = setup.startRun()!!

        val result = submit(code, password = "short")

        assertTrue(result.page().contains("Password must be at least 10 characters"))
    }

    @Test
    fun `five wrong codes block that address even with the right code`() {
        val code = setup.startRun()!!
        repeat(BLOCKING_FAILURES) { submit(WRONG_CODE) }

        val result = submit(code)

        assertTrue(result.page().contains("Too many failed attempts. Try again later."))
        assertTrue(setup.pending())
    }

    @Test
    fun `a block on one address leaves another address free to set up`() {
        val code = setup.startRun()!!
        repeat(BLOCKING_FAILURES) { submit(WRONG_CODE, from = "203.0.113.7") }

        val result = submit(code, from = "198.51.100.9")

        assertEquals("/feed", result.response.redirectedUrl)
    }

    @Test
    fun `posting the setup form answers 404 once an account exists`() {
        withAccounts()

        val result = submit(WRONG_CODE)

        assertEquals(404, result.response.status)
    }

    @Test
    fun `a setup post without a CSRF token is refused`() {
        val code = setup.startRun()!!
        val form =
            postWithoutCsrf("/setup").param("code", code).param("username", "alice").param("password", NEW_PASSWORD)

        mvc.perform(form).andExpect(status().isForbidden)

        assertTrue(setup.pending())
    }

    @Test
    fun `startup logs the setup code while setup is pending`() {
        val lines = loggedBy { SetupCodeLogger(setup).logSetupCode() }

        assertTrue(Regex("Starve setup code: [A-Z2-9]{4}-[A-Z2-9]{4}").matches(lines.single()))
    }

    @Test
    fun `startup logs no setup code once an account exists`() {
        withAccounts()

        val lines = loggedBy { SetupCodeLogger(setup).logSetupCode() }

        assertTrue(lines.isEmpty())
        assertNull(setup.startRun())
    }
}
