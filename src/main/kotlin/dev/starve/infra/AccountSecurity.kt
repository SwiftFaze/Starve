package dev.starve.infra

import dev.starve.application.FailureThrottle
import dev.starve.application.SetupService
import dev.starve.application.UserRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.AccountStatusException
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.Authentication
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.authentication.AuthenticationFailureHandler
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint
import org.springframework.security.web.authentication.WebAuthenticationDetails
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.security.web.savedrequest.HttpSessionRequestCache
import org.springframework.security.web.util.matcher.RequestMatcher
import org.springframework.web.filter.OncePerRequestFilter
import java.util.Locale

/** Raised instead of a password check while a username and client address are blocked. */
class LoginBlockedException : AccountStatusException("Too many failed attempts. Try again later.")

/** Refuses blocked username and client pairs before any password check, and counts the failures. */
class ThrottledLoginProvider(
    private val delegate: AuthenticationProvider,
    private val throttle: FailureThrottle,
) : AuthenticationProvider {
    override fun authenticate(authentication: Authentication): Authentication {
        val key = loginKey(authentication)
        if (throttle.isBlocked(key)) throw LoginBlockedException()
        try {
            val result = delegate.authenticate(authentication)
            throttle.reset(key)
            return result
        } catch (failure: BadCredentialsException) {
            throttle.recordFailure(key)
            throw failure
        }
    }

    override fun supports(authentication: Class<*>): Boolean = delegate.supports(authentication)

    private fun loginKey(authentication: Authentication): String {
        val address = (authentication.details as? WebAuthenticationDetails)?.remoteAddress.orEmpty()
        return "login|${authentication.name.lowercase(Locale.ROOT)}|$address"
    }
}

/** Sends a blocked attempt and a wrong password to different login messages. */
class LoginFailureHandler : AuthenticationFailureHandler {
    override fun onAuthenticationFailure(
        request: HttpServletRequest,
        response: HttpServletResponse,
        exception: AuthenticationException,
    ) {
        response.sendRedirect(if (exception is LoginBlockedException) "/login?blocked" else "/login?error")
    }
}

/**
 * Keeps the first-run gate in one place: while no account exists, everything except setup, health and
 * static assets goes to /setup, including /login.
 */
class SetupGate(
    private val setup: SetupService,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        if (setup.pending() && !OPEN_WHILE_SETUP.matches(request.servletPath)) {
            redirectTo(request, response, "/setup")
        } else {
            chain.doFilter(request, response)
        }
    }

    private companion object {
        val OPEN_WHILE_SETUP =
            Regex("/setup|/docs|/error|/actuator/health|/favicon\\.ico|/fonts/.+|/[^/]+\\.(css|js|svg)")
    }
}

/**
 * Treats a signed-in session whose account has been deleted as signed out, on every page rather than only those that
 * look the user up: the context and session are dropped, so the request goes on as anonymous (and is sent to /login
 * where the page needs a user). A remember-me cookie of a deleted account already fails when it is read.
 */
class DeletedUserFilter(
    private val users: UserRepository,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val authentication = SecurityContextHolder.getContext().authentication
        if (authentication != null && authentication !is AnonymousAuthenticationToken &&
            !accountExists(authentication)
        ) {
            SecurityContextHolder.clearContext()
            request.getSession(false)?.invalidate()
        }
        chain.doFilter(request, response)
    }

    private fun accountExists(authentication: Authentication) = users.findByUsername(authentication.name) != null
}

/** Loads an account for the password check, the remember-me cookie and the setup sign-in. */
class AccountDetailsService(
    private val users: UserRepository,
) : UserDetailsService {
    override fun loadUserByUsername(username: String): UserDetails {
        val user = users.findByUsername(username) ?: throw UsernameNotFoundException(username)
        return User
            .withUsername(user.username)
            .password(user.passwordHash)
            .authorities(if (user.admin) "ROLE_ADMIN" else "ROLE_USER")
            .build()
    }
}

/** Whether the request came from htmx, which swaps fragments and so must never receive a login page. */
fun isHtmxRequest(request: HttpServletRequest): Boolean = request.getHeader("HX-Request") != null

/** A plain redirect, or for htmx an HX-Redirect so the whole page loads at [target]. */
fun redirectTo(
    request: HttpServletRequest,
    response: HttpServletResponse,
    target: String,
) {
    if (isHtmxRequest(request)) {
        response.setHeader("HX-Redirect", target)
        response.status = HttpServletResponse.SC_UNAUTHORIZED
    } else {
        response.sendRedirect(target)
    }
}

/**
 * Reads the CSRF token on every request. Spring creates the token lazily, and the XSRF-TOKEN cookie that htmx and
 * the forms need is only written once it is read, so a page without a form would otherwise never set it.
 */
class CsrfCookieWriter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val csrf = request.getAttribute(CsrfToken::class.java.name) as? CsrfToken
        csrf?.token
        chain.doFilter(request, response)
    }
}

/**
 * Saves a refused request for the login to return to only when it was a page the browser was opening. An htmx
 * fragment would land the login on a bare fragment, and a request the browser makes on its own (DevTools' probe of
 * a well-known path, a manifest, robots.txt) would land it on a 404.
 */
fun pageOnlyRequestCache() =
    HttpSessionRequestCache().apply {
        // Without this, Spring appends "?continue" to the page a login returns to.
        setMatchingRequestParameterName(null)
        setRequestMatcher(RequestMatcher { request -> isPageLoad(request) && !isHtmxRequest(request) })
    }

/**
 * A GET the browser makes to show a page: its fetch metadata names a document, or, from a client that sends none,
 * it asks for HTML.
 */
fun isPageLoad(request: HttpServletRequest): Boolean {
    val destination = request.getHeader("Sec-Fetch-Dest")
    val page = if (destination != null) destination == "document" else asksForHtml(request)
    return request.method == "GET" && page
}

private fun asksForHtml(request: HttpServletRequest) = request.getHeader("Accept").orEmpty().contains("text/html")

/** A browser is sent to /login; an htmx request is told to load /login as a whole page. */
fun loginEntryPoint(): AuthenticationEntryPoint {
    val browser = LoginUrlAuthenticationEntryPoint("/login")
    return AuthenticationEntryPoint { request, response, exception: AuthenticationException ->
        if (isHtmxRequest(request)) {
            redirectTo(request, response, "/login")
        } else {
            browser.commence(request, response, exception)
        }
    }
}
