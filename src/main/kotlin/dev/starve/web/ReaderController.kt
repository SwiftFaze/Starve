package dev.starve.web

import dev.starve.application.PostRef
import dev.starve.application.ReaderService
import dev.starve.domain.Catalogue
import dev.starve.domain.PostImages
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.server.ResponseStatusException

/**
 * What the reader shows. [body] is stored, already-sanitised HTML; everything else is plain text.
 * [headerImage] is the feed image shown above the body; [siteIcon] is the source's icon, shown small by its name.
 */
data class ReaderView(
    val catalogueNumber: String,
    val title: String,
    val sourceTitle: String,
    val published: String,
    val publishedIso: String,
    val body: String?,
    val link: String?,
    val headerImage: String?,
    val siteIcon: String?,
)

@Controller
class ReaderController(
    private val reader: ReaderService,
) {
    /** The reader fragment of a blog post; requesting it marks the item read. */
    @GetMapping("/items/{id}/reader")
    fun reader(
        @PathVariable id: Long,
        model: Model,
    ): String {
        val post = reader.open(id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        model.addAttribute("post", post.toView())
        model.addAttribute("star", StarView("reader-star", post.id, post.favourite, fromReader = true))
        return "reader :: content"
    }

    private fun PostRef.toView() =
        ReaderView(
            Catalogue.number(id),
            title,
            sourceTitle,
            DISPLAY_FORMAT.format(publishedAt),
            publishedAt.toString(),
            body,
            link?.takeIf { WEB_LINK.matches(it) },
            PostImages.headerImage(thumbnailUrl, body),
            faviconUrl,
        )

    private companion object {
        // The link comes from the feed, so it is only offered as an href when it is a plain web link.
        val WEB_LINK = Regex("(?i)https?://.*")
    }
}
