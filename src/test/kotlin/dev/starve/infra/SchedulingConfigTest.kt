package dev.starve.infra

import dev.starve.application.FeedFetcher
import dev.starve.application.FeedIngestor
import dev.starve.application.ImageCache
import dev.starve.application.ImageCacheService
import dev.starve.application.ItemRepository
import dev.starve.application.PollService
import dev.starve.application.RetentionService
import dev.starve.application.SourceRepository
import dev.starve.domain.Source
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.boot.convert.ApplicationConversionService
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.time.Clock
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

class SchedulingConfigTest {
    private val polls = AtomicInteger()

    private val runner =
        ApplicationContextRunner()
            .withInitializer { it.beanFactory.conversionService = ApplicationConversionService.getSharedInstance() }
            .withBean(SchedulingConfig::class.java)
            .withBean(Clock::class.java, { Clock.systemUTC() })
            .withBean(PollService::class.java, { pollService() })
            .withBean(
                RetentionService::class.java,
                { RetentionService(mock(ItemRepository::class.java), Clock.systemUTC(), Duration.ofDays(1)) },
            ).withBean(
                ImageCacheService::class.java,
                { ImageCacheService(mock(ImageCache::class.java), { emptySet() }, 0) },
            ).withPropertyValues("starve.poll.tick=PT0.05S")

    @Test
    fun `ticks on the configured tick when scheduling is enabled`() {
        runner.run {
            await().atMost(Duration.ofSeconds(5)).until { polls.get() >= 2 }
        }
    }

    @Test
    fun `never polls when scheduling is switched off`() {
        runner.withPropertyValues("starve.scheduling.enabled=false").run {
            assertEquals(0, it.getBeanNamesForType(SchedulingConfig::class.java).size)
            assertEquals(0, polls.get())
        }
    }

    private fun pollService(): PollService {
        val sources = mock(SourceRepository::class.java)
        `when`(sources.findAll()).thenAnswer {
            polls.incrementAndGet()
            emptyList<Source>()
        }
        return PollService(sources, mock(FeedFetcher::class.java), mock(FeedIngestor::class.java))
    }
}
