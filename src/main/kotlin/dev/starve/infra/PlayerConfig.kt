package dev.starve.infra

import dev.starve.application.EmbedChecker
import dev.starve.application.ItemRepository
import dev.starve.application.PlayerService
import dev.starve.application.PositionRepository
import dev.starve.application.ResumeService
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/** Wires inline playback: the YouTube embedding check, the player use case and resuming where it stopped. */
@Configuration
class PlayerConfig {
    @Bean
    fun embedChecker(
        http: BoundedHttp,
        @Value("\${starve.youtube.oembed-url:https://www.youtube.com/oembed}") oembedUrl: String,
    ): EmbedChecker = YouTubeEmbedChecker(http, oembedUrl)

    @Bean
    fun playerService(
        items: ItemRepository,
        embeds: EmbedChecker,
        clock: Clock,
    ) = PlayerService(items, embeds, clock)

    @Bean
    fun resumeService(positions: PositionRepository) = ResumeService(positions)
}
