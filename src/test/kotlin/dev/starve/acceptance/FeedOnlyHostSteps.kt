package dev.starve.acceptance

import io.cucumber.java.en.Given

/** Steps for reader-feed-hosts.feature: the configurable list of hosts whose pages are never fetched. */
class FeedOnlyHostSteps {
    @Given("the reader treats {string} as its only feed-only host")
    fun onlyFeedOnlyHost(host: String) = World.app.restartWith("--starve.reader.feed-only-hosts=$host")
}
