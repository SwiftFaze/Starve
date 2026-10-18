package dev.starve.infra

import dev.starve.domain.Platform
import dev.starve.domain.PlatformSite
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlatformConfigTest {
    private val config = PlatformConfig()
    private val hosts = listOf(" Example.COM ", "www.example.com")

    @Test
    fun `each site names its platform, trims and lowercases its hosts and keeps its template`() {
        val trimmed = setOf("example.com", "www.example.com")
        val expected = { platform: Platform -> PlatformSite(platform, trimmed, "t/{key}") }

        assertEquals(expected(Platform.REDDIT), config.redditSite(hosts, "t/{key}"))
        assertEquals(expected(Platform.VIMEO), config.vimeoSite(hosts, "t/{key}"))
        assertEquals(expected(Platform.GITHUB), config.gitHubSite(hosts, "t/{key}"))
        assertEquals(expected(Platform.APPLE_PODCASTS), config.applePodcastsSite(hosts, "t/{key}"))
    }
}
