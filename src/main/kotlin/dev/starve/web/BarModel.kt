package dev.starve.web

import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ModelAttribute

/** Tells every page's top bar whether the signed-in user is an admin, who alone sees the Settings link. */
@ControllerAdvice
class BarModel {
    @ModelAttribute("admin")
    fun admin(request: HttpServletRequest): Boolean = request.isUserInRole("ADMIN")
}
