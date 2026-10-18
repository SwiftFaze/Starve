package dev.starve.infra

import dev.starve.application.FeedFetchException
import java.net.URI
import java.net.http.HttpResponse

/** The answer at the end of a GET's redirects, and where its leading permanent (301/308) redirects led, if anywhere. */
data class Landing(
    val response: HttpResponse<ByteArray>,
    val movedTo: String?,
)

/**
 * GETs that follow redirects by hand, at most [MAX_REDIRECTS] hops, so that a feed's permanent move can be told apart
 * from a temporary one. [http] must not follow redirects itself.
 */
class FeedRedirects(
    private val http: BoundedHttp,
) {
    /** GETs [url] and follows its redirects; a loop, a chain too long or a bad target is a [FeedFetchException]. */
    fun get(
        url: String,
        accept: String,
        headers: Map<String, String>,
    ): Landing = follow(url, Request(accept, headers), Trail(listOf(url)))

    private tailrec fun follow(
        url: String,
        request: Request,
        trail: Trail,
    ): Landing {
        val response = http.get(url, request.accept, request.headers)
        val next = redirectTarget(url, response) ?: return Landing(response, trail.movedTo)
        if (next in trail.visited) throw FeedFetchException("redirect loop")
        if (trail.visited.size > MAX_REDIRECTS) throw FeedFetchException("more than $MAX_REDIRECTS redirects")
        return follow(next, request, trail.then(next, response.statusCode() in PERMANENT))
    }

    /** Where a redirect [response] to a GET of [url] points; null when it is not a redirect or names no target. */
    private fun redirectTarget(
        url: String,
        response: HttpResponse<*>,
    ): String? {
        val location = response.headers().firstValue("Location").orElse(null)
        if (response.statusCode() !in REDIRECTS || location == null) return null
        val target =
            try {
                URI.create(url).resolve(location.trim())
            } catch (e: IllegalArgumentException) {
                throw FeedFetchException("redirect to an invalid address", e)
            }
        if (target.scheme?.lowercase() !in HTTP_SCHEMES) throw FeedFetchException("redirect to a non-HTTP address")
        return target.toString()
    }

    private data class Request(
        val accept: String,
        val headers: Map<String, String>,
    )

    /** The URLs fetched so far, and where the leading run of permanent redirects led, if anywhere. */
    private data class Trail(
        val visited: List<String>,
        val movedTo: String? = null,
        val permanentSoFar: Boolean = true,
    ) {
        fun then(
            url: String,
            permanent: Boolean,
        ): Trail {
            val stillPermanent = permanentSoFar && permanent
            return Trail(visited + url, if (stillPermanent) url else movedTo, stillPermanent)
        }
    }

    private companion object {
        const val MAX_REDIRECTS = 5
        val PERMANENT = setOf(301, 308)
        val REDIRECTS = setOf(301, 302, 303, 307, 308)
        val HTTP_SCHEMES = setOf("http", "https")
    }
}
