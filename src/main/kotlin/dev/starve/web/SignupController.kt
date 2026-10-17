package dev.starve.web

import dev.starve.application.SignupOutcome
import dev.starve.application.SignupService
import dev.starve.application.SignupSettings
import dev.starve.domain.AccountRules
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.server.ResponseStatusException

/** The fields of the signup form. */
class SignupForm {
    var username: String = ""
    var password: String = ""
}

/**
 * Self-service signup. While signups are on the page shows the form and a post creates a regular user and signs
 * them in; while they are off the page says so and a post is refused. A signed-in visitor is sent to the timeline.
 */
@Controller
class SignupController(
    private val signup: SignupService,
    private val settings: SignupSettings,
    private val accounts: UserDetailsService,
) {
    @GetMapping("/signup")
    fun page(
        request: HttpServletRequest,
        model: Model,
    ): String {
        if (request.userPrincipal != null) return "redirect:/feed"
        model.addAttribute("open", settings.enabled())
        return "signup"
    }

    @PostMapping("/signup")
    fun submit(
        @ModelAttribute form: SignupForm,
        request: HttpServletRequest,
        response: HttpServletResponse,
        model: Model,
    ): String {
        if (request.userPrincipal != null) return "redirect:/feed"
        return when (val outcome = signup.signUp(form.username, form.password, request.remoteAddr)) {
            is SignupOutcome.Created -> {
                signInSession(accounts, outcome.username, request, response)
                "redirect:/feed"
            }

            SignupOutcome.Closed -> {
                throw ResponseStatusException(HttpStatus.FORBIDDEN)
            }

            SignupOutcome.Blocked -> {
                rejected(model, AccountRules.TOO_MANY_ATTEMPTS)
            }

            SignupOutcome.Taken -> {
                rejected(model, AccountRules.USERNAME_TAKEN)
            }

            is SignupOutcome.Invalid -> {
                rejected(model, outcome.error)
            }
        }
    }

    private fun rejected(
        model: Model,
        message: String,
    ): String {
        model.addAttribute("open", true)
        model.addAttribute("message", message)
        model.addAttribute("invalid", invalidFieldOf(message, "password"))
        return "signup"
    }
}
