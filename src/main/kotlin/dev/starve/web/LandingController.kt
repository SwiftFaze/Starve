package dev.starve.web

import dev.starve.application.SignupSettings
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

/**
 * The public home page. An anonymous visitor gets the landing page whatever the query string; a signed-in one is
 * sent to the timeline at "/feed", keeping the query so old timeline URLs still work.
 */
@Controller
class LandingController(
    private val signups: SignupSettings,
) {
    @GetMapping("/")
    fun home(
        request: HttpServletRequest,
        model: Model,
    ): Any {
        if (request.userPrincipal == null) {
            model.addAttribute("appName", "Starve")
            model.addAttribute("signupsOpen", signups.enabled())
            return "landing"
        }
        val query = request.queryString?.let { "?$it" }.orEmpty()
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, "/feed$query").build<Unit>()
    }
}
