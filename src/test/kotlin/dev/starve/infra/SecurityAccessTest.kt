package dev.starve.infra

import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.http.HttpMethod
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class SecurityAccessTest : SecurityTestBase() {
    @ParameterizedTest
    @ValueSource(
        strings = [
            "/sources", "/whoami", "/nested/page.css", "/nested/app.js", "/other/fonts/x.woff2",
            "/actuator/info",
        ],
    )
    fun `an anonymous visitor to any other path is sent to the login page`(path: String) {
        withAccounts()

        mvc
            .perform(get(path))
            .andExpect(status().isFound)
            .andExpect(redirectedUrl("http://localhost/login"))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "/", "/login", "/signup", "/actuator/health", "/starve.css", "/htmx.min.js", "/fonts/mono.woff2",
            "/fonts/a/b.woff2", "/favicon.ico",
        ],
    )
    fun `an anonymous visitor can reach the public paths`(path: String) {
        withAccounts()

        mvc.perform(get(path)).andExpect(status().isOk)
    }

    @Test
    fun `setup is public but closed once an account exists`() {
        withAccounts()

        mvc.perform(get("/setup")).andExpect(status().isNotFound)
    }

    @Test
    fun `a signed in visitor reaches a protected path`() {
        withAccounts()
        val session = MockHttpSession()
        login(ADMIN, session = session)

        mvc.perform(get("/whoami").session(session)).andExpect(status().isOk)
    }

    @Test
    fun `an anonymous htmx request gets 401 and an HX-Redirect to the login page`() {
        withAccounts()

        mvc
            .perform(get("/sources").header("HX-Request", "true"))
            .andExpect(status().isUnauthorized)
            .andExpect(header().string("HX-Redirect", "/login"))
    }

    @Test
    fun `the page a visitor asked for is where the login returns, without a continue parameter`() {
        withAccounts()
        val session = MockHttpSession()
        mvc.perform(get("/sources").session(session)).andExpect(status().isFound)

        val result = login(ADMIN, session = session)

        assertEquals("http://localhost/sources", result.response.redirectedUrl)
    }

    @Test
    fun `an htmx request that was bounced to login is not what the login returns to`() {
        withAccounts()
        val session = MockHttpSession()
        mvc.perform(get("/fragment").session(session).header("HX-Request", "true")).andExpect(status().isUnauthorized)

        val result = login(ADMIN, session = session)

        assertEquals("/feed", result.response.redirectedUrl)
    }

    @Test
    fun `a login with nothing saved goes to the timeline`() {
        withAccounts()

        val result = login(ADMIN)

        assertEquals("/feed", result.response.redirectedUrl)
    }

    @Test
    fun `a post without a CSRF token is refused`() {
        withAccounts()

        mvc
            .perform(postWithoutCsrf("/login").param("username", ADMIN).param("password", PASSWORD))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `a CSRF token sent under another header name is refused`() {
        withAccounts()
        val form =
            request(HttpMethod.POST, "/login")
                .cookie(Cookie(CSRF_COOKIE, CSRF_TOKEN))
                .header("X-XSRF-TOKEN", CSRF_TOKEN)
                .param("username", ADMIN)
                .param("password", PASSWORD)

        mvc.perform(form).andExpect(status().isForbidden)
    }

    @Test
    fun `a post with the token in the X-CSRF-TOKEN header is accepted`() {
        withAccounts()

        val result = login(ADMIN)

        assertEquals(302, result.response.status)
    }

    @Test
    fun `a post whose header token differs from the cookie is refused`() {
        withAccounts()
        val form =
            request(HttpMethod.POST, "/login")
                .cookie(Cookie(CSRF_COOKIE, CSRF_TOKEN))
                .header(CSRF_HEADER, "another-token")
                .param("username", ADMIN)
                .param("password", PASSWORD)

        mvc.perform(form).andExpect(status().isForbidden)
    }

    @Test
    fun `every page sets a script-readable XSRF-TOKEN cookie, even without a form`() {
        withAccounts()

        val cookie =
            mvc
                .perform(get("/starve.css"))
                .andReturn()
                .response
                .getCookie(CSRF_COOKIE)

        assertNotNull(cookie)
        assertFalse(cookie!!.isHttpOnly)
        assertEquals("/", cookie.path)
    }

    @Test
    fun `the error path is never redirected to login`() {
        withAccounts()

        val result = mvc.perform(get("/error")).andReturn()

        assertNull(result.response.redirectedUrl)
    }
}
