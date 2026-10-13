package dev.starve.infra

import dev.starve.application.FavouriteService
import dev.starve.application.ItemRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/** Wires the favourite toggle use case. */
@Configuration
class FavouriteConfig {
    @Bean
    fun favouriteService(
        items: ItemRepository,
        clock: Clock,
    ) = FavouriteService(items, clock)
}
