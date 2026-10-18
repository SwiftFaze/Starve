package dev.starve.infra

import dev.starve.domain.Platform
import dev.starve.domain.PlatformSite
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

private const val REDDIT_HOSTS = "reddit.com,www.reddit.com,old.reddit.com"
private const val REDDIT_FEED = "https://www.reddit.com/r/{key}/.rss"
private const val VIMEO_HOSTS = "vimeo.com,www.vimeo.com"
private const val VIMEO_FEED = "https://vimeo.com/{key}/videos/rss"
private const val GITHUB_HOSTS = "github.com,www.github.com"
private const val GITHUB_FEED = "https://github.com/{key}.atom"
private const val APPLE_HOSTS = "podcasts.apple.com"
private const val APPLE_LOOKUP = "https://itunes.apple.com/lookup?id={key}"

/** The platforms Smart-add and import resolve by URL rule; each one's hosts and feed address are configurable. */
@Configuration
class PlatformConfig {
    @Bean
    fun redditSite(
        @Value("\${starve.reddit.hosts:$REDDIT_HOSTS}") hosts: List<String>,
        @Value("\${starve.reddit.feed-url-template:$REDDIT_FEED}") template: String,
    ) = site(Platform.REDDIT, hosts, template)

    @Bean
    fun vimeoSite(
        @Value("\${starve.vimeo.hosts:$VIMEO_HOSTS}") hosts: List<String>,
        @Value("\${starve.vimeo.feed-url-template:$VIMEO_FEED}") template: String,
    ) = site(Platform.VIMEO, hosts, template)

    @Bean
    fun gitHubSite(
        @Value("\${starve.github.hosts:$GITHUB_HOSTS}") hosts: List<String>,
        @Value("\${starve.github.feed-url-template:$GITHUB_FEED}") template: String,
    ) = site(Platform.GITHUB, hosts, template)

    @Bean
    fun applePodcastsSite(
        @Value("\${starve.apple-podcasts.hosts:$APPLE_HOSTS}") hosts: List<String>,
        @Value("\${starve.apple-podcasts.lookup-url-template:$APPLE_LOOKUP}") template: String,
    ) = site(Platform.APPLE_PODCASTS, hosts, template)

    private fun site(
        platform: Platform,
        hosts: List<String>,
        template: String,
    ) = PlatformSite(platform, hosts.map { it.trim().lowercase() }.toSet(), template)
}
