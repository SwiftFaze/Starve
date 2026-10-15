package dev.starve.web

import dev.starve.application.DetailService
import dev.starve.application.ItemDetail
import dev.starve.domain.Catalogue
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
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
)

@Controller
class DetailController(
    private val details: DetailService,
) {
    /** The detail view fragment of a SoundCloud track or YouTube video; requesting it marks the item read. */
    @GetMapping("/items/{id}/detail")
    fun detail(
        @CurrentUserId owner: Long,
        @PathVariable id: Long,
        model: Model,
    ): String {
        val detail = details.open(owner, id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        model.addAttribute("detail", detail.toView())
        model.addAttribute("laterToggle", LaterView("detail-later", detail.id, detail.later, from = "detail"))
        return "detail :: content"
    }

    private fun ItemDetail.toView() =
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
        )
}
