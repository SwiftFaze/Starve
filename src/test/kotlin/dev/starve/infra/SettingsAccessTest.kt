package dev.starve.infra

import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class SettingsAccessTest : SecurityTestBase() {
    private fun signedIn(username: String) = MockHttpSession().also { login(username, session = it) }

    @ParameterizedTest
    @ValueSource(strings = ["/settings", "/settings/signups", "/settings/other"])
    fun `an anonymous visitor to settings is sent to the login page`(path: String) {
        withAccounts()

        mvc.perform(get(path)).andExpect(status().isFound).andExpect(redirectedUrl("http://localhost/login"))
    }

    @Test
    fun `an anonymous post to the switch is sent to login and changes nothing`() {
        withAccounts()

        mvc
            .perform(post("/settings/signups").param("allow", "true"))
            .andExpect(status().isFound)
            .andExpect(redirectedUrl("http://localhost/login"))

        assertFalse(signups.enabled())
    }

    @Test
    fun `an anonymous htmx request to settings gets 401 and an HX-Redirect to login`() {
        withAccounts()

        mvc
            .perform(get("/settings").header("HX-Request", "true"))
            .andExpect(status().isUnauthorized)
            .andExpect(header().string("HX-Redirect", "/login"))
    }

    @ParameterizedTest
    @ValueSource(strings = ["/settings", "/settings/other"])
    fun `a regular user gets 403 for settings`(path: String) {
        withAccounts()

        mvc.perform(get(path).session(signedIn(MEMBER))).andExpect(status().isForbidden)
    }

    @Test
    fun `a regular user cannot post the switch and it stays off`() {
        withAccounts()

        mvc
            .perform(post("/settings/signups").param("allow", "true").session(signedIn(MEMBER)))
            .andExpect(status().isForbidden)

        assertFalse(signups.enabled())
    }

    @Test
    fun `a regular user cannot turn the switch off either`() {
        withAccounts()
        signups.set(true)

        mvc
            .perform(post("/settings/signups").param("allow", "false").session(signedIn(MEMBER)))
            .andExpect(status().isForbidden)

        assertTrue(signups.enabled())
    }

    @Test
    fun `an admin sees the settings page with the switch off by default`() {
        withAccounts()

        mvc
            .perform(get("/settings").session(signedIn(ADMIN)))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Allow new signups")))
            .andExpect(content().string(containsString("aria-checked=\"false\"")))
            .andExpect(content().string(containsString("action=\"/settings/signups\"")))
            .andExpect(content().string(containsString("name=\"_csrf\"")))
    }

    @Test
    fun `the settings page carries the Settings link and the sign-out control for the admin`() {
        withAccounts()

        mvc
            .perform(get("/settings").session(signedIn(ADMIN)))
            .andExpect(content().string(containsString("href=\"/settings\"")))
            .andExpect(content().string(containsString("action=\"/logout\"")))
    }

    @Test
    fun `an admin turns signups on and the page shows the switch on`() {
        withAccounts()
        val session = signedIn(ADMIN)

        mvc
            .perform(post("/settings/signups").param("allow", "true").session(session))
            .andExpect(status().isFound)
            .andExpect(redirectedUrl("/settings"))

        assertTrue(signups.enabled())
        mvc
            .perform(get("/settings").session(session))
            .andExpect(content().string(containsString("aria-checked=\"true\"")))
    }

    @Test
    fun `an admin turns signups off again`() {
        withAccounts()
        signups.set(true)

        mvc.perform(post("/settings/signups").param("allow", "false").session(signedIn(ADMIN)))

        assertFalse(signups.enabled())
    }

    @Test
    fun `the switch needs the CSRF token`() {
        withAccounts()

        mvc
            .perform(postWithoutCsrf("/settings/signups").param("allow", "true").session(signedIn(ADMIN)))
            .andExpect(status().isForbidden)

        assertFalse(signups.enabled())
    }
}
