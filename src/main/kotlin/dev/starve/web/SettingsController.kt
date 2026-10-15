package dev.starve.web

import dev.starve.application.SignupSettings
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam

/**
 * Settings, for admins only (the security chain answers 403 to anyone else, and sends a visitor who is not signed
 * in to the login page). The signup switch is a plain form post, so it works without JavaScript.
 */
@Controller
class SettingsController(
    private val signups: SignupSettings,
) {
    @GetMapping("/settings")
    fun page(model: Model): String {
        model.addAttribute("signupsOn", signups.enabled())
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
