package dev.starve.web

import dev.starve.application.UserAction
import dev.starve.application.UserAdminService
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.mvc.support.RedirectAttributes

/**
 * Admin actions on other users under /settings/users (the security chain keeps everyone else out): a password
 * reset that answers with the temporary password once, and deletion behind a confirmation page. Every action works
 * as a plain form post. An id that is no user, or not a number, is a 404; aiming at oneself is a 400.
 */
@Controller
class UserAdminController(
    private val users: UserAdminService,
) {
    /**
     * The temporary password is a credential, so it is answered directly (no redirect, session or URL) and the
     * response is never cached.
     */
    @PostMapping("/settings/users/{id}/reset")
    fun reset(
        @CurrentUserId adminId: Long,
        @PathVariable id: String,
        response: HttpServletResponse,
        model: Model,
    ): String {
        val reset = users.resetPassword(adminId, userId(id)).done()
        response.setHeader("Cache-Control", "no-store")
        model.addAttribute("username", reset.username)
        model.addAttribute("temporaryPassword", reset.temporaryPassword)
        return "temporary-password"
    }

    @GetMapping("/settings/users/{id}/delete")
    fun confirmDelete(
        @CurrentUserId adminId: Long,
        @PathVariable id: String,
        model: Model,
    ): String {
        model.addAttribute("id", userId(id))
        model.addAttribute("username", users.lookup(adminId, userId(id)).done())
        return "delete-user"
    }

    @PostMapping("/settings/users/{id}/delete")
    fun delete(
        @CurrentUserId adminId: Long,
        @PathVariable id: String,
        redirect: RedirectAttributes,
    ): String {
        redirect.addFlashAttribute("notice", "Deleted user ${users.delete(adminId, userId(id)).done()}")
        return "redirect:/settings"
    }

    private fun userId(raw: String): Long = raw.toLongOrNull() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)

    private fun <T> UserAction<T>.done(): T =
        when (this) {
            is UserAction.Done -> value
            UserAction.Missing -> throw ResponseStatusException(HttpStatus.NOT_FOUND)
            UserAction.Self -> throw ResponseStatusException(HttpStatus.BAD_REQUEST)
        }
}
