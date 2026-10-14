package dev.starve.infra

import dev.starve.application.DetailService
import dev.starve.application.ItemRepository
import dev.starve.application.SoundCloudOembed
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/** Wires the detail view: SoundCloud's public oEmbed and the detail use case. */
@Configuration
class DetailConfig {
    @Bean
    fun soundCloudOembed(
        http: BoundedHttp,
        @Value("\${starve.soundcloud.oembed-url:https://soundcloud.com/oembed}") oembedUrl: String,
    ): SoundCloudOembed = SoundCloudOembedClient(http, oembedUrl)

    @Bean
    fun detailService(
        items: ItemRepository,
        soundcloud: SoundCloudOembed,
        clock: Clock,
    ) = DetailService(items, soundcloud, clock)
}
