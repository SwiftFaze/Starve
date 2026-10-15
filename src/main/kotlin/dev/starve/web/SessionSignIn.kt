package dev.starve.web

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.web.context.HttpSessionSecurityContextRepository

/**
 * Signs [username] in on this session, with a fresh session id as a login does. Used where an account is created
 * and the visitor should not have to log in again (setup and signup).
 */
fun signInSession(
    accounts: UserDetailsService,
    username: String,
    request: HttpServletRequest,
    response: HttpServletResponse,
) {
    val principal = accounts.loadUserByUsername(username)
    val context = SecurityContextHolder.createEmptyContext()
    context.authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.authorities)
    request.getSession(true)
    request.changeSessionId()
    HttpSessionSecurityContextRepository().saveContext(context, request, response)
}
