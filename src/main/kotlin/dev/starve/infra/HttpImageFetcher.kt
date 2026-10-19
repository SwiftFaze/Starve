package dev.starve.infra

import dev.starve.application.FeedFetchException
import dev.starve.application.ImageFetcher
import dev.starve.application.ProxiedImage
import dev.starve.domain.AddressRules
import dev.starve.domain.FeedRules
import dev.starve.domain.ImageRules
import java.net.InetAddress
import java.net.URI
import java.net.UnknownHostException
import java.net.http.HttpResponse

/**
 * Fetches images for the proxy through [http], which must not follow redirects itself: each hop is followed here, at
 * most [MAX_HOPS], so that every host on the way is checked. A host that resolves to any private, loopback or
 * otherwise local address is refused, so a feed cannot point the proxy at the home network; only [trustedHosts]
 * (none by default; tests trust their local server) are exempt.
 */
class HttpImageFetcher(
    private val http: BoundedHttp,
    private val trustedHosts: Set<String> = emptySet(),
    private val resolve: (String) -> List<InetAddress> = { InetAddress.getAllByName(it).toList() },
    private val maxBytes: Int = ImageRules.MAX_BYTES,
) : ImageFetcher {
    override fun fetch(url: String): ProxiedImage? = hop(url, 0)

    /** [target], the [hops]th address on the way; a redirect is followed while hops are left. */
    private fun hop(
        target: String,
        hops: Int,
    ): ProxiedImage? {
        val allowed = hops <= MAX_HOPS && FeedRules.isHttpUrl(target) && reachable(target)
        val response = if (allowed) get(target) else null
        return when {
            response == null -> null
            response.statusCode() in REDIRECTS -> next(response, target)?.let { hop(it, hops + 1) }
            else -> image(response)
        }
    }

    private fun get(target: String): HttpResponse<ByteArray>? =
        try {
            http.get(target, ACCEPT)
        } catch (_: FeedFetchException) {
            null
        }

    /** Where a redirect from [from] points, made absolute; null when it points nowhere usable. */
    private fun next(
        response: HttpResponse<ByteArray>,
        from: String,
    ): String? = FeedRules.absoluteHttpUrl(response.headers().firstValue("Location").orElse(null), from)

    private fun image(response: HttpResponse<ByteArray>): ProxiedImage? {
        val type = ImageRules.acceptedType(response.headers().firstValue("Content-Type").orElse(null))
        val body = response.body()
        val ok = response.statusCode() in OK && type != null && body.isNotEmpty() && body.size <= maxBytes
        return if (ok) ProxiedImage(body, checkNotNull(type)) else null
    }

    /** Whether [url]'s host is trusted, or every address it resolves to may be fetched; an unknown host may not. */
    private fun reachable(url: String): Boolean {
        val host = URI(url.trim()).host?.lowercase()
        return host != null && (host in trustedHosts || resolvesPublic(host))
    }

    private fun resolvesPublic(host: String): Boolean {
        val addresses =
            try {
                resolve(host.removePrefix("[").removeSuffix("]"))
            } catch (_: UnknownHostException) {
                emptyList()
            }
        return addresses.isNotEmpty() && addresses.all(AddressRules::isPublic)
    }

    private companion object {
        const val MAX_HOPS = 3
        const val ACCEPT = "image/avif,image/webp,image/png,image/svg+xml,image/*;q=0.8"
        val OK = 200..299
        val REDIRECTS = setOf(301, 302, 303, 307, 308)
    }
}
