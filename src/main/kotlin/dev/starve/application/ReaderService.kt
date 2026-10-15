package dev.starve.application

import dev.starve.domain.ArticleRules
import dev.starve.domain.FeedRules
import dev.starve.domain.Playable
import java.time.Clock
import java.time.Instant

class ReaderService(
    private val items: ItemRepository,
    private val checks: ArticleRepository,
    private val clock: Clock,
    private val articles: ArticleFetcher,
    private val text: HtmlText,
) {
    /**
     * The post of [owner]'s item to show in the reader, marked read; null for an unknown or another user's item and
     * for a playable one, which keeps opening through the player. A teaser-only post first has its article page
     * fetched.
     */
    fun open(
        owner: Long,
        id: Long,
    ): PostRef? {
        val post = items.findPost(owner, id)
        if (post == null || Playable.of(post.link) != null) return null
        val now = clock.instant()
        val shown = withArticle(post, now)
        items.markRead(owner, id, now)
        return shown
    }

    /** The post with its article fetched when it is a teaser that was not checked within the recheck period. */
    private fun withArticle(
        post: PostRef,
        now: Instant,
    ): PostRef {
        val link = post.link
        return if (link != null && needsArticle(post, link, now)) fetchArticle(post, link, now) else post
    }

    private fun needsArticle(
        post: PostRef,
        link: String,
        now: Instant,
    ): Boolean =
        FeedRules.isHttpUrl(link) &&
            ArticleRules.isDue(post.articleCheckedAt, now) &&
            ArticleRules.isTeaser(textLength(post.body))

    private fun fetchArticle(
        post: PostRef,
        link: String,
        now: Instant,
    ): PostRef {
        val page = articles.fetch(link)
        // The teaser stays unless the extracted article text is longer than it.
        val body = page?.body?.takeIf { textLength(it) > textLength(post.body) }
        val image = page?.imageUrl
        checks.recordArticleCheck(post.id, body, image, now)
        return post.copy(
            body = body ?: post.body,
            thumbnailUrl = post.thumbnailUrl ?: image,
            articleCheckedAt = now,
        )
    }

    private fun textLength(html: String?): Int = html?.let { text.plainText(it).length } ?: 0
}
