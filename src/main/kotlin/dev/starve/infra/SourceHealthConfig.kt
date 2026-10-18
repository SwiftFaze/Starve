package dev.starve.infra

import dev.starve.application.SourceHealthService
import dev.starve.application.SourceRepository
import dev.starve.application.SourceStatsRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/** Wires the per-source statistics shown on Sources. */
@Configuration
class SourceHealthConfig {
    @Bean
    fun sourceHealthService(
        sources: SourceRepository,
        stats: SourceStatsRepository,
        clock: Clock,
    ) = SourceHealthService(sources, stats, clock)
}
