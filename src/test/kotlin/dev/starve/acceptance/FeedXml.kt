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
        val items = (1..spec.entries).joinToString("") { rssItem(baseUrl, path, spec, it) }
        return """<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0" xmlns:itunes="$ITUNES"><channel>$title<link>$baseUrl$path</link>""" +
            """<description>fixture</description>$items</channel></rss>"""
    }

    private fun rssItem(
        baseUrl: String,
        path: String,
        spec: FeedSpec,
        n: Int,
    ): String {
        val link = "$baseUrl$path/item-$n"
        val guid = if (spec.noGuid) "" else "<guid isPermaLink=\"false\">guid-$path-$n</guid>"
        val date = if (spec.noDate) "" else "<pubDate>${pubDate(n)}</pubDate>"
        val art = if (spec.coverArt) "<itunes:image href=\"${spec.coverArtUrl ?: "$baseUrl/art-$n.jpg"}\"/>" else ""
        return "<item><title>Entry $n</title><link>$link</link>$guid$date<author>a$n@example.com</author>$art</item>"
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
        return "<entry><id>yt:$path:$n</id><title>Entry $n</title><link href=\"$baseUrl$path/item-$n\"/>" +
            "<published>2026-01-0${n}T10:00:00Z</published><updated>2026-01-0${n}T10:00:00Z</updated>" +
            "<author><name>Author $n</name></author>$thumb</entry>"
    }

    private fun pubDate(n: Int) = "Mon, 0$n Jun 2026 10:00:00 GMT".replace("Mon", WEEKDAYS[(n - 1) % WEEKDAYS.size])

    private val WEEKDAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    private const val ITUNES = "http://www.itunes.com/dtds/podcast-1.0.dtd"
}
