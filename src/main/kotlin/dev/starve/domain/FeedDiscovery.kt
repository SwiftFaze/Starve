package dev.starve.domain

import java.net.URI
import java.net.URISyntaxException

/** Finds the feed behind an HTML page: its alternate link or, for SoundCloud profiles, the user id marker. */
object FeedDiscovery {
    private val FEED_TYPES = setOf("application/rss+xml", "application/atom+xml")
    private val LINK_TAG = Regex("<link\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val ATTRIBUTE = Regex("([^\\s=/<>\"']+)(?:\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"'>]+)))?")
    private val SOUNDCLOUD_ID = Regex("soundcloud://users:(\\d+)")
    private val HTML_ELEMENT = Regex("<html[\\s>]", RegexOption.IGNORE_CASE)
    private const val LINK_PREFIX_LENGTH = "<link".length
    private val WHITESPACE = Regex("\\s+")

    /** True for an HTML content type, or a body that contains an html element and is not typed as XML. */
    fun isHtml(
        contentType: String?,
        body: String,
    ): Boolean {
        val type = contentType.orEmpty().lowercase()
        return "html" in type || ("xml" !in type && HTML_ELEMENT.containsMatchIn(body))
    }

    /** The absolute URL of the first RSS/Atom alternate link in [html], relative hrefs resolved against [pageUrl]. */
    fun alternateFeed(
        html: String,
        pageUrl: String,
    ): String? =
        LINK_TAG
            .findAll(html)
            .map { attributes(it.value) }
            .filter { isFeedLink(it) }
            .mapNotNull { resolve(pageUrl, it["href"]) }
            .firstOrNull()

    /**
     * The absolute URL of the first `rel="icon"` or `rel="shortcut icon"` link in [html];
     * hrefs resolve against [pageUrl].
     */
    fun iconLink(
        html: String,
        pageUrl: String,
    ): String? =
        LINK_TAG
            .findAll(html)
            .map { attributes(it.value) }
            .filter { "icon" in it["rel"].orEmpty().lowercase().split(WHITESPACE) }
            .mapNotNull { resolve(pageUrl, it["href"]) }
            .firstOrNull()

    /** The numeric SoundCloud user id of a profile page on one of [hosts], or null when it is not one. */
    fun soundCloudUserId(
        html: String,
        pageUrl: String,
        hosts: Set<String>,
    ): String? = if (isProfilePage(pageUrl, hosts)) SOUNDCLOUD_ID.find(html)?.groupValues?.get(1) else null

    private fun isProfilePage(
        pageUrl: String,
        hosts: Set<String>,
    ): Boolean {
        val uri = parse(pageUrl) ?: return false
        val segments =
            uri.path
                .orEmpty()
                .split('/')
                .filter { it.isNotEmpty() }
        return uri.host?.lowercase() in hosts && segments.size == 1
    }

    private fun isFeedLink(attributes: Map<String, String>): Boolean {
        val rel = attributes["rel"].orEmpty().lowercase().split(WHITESPACE)
        val type =
            attributes["type"]
                .orEmpty()
                .substringBefore(';')
                .trim()
                .lowercase()
        return "alternate" in rel && type in FEED_TYPES
    }

    private fun attributes(tag: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        ATTRIBUTE.findAll(tag.drop(LINK_PREFIX_LENGTH).removeSuffix(">")).forEach {
            val value =
                it.groupValues
                    .drop(2)
                    .firstOrNull { part -> part.isNotEmpty() }
                    .orEmpty()
            result.putIfAbsent(it.groupValues[1].lowercase(), decode(value))
        }
        return result
    }

    private fun decode(value: String) =
        value
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")

    private fun resolve(
        pageUrl: String,
        href: String?,
    ): String? {
        val base = parse(pageUrl)
        val target = href?.trim()?.takeIf { it.isNotEmpty() }?.let { parse(it) }
        return base?.let { b -> target?.let { b.resolve(it).toString() } }?.takeIf { FeedRules.isHttpUrl(it) }
    }

    private fun parse(raw: String): URI? =
        try {
            URI(raw)
        } catch (_: URISyntaxException) {
            null
        }
}
