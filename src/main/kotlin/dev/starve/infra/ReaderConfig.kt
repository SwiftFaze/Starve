package dev.starve.infra

import dev.starve.application.ItemRepository
import dev.starve.application.ReaderService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/** Wires the in-app reader use case. */
@Configuration
class ReaderConfig {
    @Bean
    fun readerService(
        items: ItemRepository,
        clock: Clock,
    ) = ReaderService(items, clock)
}
