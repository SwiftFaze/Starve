package dev.starve.infra

import dev.starve.application.FaviconFinder
import dev.starve.application.FaviconService
import dev.starve.application.SourceRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** Wires the site-icon lookup: an HTTP finder, and the service that stores what it finds. */
@Configuration
class FaviconConfig {
    @Bean
    fun faviconFinder(http: BoundedHttp): FaviconFinder = HttpFaviconFinder(http)

    @Bean
    fun faviconService(
        sources: SourceRepository,
        finder: FaviconFinder,
    ) = FaviconService(sources, finder)
}
