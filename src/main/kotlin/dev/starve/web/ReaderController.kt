package dev.starve.web

import dev.starve.application.GroupService
import dev.starve.application.ListPosition
import dev.starve.application.PostRef
import dev.starve.application.ReaderService
import dev.starve.application.TimelineService
import dev.starve.domain.Catalogue
import dev.starve.domain.PostImages
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.server.ResponseStatusException

/**
 * What the reader shows. [body] is stored, already-sanitised HTML with its headings nested under the title
 * ([PostHeadings]); everything else is plain text.
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
    val feedUrl: String? = null,
    val previous: PostLink? = null,
    val next: PostLink? = null,
)

@Controller
class ReaderController(
    private val reader: ReaderService,
    private val timeline: TimelineService,
    private val groups: GroupService,
) {
    /**
     * The reader fragment of a blog post; requesting it marks the item read. [query] is the list the post was opened
     * from, which gives its Previous and Next links.
     */
    @GetMapping("/items/{id}/reader")
    fun reader(
        @CurrentUserId owner: Long,
        @PathVariable id: Long,
        model: Model,
        @ModelAttribute query: TimelineQuery = TimelineQuery(),
    ): String {
        val scope = scopeOf(groups, owner, query)
        val post = reader.open(owner, id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        val nav = postNav(timeline, owner, ListPosition(post.id, post.publishedAt), scope)
        model.addAttribute("post", post.toView(nav))
        model.addAttribute("star", StarPlace.READER.view(post.id, post.favourite))
        model.addAttribute("laterToggle", LaterView("reader-later", post.id, post.later, from = "reader"))
        return "reader :: content"
    }

    private fun PostRef.toView(nav: PostNav) =
        ReaderView(
            Catalogue.number(id),
            title,
            sourceTitle,
            DISPLAY_FORMAT.format(publishedAt),
            publishedAt.toString(),
            body?.let(PostHeadings::underTitle),
            link?.takeIf { WEB_LINK.matches(it) },
            PostImages.headerImage(thumbnailUrl, body),
            faviconUrl,
            sourceUrl?.takeIf { WEB_LINK.matches(it) },
            nav.previous,
            nav.next,
        )

    private companion object {
        // The link comes from the feed, so it is only offered as an href when it is a plain web link.
        val WEB_LINK = Regex("(?i)https?://.*")
    }
}
