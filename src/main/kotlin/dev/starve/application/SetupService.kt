package dev.starve.application

import dev.starve.domain.AccountRules
import dev.starve.domain.SetupCodeRules
import java.security.SecureRandom
import java.time.Clock

/** What a setup attempt came to. */
sealed interface SetupOutcome {
    data class Created(
        val username: String,
    ) : SetupOutcome

    /** The username or password broke a rule; [error] says which. */
    data class Invalid(
        val error: String,
    ) : SetupOutcome

    data object WrongCode : SetupOutcome

    data object Blocked : SetupOutcome

    /** A user already exists, so setup is closed. */
    data object AlreadySetUp : SetupOutcome
}

/**
 * First-run setup. A fresh code is issued for each run while no account exists and is held in memory only;
 * wrong codes are throttled per client address.
 */
class SetupService(
    private val users: UserRepository,
    private val hasher: PasswordHasher,
    private val throttle: FailureThrottle,
    private val clock: Clock,
    private val random: SecureRandom = SecureRandom(),
) {
    @Volatile
    private var code: String? = null

    fun pending(): Boolean = !users.exists()

    /** Issues this run's code while setup is pending, and returns it; null when setup is closed. */
    fun startRun(): String? {
        code = if (pending()) SetupCodeRules.generate(random) else null
        return code
    }

    fun complete(
        submitted: String,
        username: String,
        password: String,
        client: String,
    ): SetupOutcome {
        val key = "setup|$client"
        return when {
            !pending() -> {
                SetupOutcome.AlreadySetUp
            }

            throttle.isBlocked(key) -> {
                SetupOutcome.Blocked
            }

            !codeMatches(submitted) -> {
                throttle.recordFailure(key)
                SetupOutcome.WrongCode
            }

            else -> {
                create(username, password, key)
            }
        }
    }

    private fun codeMatches(submitted: String): Boolean = code?.let { SetupCodeRules.matches(it, submitted) } == true

    /** Validates the account, then stores it only if no account has appeared in the meantime. */
    private fun create(
        username: String,
        password: String,
        key: String,
    ): SetupOutcome {
        val error = AccountRules.usernameError(username) ?: AccountRules.passwordError(password)
        return when {
            error != null -> {
                SetupOutcome.Invalid(error)
            }

            !users.insertFirstAdmin(username, hasher.hash(password), clock.instant()) -> {
                SetupOutcome.AlreadySetUp
            }

            else -> {
                code = null
                throttle.reset(key)
                SetupOutcome.Created(username)
            }
        }
    }
}
