package dev.starve.web

import dev.starve.application.SetupOutcome
import dev.starve.application.SetupService
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

/** The fields of the setup form. */
class SetupForm {
    var code: String = ""
    var username: String = ""
    var password: String = ""
}

/** First-run setup. Once any account exists, the page and its form answer 404. */
@Controller
class SetupController(
    private val setup: SetupService,
    private val accounts: UserDetailsService,
) {
    @GetMapping("/setup")
    fun form(): String {
        if (!setup.pending()) throw ResponseStatusException(HttpStatus.NOT_FOUND)
        return "setup"
    }

    @PostMapping("/setup")
    fun submit(
        @ModelAttribute form: SetupForm,
        request: HttpServletRequest,
        response: HttpServletResponse,
        model: Model,
    ): String =
        when (val outcome = setup.complete(form.code, form.username, form.password, request.remoteAddr)) {
            is SetupOutcome.Created -> {
                signInSession(accounts, outcome.username, request, response)
                "redirect:/feed"
            }

            SetupOutcome.AlreadySetUp -> {
                throw ResponseStatusException(HttpStatus.NOT_FOUND)
            }

            SetupOutcome.WrongCode -> {
                rejected(model, AccountRules.INVALID_SETUP_CODE)
            }

            SetupOutcome.Blocked -> {
                rejected(model, AccountRules.TOO_MANY_ATTEMPTS)
            }

            is SetupOutcome.Invalid -> {
                rejected(model, outcome.error)
            }
        }

    private fun rejected(
        model: Model,
        message: String,
    ): String {
        model.addAttribute("message", message)
        model.addAttribute("invalid", invalidFieldOf(message, "password"))
        return "setup"
    }
}
