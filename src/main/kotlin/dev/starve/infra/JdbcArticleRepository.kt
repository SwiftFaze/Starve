package dev.starve.infra

import dev.starve.application.ArticleRepository
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Repository
class JdbcArticleRepository(
    private val jdbc: JdbcClient,
    private val search: SearchIndex,
) : ArticleRepository {
    @Transactional
    override fun recordArticleCheck(
        id: Long,
        body: String?,
        thumbnailUrl: String?,
        at: Instant,
    ) {
        jdbc
            .sql(
                "UPDATE items SET body = COALESCE(?, body), thumbnail_url = COALESCE(thumbnail_url, ?), " +
                    "article_checked_at = ? WHERE id = ?",
            ).params(body, thumbnailUrl, at.toEpochMilli(), id)
            .update()
        // The body is indexed text, so the index row changes in the same transaction.
        if (body != null) search.index(id)
    }
}
