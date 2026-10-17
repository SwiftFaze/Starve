package dev.starve.web

import jakarta.servlet.http.HttpServletRequest
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorViewResolver
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.servlet.ModelAndView

/** What the error page says for one failure: the status for its obi, a plain-words heading and one line of help. */
data class ErrorCopy(
    val code: String,
    val heading: String,
    val help: String,
) {
    companion object {
        /**
         * The copy for [status]. [posted] and [signedIn] tell the kinds of 403 apart: a refused post is almost always
         * a form whose CSRF token went stale (or a sign-up that closed meanwhile); a page is refused for not being an
         * admin.
         */
        fun of(
            status: Int,
            signedIn: Boolean,
            posted: Boolean,
        ): ErrorCopy {
            val code = status.toString()
            return when {
                status == NOT_FOUND -> {
                    ErrorCopy(code, "Nothing here", "There is no page at this address, or it was removed.")
                }

                status == FORBIDDEN && posted -> {
                    ErrorCopy(code, "This form expired", "Go back, reload the page and try again.")
                }

                status == FORBIDDEN && signedIn -> {
                    ErrorCopy(code, "Admins only", "Only an admin of this Starve can open this page.")
                }

                status == FORBIDDEN -> {
                    ErrorCopy(code, "Not allowed", "This page is not open to visitors.")
                }

                status >= SERVER_ERROR -> {
                    ErrorCopy(
                        code,
                        "Something broke on this server",
                        "Try again in a moment. The details are in the server log.",
                    )
                }

                else -> {
                    ErrorCopy(code, "That didn't work", "The request was refused. Go back and try again.")
                }
            }
        }

        private const val NOT_FOUND = 404
        private const val FORBIDDEN = 403
        private const val SERVER_ERROR = 500
    }
}

/**
 * Renders every HTML error response with templates/error.html in the app's own look, in place of Spring Boot's
 * Whitelabel page. The page only ever says what kind of failure it was, never which resource or why, so a 404 for
 * another user's id reads exactly like one for an id that never existed.
 */
@Component
class ErrorPages : ErrorViewResolver {
    override fun resolveErrorView(
        request: HttpServletRequest,
        status: HttpStatus,
        model: MutableMap<String, Any>,
    ): ModelAndView {
        val signedIn = request.userPrincipal != null
        val copy = ErrorCopy.of(status.value(), signedIn, posted = request.method == "POST")
        return ModelAndView("error", mapOf("copy" to copy, "signedIn" to signedIn), status)
    }
}
