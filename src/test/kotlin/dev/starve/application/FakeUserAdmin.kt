package dev.starve.application

import dev.starve.domain.User
import dev.starve.domain.UserAccount
import java.time.Instant

/** In-memory accounts for the account service tests; the hash of a password is "hashed:<password>". */
class FakeUserAdmin(
    vararg names: String,
) : UserAdminRepository,
    UserRepository {
    val rows = mutableListOf<User>()

    init {
        names.forEachIndexed { index, name -> rows += User(index + 1L, name, "hashed:pw-$name", admin = index == 0) }
    }

    override fun listAll() = rows.map { UserAccount(it.id, it.username, it.admin, Instant.EPOCH) }

    override fun findById(id: Long) = rows.firstOrNull { it.id == id }

    override fun updatePasswordHash(
        id: Long,
        passwordHash: String,
    ) {
        rows.replaceAll { if (it.id == id) it.copy(passwordHash = passwordHash) else it }
    }

    override fun delete(id: Long) = rows.removeIf { it.id == id }

    override fun exists() = rows.isNotEmpty()

    override fun findByUsername(username: String) = rows.firstOrNull { it.username.equals(username, true) }

    override fun insertFirstAdmin(
        username: String,
        passwordHash: String,
        at: Instant,
    ) = error("unused")

    override fun insert(
        username: String,
        passwordHash: String,
        at: Instant,
    ) = error("unused")

    override fun insertIfFree(
        username: String,
        passwordHash: String,
        at: Instant,
    ) = error("unused")
}
