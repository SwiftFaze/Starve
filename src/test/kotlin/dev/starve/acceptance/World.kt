package dev.starve.acceptance

import java.net.http.HttpResponse

/** Per-run state shared by step-definition classes (Cucumber builds a fresh instance of each per scenario). */
object World {
    /** Stands in for YouTube oEmbed; started once because the application needs its URL when it starts. */
    val oembed: FeedServer by lazy { FeedServer() }

    val app = ApplicationHarness { "${oembed.baseUrl}/oembed" }
    var feeds: FeedServer? = null

    /** The latest HTTP response, whichever step class made the request. */
    lateinit var response: HttpResponse<String>

    /** The page bodies making up the timeline as loaded so far: the first page, then each loaded fragment. */
    var pages: List<String> = emptyList()

    /** The latest rendering of the sources page, however it was reached. */
    var sourcesPage: String = ""

    /** The `<article>` markup of the timeline entry titled [title] among [pages]. */
    fun entry(title: String): String =
        pages
            .joinToString("")
            .split("<article")
            .drop(1)
            .firstOrNull { it.contains("data-title=\"$title\"") }
            ?: error("no timeline entry '$title'")

    /** Set by every action step, so a phrase used to arrange and to assert knows which it is doing. */
    var actionHasRun = false

    fun feedServer(): FeedServer = requireNotNull(feeds) { "feed server not running" }
}

/** The entities Thymeleaf writes for text and attribute values. */
object Html {
    private val entities = listOf("&" to "&amp;", "<" to "&lt;", ">" to "&gt;", "\"" to "&quot;", "'" to "&#39;")

    fun escape(text: String): String = entities.fold(text) { acc, (raw, entity) -> acc.replace(raw, entity) }

    fun unescape(html: String): String = entities.reversed().fold(html) { text, (raw, ent) -> text.replace(ent, raw) }
}
