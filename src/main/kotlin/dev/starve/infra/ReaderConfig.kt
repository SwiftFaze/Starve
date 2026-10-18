package dev.starve.infra

import dev.starve.application.ArticleFetcher
import dev.starve.application.ArticleRepository
import dev.starve.application.ItemRepository
import dev.starve.application.ReaderService
import dev.starve.domain.FeedOnlyHosts
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.net.http.HttpClient
import java.time.Clock
import java.time.Duration

private const val FEED_ONLY_HOSTS = "github.com,www.github.com,bsky.app"

/** Wires the in-app reader use case and the article fetch it may run, skipping the feed-only hosts. */
@Configuration
class ReaderConfig(
    @Value("\${starve.reader.feed-only-hosts:$FEED_ONLY_HOSTS}") private val feedOnlyHosts: List<String>,
) {
    @Bean
    fun readerService(
        items: ItemRepository,
        checks: ArticleRepository,
        clock: Clock,
        articles: ArticleFetcher,
    ) = ReaderService(items, checks, clock, articles, FeedOnlyHosts(feedOnlyHosts), JsoupText)

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
