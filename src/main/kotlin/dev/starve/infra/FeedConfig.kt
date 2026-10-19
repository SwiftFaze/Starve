package dev.starve.infra

import dev.starve.application.FaviconService
import dev.starve.application.FavouriteRepository
import dev.starve.application.FeedFetcher
import dev.starve.application.FeedIngestor
import dev.starve.application.FeedLocator
import dev.starve.application.GroupRepository
import dev.starve.application.GroupService
import dev.starve.application.ImageCacheService
import dev.starve.application.ItemRepository
import dev.starve.application.PollService
import dev.starve.application.RetentionService
import dev.starve.application.SourceRepository
import dev.starve.application.SubscriptionService
import dev.starve.application.TagRepository
import dev.starve.application.TimelineService
import dev.starve.domain.PlatformSite
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import java.net.http.HttpClient
import java.time.Clock
import java.time.Duration

private const val SOUNDCLOUD_FEED_TEMPLATE = "https://feeds.soundcloud.com/users/soundcloud:users:{id}/sounds.rss"

private fun boundedHttp(
    timeout: Duration,
    redirects: HttpClient.Redirect,
) = BoundedHttp(
    HttpClient
        .newBuilder()
        .connectTimeout(timeout)
        .followRedirects(redirects)
        .build(),
    timeout,
    "Starve/0.1",
)

/** Wires the framework-free application services to their infrastructure adapters. */
@Configuration
class FeedConfig {
    // Overridable, so the acceptance harness can drive time (the login block lifts after 15 minutes).
    @Bean
    @ConditionalOnMissingBean(Clock::class)
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun boundedHttp(
        @Value("\${starve.fetch.timeout:PT10S}") timeout: Duration,
    ) = boundedHttp(timeout, HttpClient.Redirect.NORMAL)

    /** Feed polls see every redirect themselves, so that a feed that moved for good gets its new URL stored. */
    @Bean
    fun feedFetcher(
        @Value("\${starve.fetch.timeout:PT10S}") timeout: Duration,
    ): FeedFetcher = RomeFeedFetcher(boundedHttp(timeout, HttpClient.Redirect.NEVER))

    @Bean
    fun feedLocator(
        http: BoundedHttp,
        @Value("\${starve.soundcloud.hosts:soundcloud.com,m.soundcloud.com}") hosts: List<String>,
        @Value("\${starve.soundcloud.feed-url-template:$SOUNDCLOUD_FEED_TEMPLATE}") template: String,
        platforms: List<PlatformSite>,
    ): FeedLocator = HttpFeedLocator(http, hosts.map { it.trim().lowercase() }.toSet(), template, platforms)

    @Bean
    fun feedIngestor(
        sources: SourceRepository,
        items: ItemRepository,
        clock: Clock,
        favicons: FaviconService,
    ) = FeedIngestor(sources, items, clock, favicons)

    @Bean
    fun pollService(
        sources: SourceRepository,
        fetcher: FeedFetcher,
        ingestor: FeedIngestor,
    ) = PollService(sources, fetcher, ingestor)

    @Bean
    fun subscriptionService(
        sources: SourceRepository,
        locator: FeedLocator,
        fetcher: FeedFetcher,
        ingestor: FeedIngestor,
    ) = SubscriptionService(sources, locator, fetcher, ingestor)

    @Bean
    fun timelineService(
        items: ItemRepository,
        favourites: FavouriteRepository,
        sources: SourceRepository,
        clock: Clock,
    ) = TimelineService(items, favourites, sources, clock)

    @Bean
    fun groupService(
        groups: GroupRepository,
        sources: SourceRepository,
        tags: TagRepository,
    ) = GroupService(groups, sources, tags)

    @Bean
    fun retentionService(
        items: ItemRepository,
        clock: Clock,
        @Value("\${starve.retention.max-age:P90D}") maxAge: Duration,
    ) = RetentionService(items, clock, maxAge)
}

/**
 * Runs polling, retention and the image cache's prune in the background; switched off in acceptance tests. Every
 * tick polls the sources that are due, each on its own interval or `starve.poll.interval`.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty("starve.scheduling.enabled", matchIfMissing = true)
class SchedulingConfig(
    private val pollService: PollService,
    private val retentionService: RetentionService,
    private val imageCacheService: ImageCacheService,
    private val clock: Clock,
    @Value("\${starve.poll.interval:PT30M}") private val defaultInterval: Duration,
) {
    @Scheduled(
        initialDelayString = "\${starve.poll.tick:PT1M}",
        fixedDelayString = "\${starve.poll.tick:PT1M}",
    )
    fun poll() = pollService.pollDue(clock.instant(), defaultInterval)

    @Scheduled(
        initialDelayString = "PT1M",
        fixedDelayString = "\${starve.retention.interval:PT24H}",
    )
    fun purge() {
        retentionService.purgeOld()
        imageCacheService.prune()
    }
}
