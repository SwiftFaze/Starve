package dev.starve.domain

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.net.URISyntaxException

/** Query parameters that only tell a site where a click came from, dropped from every link out. */
object TrackingParams {
    private val NAMES =
        setOf(
            "fbclid",
            "gclid",
            "dclid",
            "gbraid",
            "wbraid",
            "msclkid",
            "yclid",
            "twclid",
            "igshid",
            "mc_cid",
            "mc_eid",
            "_hsenc",
            "_hsmi",
            "mkt_tok",
            "oly_anon_id",
            "oly_enc_id",
            "vero_id",
            "ttclid",
            "srsltid",
        )

    /** Whether the query parameter [name] is a tracking one: `utm_*` or a known click id, ignoring case. */
    fun isTracking(name: String): Boolean {
        val lower = name.lowercase()
        return lower.startsWith("utm_") || lower in NAMES
    }

    /**
     * [url] without its tracking parameters; every other parameter keeps its place and spelling, and so does the
     * fragment. A URL that does not parse, or has no query, comes back as it was.
     */
    fun strip(url: String): String {
        val uri = parsed(url)
        val pieces = uri?.rawQuery?.split('&').orEmpty()
        val kept = pieces.filter { it.isNotEmpty() && !isTracking(it.substringBefore('=')) }
        if (uri == null || kept.size == pieces.size) return url
        val query = if (kept.isEmpty()) "" else "?" + kept.joinToString("&")
        return url.substringBefore('?') + query + uri.rawFragment?.let { "#$it" }.orEmpty()
    }

    private fun parsed(url: String): URI? =
        try {
            URI(url)
        } catch (_: URISyntaxException) {
            null
        }
}

/** Which addresses the server may fetch images from on a user's behalf: never the home network or itself. */
object AddressRules {
    private const val BYTE_MASK = 0xff
    private const val CGNAT_FIRST = 100
    private const val CGNAT_SECOND_MIN = 64
    private const val CGNAT_SECOND_MAX = 127
    private const val IPV6_UNIQUE_LOCAL = 0xfc
    private const val IPV6_UNIQUE_LOCAL_MASK = 0xfe

    /** False for loopback, private, link-local, carrier-grade NAT, unique-local, wildcard and multicast addresses. */
    fun isPublic(address: InetAddress): Boolean {
        val local =
            address.isLoopbackAddress || address.isSiteLocalAddress || address.isLinkLocalAddress ||
                address.isAnyLocalAddress || address.isMulticastAddress
        return !local && !isSharedOrUniqueLocal(address)
    }

    private fun isSharedOrUniqueLocal(address: InetAddress): Boolean {
        val bytes = address.address.map { it.toInt() and BYTE_MASK }
        return when (address) {
            is Inet4Address -> bytes[0] == CGNAT_FIRST && bytes[1] in CGNAT_SECOND_MIN..CGNAT_SECOND_MAX
            is Inet6Address -> bytes[0] and IPV6_UNIQUE_LOCAL_MASK == IPV6_UNIQUE_LOCAL
            else -> false
        }
    }
}

/** What the image proxy passes on: raster and vector images only, up to a size. */
object ImageRules {
    const val MAX_BYTES = 5_000_000

    private val TYPES =
        setOf(
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp",
            "image/avif",
            "image/bmp",
            "image/svg+xml",
            "image/x-icon",
            "image/vnd.microsoft.icon",
        )

    /** The bare media type of a Content-Type header when it names an image the proxy serves; null otherwise. */
    fun acceptedType(contentType: String?): String? =
        contentType
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase()
            ?.takeIf { it in TYPES }
}
