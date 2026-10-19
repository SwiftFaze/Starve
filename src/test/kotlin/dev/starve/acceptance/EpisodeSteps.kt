package dev.starve.acceptance

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

/** Steps for podcast-audio.feature: podcast feeds and the deck's audio player. */
class EpisodeSteps {
    private fun serve(
        path: String,
        spec: FeedSpec,
    ) = World.feedServer().route(path, Route.Feed(spec))

    @Given(
        "the feed server serves {string} as a podcast titled {string} whose episode has the audio enclosure {string}",
    )
    fun podcast(
        path: String,
        title: String,
        audio: String,
    ) = serve(path, FeedSpec(title = title, audioEnclosure = World.expandFeedServer(audio)))

    @Given(
        "the feed server serves {string} as a podcast titled {string} " +
            "whose episode has the audio enclosure {string} lasting {string}",
    )
    fun podcastLasting(
        path: String,
        title: String,
        audio: String,
        duration: String,
    ) = serve(path, FeedSpec(title = title, audioEnclosure = audio, itunesDuration = duration))

    @Given(
        "the feed server serves {string} as a podcast titled {string} " +
            "whose episode has the audio media:content {string} of type {string}",
    )
    fun podcastMediaContent(
        path: String,
        title: String,
        audio: String,
        type: String,
    ) = serve(path, FeedSpec(title = title, mediaContent = MediaContent(audio, type = type)))

    @Given(
        "the feed server serves {string} as a podcast titled {string} with the show art {string} " +
            "whose episode has the audio enclosure {string}",
    )
    fun podcastWithArt(
        path: String,
        title: String,
        art: String,
        audio: String,
    ) = serve(path, FeedSpec(title = title, audioEnclosure = audio, showArt = art))

    @Given(
        "the feed server serves {string} as a podcast titled {string} " +
            "whose episode links to {string} and has the audio enclosure {string}",
    )
    fun podcastLinkingOut(
        path: String,
        title: String,
        link: String,
        audio: String,
    ) = serve(path, FeedSpec(title = title, entryLink = link, audioEnclosure = audio))

    @Then("the player plays the audio {string}")
    fun playsAudio(url: String) {
        val audio = requireNotNull(AUDIO.find(World.response.body())) { "no audio player: ${World.response.body()}" }
        assertTrue(audio.value.contains("src=\"${World.expandFeedServer(url)}\""), audio.value)
        assertTrue(audio.value.contains("preload=\"metadata\""), audio.value)
    }

    @Then("the response contains no audio player")
    fun noAudioPlayer() = assertFalse(AUDIO.containsMatchIn(World.response.body()), World.response.body())

    @Then("the player shows the total time {string}")
    fun showsTotal(total: String) {
        val shown = TOTAL.find(World.response.body())?.groupValues?.get(1)
        assertEquals(total, shown, World.response.body())
    }

    @Then("the player shows the art {string}")
    fun showsArt(url: String) {
        val body = Html.unproxied(World.response.body())
        assertTrue(body.contains("class=\"now-art\" src=\"$url\""), body)
    }

    @Then("the player holds a hidden notice {string} linking to {string}")
    fun hiddenNotice(
        text: String,
        link: String,
    ) {
        val notice = requireNotNull(NOTICE.find(World.response.body())) { "no notice: ${World.response.body()}" }
        assertTrue(Html.unescape(notice.value).contains("<p>$text</p>"), notice.value)
        assertTrue(notice.value.contains("href=\"${World.expandFeedServer(link)}\""), notice.value)
    }

    private companion object {
        val AUDIO = Regex("<audio [^>]*>")
        val TOTAL = Regex("<time class=\"total\">([^<]*)</time>")
        val NOTICE = Regex("<div class=\"player-off audio-failed\" hidden>[\\s\\S]*?</div>")
    }
}
