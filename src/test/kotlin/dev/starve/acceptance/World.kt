package dev.starve.acceptance

import java.net.http.HttpResponse

/** Per-run state shared by step-definition classes (Cucumber builds a fresh instance of each per scenario). */
object World {
    val app = ApplicationHarness()
    var feeds: FeedServer? = null

    /** The latest HTTP response, whichever step class made the request. */
    lateinit var response: HttpResponse<String>

    /** The page bodies making up the timeline as loaded so far: the first page, then each loaded fragment. */
    var pages: List<String> = emptyList()

    fun feedServer(): FeedServer = requireNotNull(feeds) { "feed server not running" }
}
