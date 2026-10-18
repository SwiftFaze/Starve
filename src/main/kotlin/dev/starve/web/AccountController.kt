package dev.starve.web

import dev.starve.application.AccountService
import dev.starve.application.PasswordChange
import dev.starve.domain.AccountRules
import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import java.security.Principal

/** The fields of the change-password form. */
class PasswordForm {
    var currentPassword: String = ""
    var newPassword: String = ""
}

/**
 * The Account page, open to every signed-in user: it changes their own password. The form is a plain post, so it
 * works without JavaScript; success redirects back with `?changed` and keeps the session.
 */
@Controller
class AccountController(
    private val accounts: AccountService,
) {
    @GetMapping("/account")
    fun page(
        principal: Principal,
        @RequestParam(required = false) changed: String?,
        model: Model,
    ): String {
        model.addAttribute("username", principal.name)
        model.addAttribute("changed", changed != null)
        return "account"
    }

    @PostMapping("/account/password")
    fun change(
        @CurrentUserId userId: Long,
        @ModelAttribute form: PasswordForm,
        request: HttpServletRequest,
        model: Model,
    ): String {
        val outcome = accounts.changePassword(userId, form.currentPassword, form.newPassword, request.remoteAddr)
        return when (outcome) {
            PasswordChange.Changed -> "redirect:/account?changed"
            PasswordChange.WrongCurrent -> rejected(request.userPrincipal, model, AccountRules.CURRENT_PASSWORD_WRONG)
            PasswordChange.Blocked -> rejected(request.userPrincipal, model, AccountRules.TOO_MANY_ATTEMPTS)
            is PasswordChange.Invalid -> rejected(request.userPrincipal, model, outcome.error)
        }
    }

    private fun rejected(
        principal: Principal,
        model: Model,
        message: String,
    ): String {
        model.addAttribute("username", principal.name)
        model.addAttribute("changed", false)
        model.addAttribute("message", message)
        model.addAttribute("invalid", invalidFieldOf(message, "new-password"))
        return "account"
    }
}
