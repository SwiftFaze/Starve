package dev.starve.acceptance

import java.net.http.HttpResponse

/** Per-run state shared by step-definition classes (Cucumber builds a fresh instance of each per scenario). */
object World {
    /** The account every scenario that is not about accounts runs as. */
    const val OWNER = "owner"
    const val OWNER_PASSWORD = "owner-pass-1"

    /** Stands in for YouTube oEmbed; started once because the application needs its URL when it starts. */
    val oembed: FeedServer by lazy { FeedServer() }

    val app =
        ApplicationHarness(
            { "${oembed.baseUrl}/oembed" },
            { "${oembed.baseUrl}${FeedServer.SOUNDCLOUD_OEMBED_PATH}" },
        )
    var feeds: FeedServer? = null

    /** The latest HTTP response, whichever step class made the request. */
    lateinit var response: HttpResponse<String>

    /** The page bodies making up the timeline as loaded so far: the first page, then each loaded fragment. */
    var pages: List<String> = emptyList()

    /** The latest rendering of the sources page, however it was reached. */
    var sourcesPage: String = ""

    /** How long the latest reader request took, in milliseconds. */
    var readerMillis: Long = 0

    /** The stored body of the item the latest reader request opened, before that request ran. */
    var bodyBeforeReader: String? = null

    /** Replaces the `<feed server>` placeholder with the running feed server's base URL, when one runs. */
    fun expandFeedServer(text: String): String = text.replace("<feed server>", feeds?.baseUrl ?: "<feed server>")

    /** The `<article>` markup of the timeline entry titled [title] among [pages]. */
    fun entry(title: String): String =
        pages
            .joinToString("")
            .split("<article")
            .drop(1)
            .firstOrNull { it.contains("data-title=\"$title\"") }
            ?: error("no timeline entry '$title'")

    /** The opening `<a>` tag of the title link of the timeline entry titled [title]. */
    fun titleLink(title: String): String {
        val page = entry(title)
        return requireNotNull(Regex("<a class=\"title\"[^>]*>").find(page)) { "no title link in $page" }.value
    }

    /** Set by every action step, so a phrase used to arrange and to assert knows which it is doing. */
    var actionHasRun = false

    fun feedServer(): FeedServer = requireNotNull(feeds) { "feed server not running" }

    /** The opening `<a>` tag and the label of each link in the groups navigation of [page], in order. */
    fun navAnchors(page: String): List<Pair<String, String>> {
        val nav = requireNotNull(GROUPS_NAV.find(page)) { "no groups navigation" }.value
        return ANCHOR.findAll(nav).map { it.groupValues[1] to it.groupValues[2] }.toList()
    }

    private val GROUPS_NAV = Regex("<nav class=\"groups\".*?</nav>", RegexOption.DOT_MATCHES_ALL)
    private val ANCHOR = Regex("(<a [^>]*>)([^<]*)</a>")
}

/** The entities Thymeleaf writes for text and attribute values. */
object Html {
    private val entities = listOf("&" to "&amp;", "<" to "&lt;", ">" to "&gt;", "\"" to "&quot;", "'" to "&#39;")

    fun escape(text: String): String = entities.fold(text) { acc, (raw, entity) -> acc.replace(raw, entity) }

    fun unescape(html: String): String = entities.reversed().fold(html) { text, (raw, ent) -> text.replace(ent, raw) }

    /** The `<a>` markup whose text is [label], or null when the page has none. */
    fun anchor(
        page: String,
        label: String,
    ): String? = Regex("<a [^>]*>\\s*${Regex.escape(label)}\\s*</a>").find(page)?.value
}
