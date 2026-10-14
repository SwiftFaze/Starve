package dev.starve.web

import dev.starve.domain.AccountRules
import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam

/** The login page. The form posts to Spring Security, which is where a password is checked. */
@Controller
class LoginController {
    @GetMapping("/login")
    fun login(
        @RequestParam(required = false) error: String?,
        @RequestParam(required = false) blocked: String?,
        request: HttpServletRequest,
        model: Model,
    ): String {
        if (request.userPrincipal != null) return "redirect:/feed"
        model.addAttribute("message", messageFor(error, blocked))
        return "login"
    }

    private fun messageFor(
        error: String?,
        blocked: String?,
    ): String? =
        when {
            blocked != null -> AccountRules.TOO_MANY_ATTEMPTS
            error != null -> AccountRules.LOGIN_FAILED
            else -> null
        }
}
