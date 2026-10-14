package dev.starve.infra

import dev.starve.application.LaterRepository
import dev.starve.application.LaterService
import dev.starve.application.SourceRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/** Wires the read-later use case. */
@Configuration
class LaterConfig {
    @Bean
    fun laterService(
        later: LaterRepository,
        sources: SourceRepository,
        clock: Clock,
    ) = LaterService(later, sources, clock)
}
