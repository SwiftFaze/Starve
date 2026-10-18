package dev.starve.domain

import java.net.URI
import java.net.URISyntaxException

/** Platforms whose pages advertise no feed link, but whose feeds sit at an address built from the page's URL. */
enum class Platform {
    /** A subreddit page, `/r/<name>`: key `<name>`. */
    REDDIT,

    /** A Vimeo channel, `/channels/<name>` (key `channels/<name>`), or a user, `/<name>` (key `<name>`). */
    VIMEO,

    /** A GitHub user page, `/<user>`: key `<user>`. */
    GITHUB,

    /** An Apple Podcasts show link ending in `id<digits>`: key the digits, the show's id for the lookup API. */
    APPLE_PODCASTS,
}

/**
 * Where [platform] serves on [hosts], and the address of the feed (for Apple Podcasts, of the lookup that names it)
 * for a page: [template] with `{key}` replaced by the key the page's URL yields.
 */
data class PlatformSite(
    val platform: Platform,
    val hosts: Set<String>,
    val template: String,
)

/** A page recognised as one of [site]'s, with the [key] its feed address is built from. */
data class PlatformPage(
    val site: PlatformSite,
    val key: String,
) {
    /** [site]'s template filled with [key]. */
    val address: String get() = site.template.replace("{key}", key)
}

/** Recognises platform pages from their URL alone. Keys only ever hold letters, digits, `_`, `-` and one `/`. */
object PlatformPages {
    private val SUBREDDIT = Regex("[A-Za-z0-9_]{2,21}")
    private val VIMEO_NAME = Regex("[A-Za-z0-9_-]*[A-Za-z_-][A-Za-z0-9_-]*")
    private val GITHUB_USER = Regex("[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})")
    private val APPLE_ID = Regex("id(\\d+)")

    /** The page [url] stands for on the first of [sites] whose host it is on; null when none recognises it. */
    fun match(
        url: String,
        sites: List<PlatformSite>,
    ): PlatformPage? {
        val uri = parse(url.trim())
        val host = uri?.host?.lowercase() ?: return null
        val segments =
            uri.rawPath
                .orEmpty()
                .split('/')
                .filter { it.isNotEmpty() }
        return sites
            .filter { host in it.hosts }
            .firstNotNullOfOrNull { site -> keyOf(site.platform, segments)?.let { PlatformPage(site, it) } }
    }

    private fun keyOf(
        platform: Platform,
        segments: List<String>,
    ): String? =
        when (platform) {
            Platform.REDDIT -> subredditKey(segments)
            Platform.VIMEO -> vimeoKey(segments)
            Platform.GITHUB -> segments.singleOrNull()?.takeIf(GITHUB_USER::matches)
            Platform.APPLE_PODCASTS -> appleKey(segments)
        }

    private fun subredditKey(segments: List<String>): String? =
        segments
            .takeIf { it.size == 2 && it[0].lowercase() == "r" }
            ?.get(1)
            ?.takeIf(SUBREDDIT::matches)

    private fun vimeoKey(segments: List<String>): String? {
        val name = segments.lastOrNull()?.takeIf(VIMEO_NAME::matches)
        return when {
            name == null -> null
            segments.size == 2 && segments[0] == "channels" -> "channels/$name"
            segments.size == 1 -> name
            else -> null
        }
    }

    private fun appleKey(segments: List<String>): String? =
        if ("podcast" in segments) {
            segments
                .lastOrNull()
                ?.let { APPLE_ID.matchEntire(it) }
                ?.groupValues
                ?.get(1)
        } else {
            null
        }

    private fun parse(raw: String): URI? =
        try {
            URI(raw)
        } catch (_: URISyntaxException) {
            null
        }
}
