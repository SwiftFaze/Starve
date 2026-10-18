package dev.starve.web

import dev.starve.application.GroupResult
import dev.starve.application.GroupService
import dev.starve.application.SubscribeResult
import dev.starve.application.SubscriptionService
import dev.starve.domain.GroupRules
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.server.ResponseStatusException

@Controller
class SourcesController(
    private val subscriptions: SubscriptionService,
    private val groups: GroupService,
    private val roster: SourceRoster,
) {
    /**
     * The roster. [remove] or [deleteGroup] names a row to show in its confirming state, which holds the form that
     * really removes it: the first click only asks. A tag's is [TagController]'s.
     */
    @GetMapping("/sources")
    fun sources(
        @CurrentUserId owner: Long,
        model: Model,
        @RequestParam(required = false) remove: Long? = null,
        @RequestParam(name = "delete-group", required = false) deleteGroup: Long? = null,
    ): String {
        model.addAttribute("removing", remove)
        model.addAttribute("deletingGroup", deleteGroup)
        return render(owner, model, null)
    }

    /** A rejected [url] comes back in its field, with the reason under it. */
    @PostMapping("/sources")
    fun add(
        @CurrentUserId owner: Long,
        @RequestParam url: String,
        model: Model,
    ): String {
        val result = subscriptions.subscribe(owner, url)
        if (result is SubscribeResult.Subscribed) return render(owner, model, messageFor(result))
        model.addAttribute("url", url)
        model.addAttribute("addError", messageFor(result))
        return render(owner, model, null)
    }

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
            GroupResult.Blank -> render(owner, model, "group name is required", error = true)
            GroupResult.TooLong -> render(owner, model, TOO_LONG, error = true)
            GroupResult.Duplicate -> render(owner, model, "a group with that name already exists", error = true)
        }

    private fun render(
        owner: Long,
        model: Model,
        message: String?,
        error: Boolean = false,
    ): String = roster.page(model, owner, message, error)

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

private fun notFound() = ResponseStatusException(HttpStatus.NOT_FOUND)

private const val TOO_LONG = "at most ${GroupRules.MAX_NAME_LENGTH} characters"
