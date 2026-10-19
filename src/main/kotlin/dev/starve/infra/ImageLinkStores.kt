package dev.starve.infra

import dev.starve.application.HtmlLinks
import dev.starve.application.ImageReferences
import org.jsoup.Jsoup
import org.springframework.jdbc.core.simple.JdbcClient

/** The image URLs stored items and sources refer to, read for the cache's daily prune. */
class JdbcImageReferences(
    private val jdbc: JdbcClient,
) : ImageReferences {
    override fun all(): Set<String> {
        val stored =
            jdbc
                .sql(
                    "SELECT thumbnail_url FROM items WHERE thumbnail_url IS NOT NULL " +
                        "UNION SELECT favicon_url FROM sources WHERE favicon_url IS NOT NULL",
                ).query(String::class.java)
                .list()
        val inBodies =
            jdbc
                .sql("SELECT body FROM items WHERE body LIKE '%<img%'")
                .query(String::class.java)
                .list()
                .flatMap { body -> Jsoup.parseBodyFragment(body).select("img[src]").map { it.attr("src") } }
        return (stored + inBodies).toSet()
    }
}

/** Rewrites stored post HTML with jsoup: every image's `src` and every link's `href`, as [rewrite]'s caller says. */
object JsoupHtmlLinks : HtmlLinks {
    override fun rewrite(
        html: String,
        image: (String) -> String,
        link: (String) -> String,
    ): String {
        val body = Jsoup.parseBodyFragment(html).body()
        body.select("img[src]").forEach { it.attr("src", image(it.attr("src"))) }
        body.select("a[href]").forEach { it.attr("href", link(it.attr("href"))) }
        return body.html()
    }
}
