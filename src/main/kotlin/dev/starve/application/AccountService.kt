package dev.starve.application

import dev.starve.domain.AccountRules
import dev.starve.domain.TemporaryPasswordRules
import dev.starve.domain.User
import java.security.SecureRandom
import java.util.Locale

/** What a password change came to. */
sealed interface PasswordChange {
    data object Changed : PasswordChange

    data object WrongCurrent : PasswordChange

    data object Blocked : PasswordChange

    /** The new password broke a rule; [error] says which. */
    data class Invalid(
        val error: String,
    ) : PasswordChange
}

/**
 * A signed-in user changing their own password. The current password must be right; wrong ones are throttled per
 * username and client address under a key of their own, and a blocked user is refused even with the right one.
 * Only wrong current passwords count as failures.
 */
class AccountService(
    private val users: UserAdminRepository,
    private val hasher: PasswordHasher,
    private val matcher: PasswordMatcher,
    private val throttle: FailureThrottle,
) {
    fun changePassword(
        userId: Long,
        current: String,
        new: String,
        client: String,
    ): PasswordChange {
        val user = checkNotNull(users.findById(userId)) { "no user $userId" }
        val key = "password|${user.username.lowercase(Locale.ROOT)}|$client"
        val error = AccountRules.passwordError(new)
        return when {
            throttle.isBlocked(key) -> {
                PasswordChange.Blocked
            }

            !matcher.matches(current, user.passwordHash) -> {
                throttle.recordFailure(key)
                PasswordChange.WrongCurrent
            }

            error != null -> {
                PasswordChange.Invalid(error)
            }

            else -> {
                users.updatePasswordHash(userId, hasher.hash(new))
                throttle.reset(key)
                PasswordChange.Changed
            }
        }
    }
}

/** What an admin's action on another user came to. */
sealed interface UserAction<out T> {
    data class Done<T>(
        val value: T,
    ) : UserAction<T>

    /** No such user. */
    data object Missing : UserAction<Nothing>

    /** The admin aimed it at their own account, which they cannot reset or delete here. */
    data object Self : UserAction<Nothing>
}

/** A user's new temporary password, in the clear: it exists only to be shown to the admin once. */
data class PasswordReset(
    val username: String,
    val temporaryPassword: String,
)

/** The admin's view of the accounts: the list, a password reset and deletion. Nobody can act on themselves. */
class UserAdminService(
    private val users: UserAdminRepository,
    private val hasher: PasswordHasher,
    private val random: SecureRandom = SecureRandom(),
) {
    fun list() = users.listAll()

    /** The username of [id], when the admin [actingId] may act on it. */
    fun lookup(
        actingId: Long,
        id: Long,
    ): UserAction<String> = act(actingId, id) { it.username }

    /** Gives [id] a new random temporary password, stored hashed, and returns it for the admin to hand over. */
    fun resetPassword(
        actingId: Long,
        id: Long,
    ): UserAction<PasswordReset> =
        act(actingId, id) { user ->
            val temporary = TemporaryPasswordRules.generate(random)
            users.updatePasswordHash(user.id, hasher.hash(temporary))
            PasswordReset(user.username, temporary)
        }

    /** Deletes [id] and returns their username. */
    fun delete(
        actingId: Long,
        id: Long,
    ): UserAction<String> =
        act(actingId, id) { user ->
            users.delete(user.id)
            user.username
        }

    private fun <T> act(
        actingId: Long,
        id: Long,
        action: (User) -> T,
    ): UserAction<T> {
        val user = if (actingId == id) null else users.findById(id)
        return when {
            actingId == id -> UserAction.Self
            user == null -> UserAction.Missing
            else -> UserAction.Done(action(user))
        }
    }
}
