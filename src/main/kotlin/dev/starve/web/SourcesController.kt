package dev.starve.web

import dev.starve.application.GroupResult
import dev.starve.application.GroupService
import dev.starve.application.SubscribeResult
import dev.starve.application.SubscriptionService
import dev.starve.domain.GroupRules
import dev.starve.domain.Source
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.server.ResponseStatusException
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

data class SourceView(
    val id: Long,
    val title: String,
    val url: String,
    val lastFetched: String?,
    val error: String?,
    val groupId: Long?,
)

@Controller
class SourcesController(
    private val subscriptions: SubscriptionService,
    private val groups: GroupService,
) {
    @GetMapping("/sources")
    fun sources(
        @CurrentUserId owner: Long,
        model: Model,
    ): String = render(owner, model, null)

    @PostMapping("/sources")
    fun add(
        @CurrentUserId owner: Long,
        @RequestParam url: String,
        model: Model,
    ): String = render(owner, model, messageFor(subscriptions.subscribe(owner, url)))

    /** Another user's source is answered like an unknown one: 404. */
    @PostMapping("/sources/{id}/delete")
    fun remove(
        @CurrentUserId owner: Long,
        @PathVariable id: Long,
    ): String {
        if (!subscriptions.unsubscribe(owner, id)) throw notFound()
        return "redirect:/sources"
    }

    @PostMapping("/groups")
    fun createGroup(
        @CurrentUserId owner: Long,
        @RequestParam name: String,
        model: Model,
    ): String = groupOutcome(owner, groups.create(owner, name), model)

    @PostMapping("/groups/{id}/rename")
    fun renameGroup(
        @CurrentUserId owner: Long,
        @PathVariable id: Long,
        @RequestParam(required = false) name: String?,
        model: Model,
    ): String = groupOutcome(owner, groups.rename(owner, id, name.orEmpty()), model)

    @PostMapping("/groups/{id}/delete")
    fun deleteGroup(
        @CurrentUserId owner: Long,
        @PathVariable id: Long,
    ): String {
        if (!groups.delete(owner, id)) throw notFound()
        return "redirect:/sources"
    }

    /** An empty or absent [group] puts the source back in "Ungrouped". */
    @PostMapping("/sources/{id}/group")
    fun assignGroup(
        @CurrentUserId owner: Long,
        @PathVariable id: Long,
        @RequestParam(required = false) group: String?,
    ): String {
        val groupId = group?.takeIf { it.isNotBlank() }?.let { it.toLongOrNull() ?: throw notFound() }
        if (!groups.assign(owner, id, groupId)) throw notFound()
        return "redirect:/sources"
    }

    private fun groupOutcome(
        owner: Long,
        result: GroupResult,
        model: Model,
    ): String =
        when (result) {
            is GroupResult.Saved -> "redirect:/sources"
            GroupResult.Missing -> throw notFound()
            GroupResult.Blank -> render(owner, model, "group name is required")
            GroupResult.TooLong -> render(owner, model, "at most ${GroupRules.MAX_NAME_LENGTH} characters")
            GroupResult.Duplicate -> render(owner, model, "a group with that name already exists")
        }

    private fun render(
        owner: Long,
        model: Model,
        message: String?,
    ): String {
        model.addAttribute("sources", subscriptions.list(owner).map { it.toView() })
        model.addAttribute("groups", groups.list(owner))
        model.addAttribute("message", message)
        return "sources"
    }

    private fun messageFor(result: SubscribeResult): String =
        when (result) {
            is SubscribeResult.Subscribed -> "Subscribed to ${result.source.title}"
            SubscribeResult.NotHttpUrl -> "The feed URL must be an http or https URL"
            SubscribeResult.AlreadySubscribed -> "You are already subscribed to that feed"
            SubscribeResult.NotAFeed -> "That URL is not a valid RSS or Atom feed"
            SubscribeResult.NoFeedFound -> "Cannot subscribe: no feed found at this address"
            SubscribeResult.Unreachable -> "That URL could not be fetched"
        }
}

private val TIME_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC)

private fun notFound() = ResponseStatusException(HttpStatus.NOT_FOUND)

private fun Source.toView(): SourceView {
    val fetched = lastFetchedAt?.let { TIME_FORMAT.format(it) }
    return SourceView(id, title, url, fetched, lastError, groupId)
}
