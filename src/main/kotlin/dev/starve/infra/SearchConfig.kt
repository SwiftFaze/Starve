package dev.starve.infra

import dev.starve.application.SearchRepository
import dev.starve.application.SearchService
import dev.starve.application.SourceRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/** Wires the search use case. */
@Configuration
class SearchConfig {
    @Bean
    fun searchService(
        search: SearchRepository,
        sources: SourceRepository,
        clock: Clock,
    ) = SearchService(search, sources, clock)
}
