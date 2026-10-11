package dev.starve.acceptance

/** Per-run state shared by step-definition classes (Cucumber builds a fresh instance of each per scenario). */
object World {
    val app = ApplicationHarness()
    var feeds: FeedServer? = null

    fun feedServer(): FeedServer = requireNotNull(feeds) { "feed server not running" }
}
