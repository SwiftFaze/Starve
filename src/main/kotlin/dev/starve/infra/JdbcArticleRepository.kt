package dev.starve.infra

import dev.starve.application.ArticleRepository
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class JdbcArticleRepository(
    private val jdbc: JdbcClient,
) : ArticleRepository {
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
    }
}
