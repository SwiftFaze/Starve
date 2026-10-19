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
) : ImageFetcher {
    override fun fetch(url: String): ProxiedImage? {
        var target = url
        repeat(MAX_HOPS + 1) {
            if (!FeedRules.isHttpUrl(target) || !reachable(target)) return null
            val response =
                try {
                    http.get(target, ACCEPT)
                } catch (_: FeedFetchException) {
                    return null
                }
            val status = response.statusCode()
            if (status !in REDIRECTS) return image(status, response.headers().firstValue("Content-Type").orElse(null), response.body())
            val location = response.headers().firstValue("Location").orElse(null) ?: return null
            target = FeedRules.absoluteHttpUrl(location, target) ?: return null
        }
        return null
    }

    private fun image(
        status: Int,
        contentType: String?,
        body: ByteArray,
    ): ProxiedImage? {
        val type = ImageRules.acceptedType(contentType)
        val ok = status in OK && type != null && body.isNotEmpty() && body.size <= ImageRules.MAX_BYTES
        return if (ok) ProxiedImage(body, checkNotNull(type)) else null
    }

    /** Whether every address [url]'s host resolves to may be fetched; an unknown host may not. */
    private fun reachable(url: String): Boolean {
        val host = URI(url.trim()).host ?: return false
        if (host.lowercase() in trustedHosts) return true
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
