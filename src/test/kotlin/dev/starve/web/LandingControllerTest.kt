package dev.starve.web

import dev.starve.application.AppMetadata
import dev.starve.application.SignupSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.ResponseEntity
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.ui.ConcurrentModel
import java.security.Principal

class LandingControllerTest {
    private val metadata = HashMap<String, String>()
    private val settings =
        SignupSettings(
            object : AppMetadata {
                override fun find(key: String): String? = metadata[key]

                override fun putIfAbsent(
                    key: String,
                    value: String,
                ): Boolean = metadata.putIfAbsent(key, value) == null

                override fun put(
                    key: String,
                    value: String,
                ) {
                    metadata[key] = value
                }
            },
        )
    private val controller = LandingController(settings)

    private fun request(
        query: String?,
        signedIn: Boolean,
    ) = MockHttpServletRequest("GET", "/").apply {
        queryString = query
        if (signedIn) userPrincipal = Principal { "rob" }
    }

    @Test
    fun `an anonymous visitor gets the landing page whatever the query`() {
        val model = ConcurrentModel()

        assertEquals("landing", controller.home(request("favourites=1", signedIn = false), model))
        assertEquals("Starve", model.getAttribute("appName"))
    }

    @Test
    fun `the landing page knows whether signups are open`() {
        val closed = ConcurrentModel()
        controller.home(request(null, signedIn = false), closed)
        settings.set(true)
        val open = ConcurrentModel()
        controller.home(request(null, signedIn = false), open)

        assertEquals(false, closed.getAttribute("signupsOpen"))
        assertEquals(true, open.getAttribute("signupsOpen"))
    }

    @Test
    fun `a signed-in visitor is redirected to the timeline`() {
        val response = controller.home(request(null, signedIn = true), ConcurrentModel()) as ResponseEntity<*>

        assertEquals(302, response.statusCode.value())
        assertEquals("/feed", response.headers.location.toString())
    }

    @Test
    fun `the redirect keeps the query string exactly as sent`() {
        val signedIn = request("q=a%20b&group=7", signedIn = true)
        val response = controller.home(signedIn, ConcurrentModel()) as ResponseEntity<*>

        assertEquals("/feed?q=a%20b&group=7", response.headers.getFirst("Location"))
    }
}
