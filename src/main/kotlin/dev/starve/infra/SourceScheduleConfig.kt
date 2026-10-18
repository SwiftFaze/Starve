package dev.starve.infra

import dev.starve.application.BackgroundPoller
import dev.starve.application.SourceRepository
import dev.starve.application.SourceScheduleRepository
import dev.starve.application.SourceScheduleService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** Wires the per-source poll interval and Fetch now. */
@Configuration
class SourceScheduleConfig {
    @Bean
    fun sourceScheduleService(
        schedule: SourceScheduleRepository,
        sources: SourceRepository,
        background: BackgroundPoller,
    ) = SourceScheduleService(schedule, sources, background)
}
