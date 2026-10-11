package dev.starve.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class PlayableTest {
    private val id = "dQw4w9WgXcQ"

    @ParameterizedTest
    @ValueSource(
        strings = [
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://youtube.com/watch?v=dQw4w9WgXcQ",
            "http://m.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://music.youtube.com/watch?v=dQw4w9WgXcQ&list=x",
            "https://www.youtube.com/watch?list=x&v=dQw4w9WgXcQ",
            "https://youtu.be/dQw4w9WgXcQ",
            "https://youtu.be/dQw4w9WgXcQ?t=5",
            "https://www.youtube.com/shorts/dQw4w9WgXcQ",
            "https://WWW.YouTube.com/watch?v=dQw4w9WgXcQ",
        ],
    )
    fun `youtube links are recognised with their video id`(link: String) {
        assertEquals(Playable.YouTube(id), Playable.of(link))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "https://www.youtube.com/watch?v=short",
            "https://www.youtube.com/watch?v=dQw4w9WgXcQx",
            "https://www.youtube.com/watch?v=dQw4w9Wg!cQ",
            "https://www.youtube.com/watch",
            "https://www.youtube.com/watch?x=dQw4w9WgXcQ",
            "https://www.youtube.com/channel/dQw4w9WgXcQ",
            "https://www.youtube.com/shorts/short",
            "https://www.youtube.com/shorts/dQw4w9WgXcQ/more",
            "https://youtu.be/",
            "https://youtu.be/short",
            "https://youtu.be/dQw4w9WgXcQ/more",
            "https://evil.com/watch?v=dQw4w9WgXcQ",
            "https://notyoutube.com/watch?v=dQw4w9WgXcQ",
            "https://example.com/story",
            "ftp://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "not a url",
            "",
        ],
    )
    fun `other links are not playable`(link: String) {
        assertNull(Playable.of(link))
    }

    @Test
    fun `a missing link is not playable`() {
        assertNull(Playable.of(null))
    }

    @Test
    fun `soundcloud links are recognised`() {
        val link = "https://soundcloud.com/some-artist/some-track"
        assertEquals(Playable.SoundCloud(link), Playable.of(link))
        val www = "https://www.soundcloud.com/a/b"
        assertEquals(Playable.SoundCloud(www), Playable.of(www))
    }

    @Test
    fun `lookalike soundcloud hosts are not playable`() {
        assertNull(Playable.of("https://evilsoundcloud.com/a/b"))
        assertNull(Playable.of("https://soundcloud.com.evil.test/a/b"))
        assertNull(Playable.of("mailto:soundcloud.com"))
    }

    @Test
    fun `youtube urls derive from the video id`() {
        val video = Playable.YouTube(id)
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", video.watchUrl)
        assertEquals("https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ?autoplay=1", video.embedUrl)
    }

    @Test
    fun `the soundcloud widget url encodes the track link`() {
        val track = Playable.SoundCloud("https://soundcloud.com/some-artist/some-track")
        assertEquals(
            "https://w.soundcloud.com/player/" +
                "?url=https%3A%2F%2Fsoundcloud.com%2Fsome-artist%2Fsome-track&auto_play=true",
            track.embedUrl,
        )
    }
}
