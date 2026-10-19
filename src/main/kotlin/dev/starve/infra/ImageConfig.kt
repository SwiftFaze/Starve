package dev.starve.infra

import dev.starve.application.AppMetadata
import dev.starve.application.HtmlLinks
import dev.starve.application.ImageCache
import dev.starve.application.ImageCacheService
import dev.starve.application.ImageProxyService
import dev.starve.application.ImageSigner
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.util.unit.DataSize
import java.net.http.HttpClient
import java.nio.file.Path
import java.time.Duration

/**
 * Wires the image proxy: its signer, the fetcher that refuses the home network, and the cache in an `images` folder
 * beside the database, kept to what stored items and sources show and to `starve.images.cache-max-size`.
 */
@Configuration
class ImageConfig(
    @Value("\${starve.db.path:./data/starve.db}") private val dbPath: String,
    @Value("\${starve.images.cache-max-size:500MB}") private val maxSize: DataSize,
    @Value("\${starve.images.trusted-hosts:}") private val trustedHosts: List<String>,
) {
    @Bean
    fun imageSigner(metadata: AppMetadata) = ImageSigner.stored(metadata)

    @Bean
    fun imageCache(): ImageCache =
        DiskImageCache(
            Path
                .of(dbPath)
                .toAbsolutePath()
                .parent
                .resolve("images"),
        )

    @Bean
    fun imageProxyService(cache: ImageCache): ImageProxyService {
        val timeout = Duration.ofSeconds(TIMEOUT_SECONDS)
        val client =
            HttpClient
                .newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build()
        return ImageProxyService(HttpImageFetcher(BoundedHttp(client, timeout, "Starve/0.1"), trusted()), cache)
    }

    @Bean
    fun imageCacheService(
        cache: ImageCache,
        jdbc: JdbcClient,
    ) = ImageCacheService(cache, JdbcImageReferences(jdbc), maxSize.toBytes())

    @Bean
    fun htmlLinks(): HtmlLinks = JsoupHtmlLinks

    /** Hosts fetched even though they are local; set only by tests, whose feed server is on localhost. */
    private fun trusted() = trustedHosts.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()

    private companion object {
        const val TIMEOUT_SECONDS = 5L
    }
}
