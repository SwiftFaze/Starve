package dev.starve.infra

import dev.starve.application.GroupRepository
import dev.starve.domain.Group
import dev.starve.domain.GroupSummary
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet

@Repository
class JdbcGroupRepository(
    private val jdbc: JdbcClient,
) : GroupRepository {
    override fun findAll(): List<Group> =
        jdbc
            .sql("SELECT id, name FROM groups ORDER BY name COLLATE NOCASE, id")
            .query { rs, _ -> rs.toGroup() }
            .list()

    override fun findById(id: Long): Group? =
        jdbc
            .sql("SELECT id, name FROM groups WHERE id = ?")
            .param(id)
            .query { rs, _ -> rs.toGroup() }
            .optional()
            .orElse(null)

    override fun findByName(name: String): Group? =
        jdbc
            .sql("SELECT id, name FROM groups WHERE name = ? COLLATE NOCASE")
            .param(name)
            .query { rs, _ -> rs.toGroup() }
            .optional()
            .orElse(null)

    override fun insert(name: String): Group {
        val id =
            jdbc
                .sql("INSERT INTO groups (name) VALUES (?) RETURNING id")
                .param(name)
                .query(Long::class.java)
                .single()
        return Group(id, name)
    }

    override fun rename(
        id: Long,
        name: String,
    ) {
        jdbc.sql("UPDATE groups SET name = ? WHERE id = ?").params(name, id).update()
    }

    override fun delete(id: Long) {
        jdbc.sql("DELETE FROM groups WHERE id = ?").param(id).update()
    }

    override fun unreadCounts(): List<GroupSummary> =
        jdbc
            .sql(
                "SELECT g.id, g.name, COUNT(i.id) AS unread FROM groups g " +
                    "LEFT JOIN sources s ON s.group_id = g.id " +
                    "LEFT JOIN items i ON i.source_id = s.id AND i.read_at IS NULL " +
                    "GROUP BY g.id, g.name ORDER BY g.name COLLATE NOCASE, g.id",
            ).query { rs, _ -> GroupSummary(rs.toGroup(), rs.getInt("unread")) }
            .list()

    override fun totalUnread(): Int =
        jdbc
            .sql("SELECT COUNT(*) FROM items WHERE read_at IS NULL")
            .query(Int::class.java)
            .single()

    private fun ResultSet.toGroup() = Group(getLong("id"), getString("name"))
}
