package dev.starve.application

import dev.starve.domain.Group
import dev.starve.domain.GroupRules
import dev.starve.domain.GroupSummary
import dev.starve.domain.NameCheck
import dev.starve.domain.Tag
import dev.starve.domain.TagSummary

sealed interface GroupResult {
    data class Saved(
        val group: Group,
    ) : GroupResult

    data object Blank : GroupResult

    data object TooLong : GroupResult

    data object Duplicate : GroupResult

    data object Missing : GroupResult
}

/** The navigation: the total unread count, then each group and each tag (alphabetical) with its own. */
data class GroupNavigation(
    val totalUnread: Int,
    val groups: List<GroupSummary>,
    val tags: List<TagSummary> = emptyList(),
)

/** Groups, and the navigation of groups and tags that every timeline view is chosen from. */
class GroupService(
    private val groups: GroupRepository,
    private val sources: SourceRepository,
    private val tags: TagRepository,
) {
    fun list(owner: Long): List<Group> = groups.findAll(owner)

    fun find(
        owner: Long,
        id: Long,
    ): Group? = groups.findById(owner, id)

    /** [owner]'s tag [id], or null when [owner] has no such tag. */
    fun findTag(
        owner: Long,
        id: Long,
    ): Tag? = tags.findById(owner, id)

    fun navigation(owner: Long): GroupNavigation {
        val perGroup = groups.unreadCounts(owner)
        val perTag = tags.findAll(owner).map { TagSummary(it, tags.unreadCount(owner, it.narrowing)) }
        return GroupNavigation(groups.totalUnread(owner), perGroup, perTag)
    }

    fun create(
        owner: Long,
        rawName: String,
    ): GroupResult =
        withValidName(rawName) { name ->
            if (groups.findByName(owner, name) != null) {
                GroupResult.Duplicate
            } else {
                GroupResult.Saved(groups.insert(owner, name))
            }
        }

    /** Renames [owner]'s group [id]; a case-variant of its own name is allowed, a clash with another is not. */
    fun rename(
        owner: Long,
        id: Long,
        rawName: String,
    ): GroupResult {
        val existing = groups.findById(owner, id) ?: return GroupResult.Missing
        return withValidName(rawName) { name ->
            if (clashesWithOther(owner, id, name)) {
                GroupResult.Duplicate
            } else {
                groups.rename(owner, id, name)
                GroupResult.Saved(existing.copy(name = name))
            }
        }
    }

    /** Deletes [owner]'s group, whose sources become ungrouped; false when [owner] has no such group. */
    fun delete(
        owner: Long,
        id: Long,
    ): Boolean = groups.delete(owner, id)

    /** Puts [owner]'s [sourceId] in [groupId], or ungroups it when null; false when either is not [owner]'s. */
    fun assign(
        owner: Long,
        sourceId: Long,
        groupId: Long?,
    ): Boolean {
        if (groupId != null && groups.findById(owner, groupId) == null) return false
        return sources.assignGroup(owner, sourceId, groupId)
    }

    private fun clashesWithOther(
        owner: Long,
        id: Long,
        name: String,
    ): Boolean {
        val other = groups.findByName(owner, name)
        return other != null && other.id != id
    }

    private fun withValidName(
        rawName: String,
        onValid: (String) -> GroupResult,
    ): GroupResult =
        when (val check = GroupRules.checkName(rawName)) {
            NameCheck.Blank -> GroupResult.Blank
            NameCheck.TooLong -> GroupResult.TooLong
            is NameCheck.Valid -> onValid(check.name)
        }
}
