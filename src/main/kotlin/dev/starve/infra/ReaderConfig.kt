package dev.starve.infra

import dev.starve.application.ArticleFetcher
import dev.starve.application.ItemRepository
import dev.starve.application.ReaderService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.net.http.HttpClient
import java.time.Clock
import java.time.Duration

/** Wires the in-app reader use case and the article fetch it may run. */
@Configuration
class ReaderConfig {
    @Bean
    fun readerService(
        items: ItemRepository,
        clock: Clock,
        articles: ArticleFetcher,
    ) = ReaderService(items, clock, articles, JsoupText)

    @Bean
    fun articleFetcher(): ArticleFetcher {
        val timeout = Duration.ofSeconds(ARTICLE_TIMEOUT_SECONDS)
        val client =
            HttpClient
                .newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build()
        return HttpArticleFetcher(BoundedHttp(client, timeout, "Starve/0.1"))
    }

    private companion object {
        const val ARTICLE_TIMEOUT_SECONDS = 5L
    }
}
