package dev.starve.acceptance

import io.cucumber.java.en.Given

/** Points Smart-add's platform rules at the feed server (smart-add-platforms.feature). */
class PlatformSteps {
    private val app = World.app

    @Given("the {string} rule uses the feed server")
    fun ruleUsesFeedServer(platform: String) = app.restartWith(*args(platform))

    @Given("every platform rule uses the feed server")
    fun everyRuleUsesFeedServer() = app.restartWith(*PLATFORMS.keys.flatMap { args(it).toList() }.toTypedArray())

    @Given("the feed server answers the lookup of podcast {string} with the feed {string}")
    fun lookupNames(
        id: String,
        path: String,
    ) {
        val feed = World.feedServer().baseUrl + path
        lookup(id, """{"resultCount":1,"results":[{"kind":"podcast","feedUrl":"$feed"}]}""")
    }

    @Given("the feed server answers the lookup of podcast {string} with no feed")
    fun lookupNamesNothing(id: String) = lookup(id, """{"resultCount":0,"results":[]}""")

    private fun lookup(
        id: String,
        json: String,
    ) = World.feedServer().route("/lookup/$id", Route.Json(json))

    private fun args(platform: String): Array<String> {
        val (prefix, template) = requireNotNull(PLATFORMS[platform]) { "unknown platform $platform" }
        val key = if (prefix == "apple-podcasts") "lookup-url-template" else "feed-url-template"
        val base = World.feedServer().baseUrl
        return arrayOf("--starve.$prefix.hosts=localhost", "--starve.$prefix.$key=$base$template")
    }

    private companion object {
        /** Each platform's property prefix, and the feed server path its template points at. */
        val PLATFORMS =
            linkedMapOf(
                "Reddit" to ("reddit" to "/reddit/r/{key}/.rss"),
                "GitHub" to ("github" to "/github/{key}.atom"),
                "Apple Podcasts" to ("apple-podcasts" to "/lookup/{key}"),
            )
    }
}
