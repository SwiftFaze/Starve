package dev.starve.infra

import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class SetupGateTest : SecurityTestBase() {
    @ParameterizedTest
    @ValueSource(
        strings = [
            "/", "/login", "/signup", "/settings", "/sources", "/nested/page.css", "/nested/app.js",
            "/fonts", "/page.css.map", "/setup/x",
        ],
    )
    fun `while no account exists everything else goes to setup`(path: String) {
        mvc
            .perform(get(path))
            .andExpect(status().isFound)
            .andExpect(redirectedUrl("/setup"))
    }

    @ParameterizedTest
    @ValueSource(
        strings = ["/setup", "/actuator/health", "/favicon.ico", "/fonts/mono.woff2", "/starve.css", "/htmx.min.js"],
    )
    fun `while no account exists setup, health and static assets stay open`(path: String) {
        mvc.perform(get(path)).andExpect(status().isOk)
    }

    @Test
    fun `while no account exists an htmx request is told to load setup as a whole page`() {
        mvc
            .perform(get("/sources").header("HX-Request", "true"))
            .andExpect(status().isUnauthorized)
            .andExpect(header().string("HX-Redirect", "/setup"))
    }

    @Test
    fun `while no account exists the setup page shows the form`() {
        mvc
            .perform(get("/setup"))
            .andExpect(content().string(containsString("Setup code")))
    }

    @Test
    fun `once an account exists the login page is served and setup is gone`() {
        withAccounts()

        mvc.perform(get("/login")).andExpect(status().isOk)
        mvc.perform(get("/setup")).andExpect(status().isNotFound)
    }

    @Test
    fun `the setup gate lets the login page through only after setup`() {
        assertTrue(setup.pending())

        mvc.perform(get("/login")).andExpect(redirectedUrl("/setup"))
    }

    @Test
    fun `while no account exists the error path is not redirected to setup`() {
        val result = mvc.perform(get("/error")).andReturn()

        assertNull(result.response.redirectedUrl)
    }
}
