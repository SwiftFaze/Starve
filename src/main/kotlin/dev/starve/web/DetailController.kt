package dev.starve.web

import dev.starve.application.DetailService
import dev.starve.application.GroupService
import dev.starve.application.ItemDetail
import dev.starve.application.ListPosition
import dev.starve.application.TimelineService
import dev.starve.domain.Catalogue
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.server.ResponseStatusException

/** What the detail view shows. [description] is stored, already-sanitised HTML; everything else is plain text. */
data class DetailView(
    val id: Long,
    val catalogueNumber: String,
    val title: String,
    val sourceTitle: String,
    val published: String,
    val publishedIso: String,
    val artist: String?,
    val thumbnailUrl: String?,
    val description: String?,
    val link: String?,
    val feedUrl: String? = null,
    val previous: PostLink? = null,
    val next: PostLink? = null,
)

@Controller
class DetailController(
    private val details: DetailService,
    private val timeline: TimelineService,
    private val groups: GroupService,
) {
    /**
     * The detail view fragment of a SoundCloud track or YouTube video; requesting it marks the item read. [query] is
     * the list the item was opened from, which gives its Previous and Next links.
     */
    @AnnouncesRead
    @GetMapping("/items/{id}/detail")
    fun detail(
        @CurrentUserId owner: Long,
        @PathVariable id: Long,
        model: Model,
        @ModelAttribute query: TimelineQuery = TimelineQuery(),
    ): String {
        val scope = scopeOf(groups, owner, query)
        val detail = details.open(owner, id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        val nav = postNav(timeline, owner, ListPosition(detail.id, detail.publishedAt), scope)
        model.addAttribute("detail", detail.toView(nav))
        model.addAttribute("star", StarPlace.DETAIL.view(detail.id, detail.favourite))
        model.addAttribute("laterToggle", LaterView("detail-later", detail.id, detail.later, from = "detail"))
        return "detail :: content"
    }

    private fun ItemDetail.toView(nav: PostNav) =
        DetailView(
            id,
            Catalogue.number(id),
            title,
            sourceTitle,
            DISPLAY_FORMAT.format(publishedAt),
            publishedAt.toString(),
            artist,
            thumbnailUrl,
            description,
            link,
            sourceUrl?.takeIf { FEED_LINK.matches(it) },
            nav.previous,
            nav.next,
        )

    private companion object {
        // The feed URL is stored as the user gave it, so it is only offered as an href when it is a plain web link.
        val FEED_LINK = Regex("(?i)https?://.*")
    }
}
