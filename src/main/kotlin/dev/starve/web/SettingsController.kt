package dev.starve.web

import dev.starve.application.SignupSettings
import dev.starve.application.UserAdminService
import dev.starve.domain.UserAccount
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** One row of the Settings user list: [created] is the UTC date as YYYY-MM-DD and [you] marks the signed-in admin. */
data class UserRow(
    val id: Long,
    val username: String,
    val admin: Boolean,
    val you: Boolean,
    val created: String,
)

private val DAY: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC)

private fun UserAccount.row(viewerId: Long) = UserRow(id, username, admin, id == viewerId, DAY.format(createdAt))

/**
 * Settings, for admins only (the security chain answers 403 to anyone else, and sends a visitor who is not signed
 * in to the login page). The signup switch is a plain form post, so it works without JavaScript. The page also
 * lists the users; resetting and deleting them is [UserAdminController].
 */
@Controller
class SettingsController(
    private val signups: SignupSettings,
    private val users: UserAdminService,
) {
    @GetMapping("/settings")
    fun page(
        @CurrentUserId adminId: Long,
        model: Model,
    ): String {
        model.addAttribute("signupsOn", signups.enabled())
        model.addAttribute("users", users.list().map { it.row(adminId) })
        return "settings"
    }

    @PostMapping("/settings/signups")
    fun setSignups(
        @RequestParam allow: Boolean,
    ): String {
        signups.set(allow)
        return "redirect:/settings"
    }
}
