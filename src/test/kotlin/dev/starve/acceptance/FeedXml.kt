package dev.starve.acceptance

/** Renders RSS 2.0 and Atom fixture documents from a [FeedSpec]. */
object FeedXml {
    fun render(
        baseUrl: String,
        path: String,
        spec: FeedSpec,
    ): String = if (spec.atom) atom(baseUrl, path, spec) else rss(baseUrl, path, spec)

    private fun rss(
        baseUrl: String,
        path: String,
        spec: FeedSpec,
    ): String {
        val title = spec.title?.let { "<title>$it</title>" } ?: ""
        val site = spec.siteLink?.let { "<link>$it</link>" } ?: ""
        val items = (1..spec.entries).joinToString("") { rssItem(baseUrl, path, spec, it) }
        return """<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0" xmlns:itunes="$ITUNES" xmlns:content="$CONTENT" xmlns:media="$MEDIA"><channel>$title$site""" +
            """<description>fixture</description>$items</channel></rss>"""
    }

    private fun rssItem(
        baseUrl: String,
        path: String,
        spec: FeedSpec,
        n: Int,
    ): String {
        val link = entryLink(baseUrl, path, spec, n)
        val linkTag = link?.let { "<link>$it</link>" } ?: ""
        val guid = if (spec.noGuid) "" else "<guid isPermaLink=\"false\">guid-$path-$n</guid>"
        val date = if (spec.noDate) "" else "<pubDate>${pubDate(n)}</pubDate>"
        val art = if (spec.coverArt) "<itunes:image href=\"${spec.coverArtUrl ?: "$baseUrl/art-$n.jpg"}\"/>" else ""
        val title = spec.itemTitle ?: "Entry $n"
        return "<item><title>$title</title>$linkTag$guid$date<author>a$n@example.com</author>" +
            "$art${media(spec)}${enclosure(spec)}${bodies(spec)}</item>"
    }

    private fun entryLink(
        baseUrl: String,
        path: String,
        spec: FeedSpec,
        n: Int,
    ): String? =
        when {
            spec.linkless -> null
            spec.entryLink != null -> spec.entryLink
            else -> "$baseUrl$path/item-$n"
        }

    private fun media(spec: FeedSpec): String {
        val thumbnail = spec.mediaThumbnail?.let { "<media:thumbnail url=\"$it\"/>" } ?: ""
        val content = spec.mediaContent?.let { mediaContent(it) } ?: ""
        return thumbnail + content
    }

    private fun mediaContent(content: MediaContent): String {
        val type = content.type?.let { " type=\"$it\"" }.orEmpty()
        val medium = content.medium?.let { " medium=\"$it\"" }.orEmpty()
        return "<media:content url=\"${content.url}\"$type$medium/>"
    }

    private fun enclosure(spec: FeedSpec): String =
        spec.enclosure?.let { "<enclosure url=\"$it\" type=\"image/jpeg\" length=\"0\"/>" }.orEmpty()

    private fun bodies(spec: FeedSpec): String {
        val description = spec.description?.let { "<description><![CDATA[$it]]></description>" } ?: ""
        val encoded = spec.content?.let { "<content:encoded><![CDATA[$it]]></content:encoded>" } ?: ""
        return description + encoded
    }

    private fun atom(
        baseUrl: String,
        path: String,
        spec: FeedSpec,
    ): String {
        val title = spec.title?.let { "<title>$it</title>" } ?: ""
        val entries = (1..spec.entries).joinToString("") { atomEntry(baseUrl, path, spec, it) }
        return """<?xml version="1.0" encoding="UTF-8"?>
<feed xmlns="http://www.w3.org/2005/Atom" xmlns:media="http://search.yahoo.com/mrss/">
<id>$baseUrl$path</id>$title<updated>2026-01-01T00:00:00Z</updated>$entries</feed>"""
    }

    private fun atomEntry(
        baseUrl: String,
        path: String,
        spec: FeedSpec,
        n: Int,
    ): String {
        val thumb =
            if (spec.thumbnails) {
                "<media:group><media:thumbnail url=\"$baseUrl/thumb-$n.jpg\"/></media:group>"
            } else {
                ""
            }
        val content = spec.content?.let { "<content type=\"html\">${escape(it)}</content>" } ?: ""
        return "<entry><id>yt:$path:$n</id><title>Entry $n</title><link href=\"$baseUrl$path/item-$n\"/>" +
            "<published>2026-01-0${n}T10:00:00Z</published><updated>2026-01-0${n}T10:00:00Z</updated>" +
            "<author><name>Author $n</name></author>$thumb$content</entry>"
    }

    private fun pubDate(n: Int) = "Mon, 0$n Jun 2026 10:00:00 GMT".replace("Mon", WEEKDAYS[(n - 1) % WEEKDAYS.size])

    private val WEEKDAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    private const val ITUNES = "http://www.itunes.com/dtds/podcast-1.0.dtd"
    private const val CONTENT = "http://purl.org/rss/1.0/modules/content/"
    private const val MEDIA = "http://search.yahoo.com/mrss/"

    private fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
