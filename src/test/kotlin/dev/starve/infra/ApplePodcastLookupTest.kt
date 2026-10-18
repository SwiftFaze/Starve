package dev.starve.infra

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ApplePodcastLookupTest {
    @Test
    fun `the first result with a feed url names the feed`() {
        val json =
            """{"resultCount":2,"results":[{"kind":"podcast"},{"feedUrl":" https://feeds.test/show.xml "},""" +
                """{"feedUrl":"https://feeds.test/other.xml"}]}"""

        assertEquals("https://feeds.test/show.xml", ApplePodcastLookup.feedUrl(json))
    }

    @Test
    fun `no results, a non-http feed url or text that is not json name no feed`() {
        assertNull(ApplePodcastLookup.feedUrl("""{"resultCount":0,"results":[]}"""))
        assertNull(ApplePodcastLookup.feedUrl("""{"results":[{"feedUrl":"ftp://feeds.test/show.xml"}]}"""))
        assertNull(ApplePodcastLookup.feedUrl("""{"results":[{"feedUrl":42}]}"""))
        assertNull(ApplePodcastLookup.feedUrl("""{"feedUrl":"https://feeds.test/show.xml"}"""))
        assertNull(ApplePodcastLookup.feedUrl("<html>"))
    }
}
