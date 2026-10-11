package dev.starve.application

import dev.starve.domain.Group
import dev.starve.domain.GroupRules
import dev.starve.domain.GroupSummary
import dev.starve.domain.NameCheck

sealed interface GroupResult {
    data class Saved(
        val group: Group,
    ) : GroupResult

    data object Blank : GroupResult

    data object TooLong : GroupResult

    data object Duplicate : GroupResult

    data object Missing : GroupResult
}

/** The group navigation: the total unread count and each group (alphabetical) with its own. */
data class GroupNavigation(
    val totalUnread: Int,
    val groups: List<GroupSummary>,
)

class GroupService(
    private val groups: GroupRepository,
    private val sources: SourceRepository,
) {
    fun list(): List<Group> = groups.findAll()

    fun find(id: Long): Group? = groups.findById(id)

    fun navigation(): GroupNavigation = GroupNavigation(groups.totalUnread(), groups.unreadCounts())

    fun create(rawName: String): GroupResult =
        withValidName(rawName) { name ->
            if (groups.findByName(name) != null) GroupResult.Duplicate else GroupResult.Saved(groups.insert(name))
        }

    /** Renames group [id]; a case-variant of its own name is allowed, a clash with another group is not. */
    fun rename(
        id: Long,
        rawName: String,
    ): GroupResult {
        val existing = groups.findById(id) ?: return GroupResult.Missing
        return withValidName(rawName) { name ->
            if (clashesWithOther(id, name)) {
                GroupResult.Duplicate
            } else {
                groups.rename(id, name)
                GroupResult.Saved(existing.copy(name = name))
            }
        }
    }

    /** Deletes the group; its sources become ungrouped. */
    fun delete(id: Long) = groups.delete(id)

    /** Puts [sourceId] in [groupId], or ungroups it when null; false when the group does not exist. */
    fun assign(
        sourceId: Long,
        groupId: Long?,
    ): Boolean {
        if (groupId != null && groups.findById(groupId) == null) return false
        sources.assignGroup(sourceId, groupId)
        return true
    }

    private fun clashesWithOther(
        id: Long,
        name: String,
    ): Boolean {
        val other = groups.findByName(name)
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
