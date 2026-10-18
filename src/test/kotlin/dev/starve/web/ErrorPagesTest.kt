package dev.starve.web

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest
import java.security.Principal

class ErrorPagesTest {
    private fun heading(
        status: Int,
        signedIn: Boolean = true,
        posted: Boolean = false,
    ) = ErrorCopy.of(status, signedIn, posted).heading

    @Test
    fun `each kind of failure has its own heading`() {
        assertEquals("Nothing here", heading(404))
        assertEquals("Nothing here", heading(404, signedIn = false, posted = true))
        assertEquals("Admins only", heading(403))
        assertEquals("This form expired", heading(403, posted = true))
        assertEquals("This form expired", heading(403, signedIn = false, posted = true))
        assertEquals("Not allowed", heading(403, signedIn = false))
        assertEquals("Something broke on this server", heading(500))
        assertEquals("Something broke on this server", heading(503))
        assertEquals("That didn't work", heading(400))
        assertEquals("That didn't work", heading(499))
    }

    @Test
    fun `the copy carries the status code`() {
        assertEquals("503", ErrorCopy.of(503, signedIn = false, posted = false).code)
        assertEquals("Try again in a moment. The details are in the server log.", ErrorCopy.of(503, false, false).help)
    }

    @Test
    fun `a signed-in page request gets the error view with its copy`() {
        val request = MockHttpServletRequest("GET", "/settings").apply { userPrincipal = Principal { "rob" } }

        val view = ErrorPages().resolveErrorView(request, HttpStatus.FORBIDDEN, mutableMapOf())

        assertEquals("error", view.viewName)
        assertEquals(HttpStatus.FORBIDDEN, view.status)
        assertEquals(true, view.model["signedIn"])
        assertEquals("Admins only", (view.model["copy"] as ErrorCopy).heading)
    }

    @Test
    fun `a refused post, signed out, says the form expired`() {
        val request = MockHttpServletRequest("POST", "/signup")

        val view = ErrorPages().resolveErrorView(request, HttpStatus.FORBIDDEN, mutableMapOf())

        assertEquals(false, view.model["signedIn"])
        assertEquals("This form expired", (view.model["copy"] as ErrorCopy).heading)
    }
}
