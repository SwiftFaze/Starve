package dev.starve.infra

import dev.starve.application.EmbedChecker
import dev.starve.application.FeedFetcher
import dev.starve.application.FeedIngestor
import dev.starve.application.FeedLocator
import dev.starve.application.GroupRepository
import dev.starve.application.GroupService
import dev.starve.application.ItemRepository
import dev.starve.application.PlayerService
import dev.starve.application.PollService
import dev.starve.application.RetentionService
import dev.starve.application.SourceRepository
import dev.starve.application.SubscriptionService
import dev.starve.application.TimelineService
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import java.net.http.HttpClient
import java.time.Clock
import java.time.Duration

private const val SOUNDCLOUD_FEED_TEMPLATE = "https://feeds.soundcloud.com/users/soundcloud:users:{id}/sounds.rss"

/** Wires the framework-free application services to their infrastructure adapters. */
@Configuration
class FeedConfig {
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun boundedHttp(
        @Value("\${starve.fetch.timeout:PT10S}") timeout: Duration,
    ) = BoundedHttp(
        HttpClient
            .newBuilder()
            .connectTimeout(timeout)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build(),
        timeout,
        "Starve/0.1",
    )

    @Bean
    fun feedFetcher(http: BoundedHttp): FeedFetcher = RomeFeedFetcher(http)

    @Bean
    fun feedLocator(
        http: BoundedHttp,
        @Value("\${starve.soundcloud.hosts:soundcloud.com,m.soundcloud.com}") hosts: List<String>,
        @Value("\${starve.soundcloud.feed-url-template:$SOUNDCLOUD_FEED_TEMPLATE}") template: String,
    ): FeedLocator = HttpFeedLocator(http, hosts.map { it.trim().lowercase() }.toSet(), template)

    @Bean
    fun embedChecker(
        http: BoundedHttp,
        @Value("\${starve.youtube.oembed-url:https://www.youtube.com/oembed}") oembedUrl: String,
    ): EmbedChecker = YouTubeEmbedChecker(http, oembedUrl)

    @Bean
    fun playerService(
        items: ItemRepository,
        embeds: EmbedChecker,
        clock: Clock,
    ) = PlayerService(items, embeds, clock)

    @Bean
    fun feedIngestor(
        sources: SourceRepository,
        items: ItemRepository,
        clock: Clock,
    ) = FeedIngestor(sources, items, clock)

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
        sources: SourceRepository,
        clock: Clock,
    ) = TimelineService(items, sources, clock)

    @Bean
    fun groupService(
        groups: GroupRepository,
        sources: SourceRepository,
    ) = GroupService(groups, sources)

    @Bean
    fun retentionService(
        items: ItemRepository,
        clock: Clock,
        @Value("\${starve.retention.max-age:P90D}") maxAge: Duration,
    ) = RetentionService(items, clock, maxAge)
}

/** Runs polling and retention in the background; switched off in acceptance tests. */
@Configuration
@EnableScheduling
@ConditionalOnProperty("starve.scheduling.enabled", matchIfMissing = true)
class SchedulingConfig(
    private val pollService: PollService,
    private val retentionService: RetentionService,
) {
    @Scheduled(
        initialDelayString = "\${starve.poll.interval:PT30M}",
        fixedDelayString = "\${starve.poll.interval:PT30M}",
    )
    fun poll() = pollService.pollAll()

    @Scheduled(
        initialDelayString = "PT1M",
        fixedDelayString = "\${starve.retention.interval:PT24H}",
    )
    fun purge() {
        retentionService.purgeOld()
    }
}
