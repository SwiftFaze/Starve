package dev.starve.infra

import dev.starve.application.FavouriteRepository
import dev.starve.application.FavouriteService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/** Wires the favourite toggle use case. */
@Configuration
class FavouriteConfig {
    @Bean
    fun favouriteService(
        favourites: FavouriteRepository,
        clock: Clock,
    ) = FavouriteService(favourites, clock)
}
