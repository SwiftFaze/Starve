package dev.starve.infra

import dev.starve.application.FeedFetcher
import dev.starve.application.FeedIngestor
import dev.starve.application.ItemRepository
import dev.starve.application.PollService
import dev.starve.application.RetentionService
import dev.starve.application.SourceRepository
import dev.starve.application.SubscriptionService
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import java.net.http.HttpClient
import java.time.Clock
import java.time.Duration

/** Wires the framework-free application services to their infrastructure adapters. */
@Configuration
class FeedConfig {
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun feedFetcher(
        @Value("\${starve.fetch.timeout:PT10S}") timeout: Duration,
    ): FeedFetcher =
        RomeFeedFetcher(
            HttpClient
                .newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build(),
            timeout,
            "Starve/0.1",
        )

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
        fetcher: FeedFetcher,
        ingestor: FeedIngestor,
    ) = SubscriptionService(sources, fetcher, ingestor)

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
