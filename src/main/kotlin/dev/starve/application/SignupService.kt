package dev.starve.application

import dev.starve.domain.AccountRules
import java.time.Clock

private const val SIGNUPS_KEY = "signups"
private const val ON = "on"
private const val OFF = "off"

/** Whether anyone may sign up. Kept in app_metadata and read on every call, so a change applies at once. */
class SignupSettings(
    private val metadata: AppMetadata,
) {
    /** Off until an admin turns it on: no stored value means off. */
    fun enabled(): Boolean = metadata.find(SIGNUPS_KEY) == ON

    fun set(enabled: Boolean) = metadata.put(SIGNUPS_KEY, if (enabled) ON else OFF)
}

/** What a signup attempt came to. */
sealed interface SignupOutcome {
    data class Created(
        val username: String,
    ) : SignupOutcome

    /** The username or password broke a rule; [error] says which. */
    data class Invalid(
        val error: String,
    ) : SignupOutcome

    data object Taken : SignupOutcome

    data object Blocked : SignupOutcome

    /** Signups are switched off. */
    data object Closed : SignupOutcome
}

/**
 * Self-service signup: creates a regular (never admin) account while signups are on. Failed attempts, whether a
 * broken rule or a taken name, are throttled per client address; successes are not counted.
 */
class SignupService(
    private val users: UserRepository,
    private val hasher: PasswordHasher,
    private val throttle: FailureThrottle,
    private val clock: Clock,
    private val settings: SignupSettings,
) {
    fun signUp(
        username: String,
        password: String,
        client: String,
    ): SignupOutcome {
        val key = "signup|$client"
        return when {
            !settings.enabled() -> SignupOutcome.Closed
            throttle.isBlocked(key) -> SignupOutcome.Blocked
            else -> create(username, password).also { if (it !is SignupOutcome.Created) throttle.recordFailure(key) }
        }
    }

    private fun create(
        username: String,
        password: String,
    ): SignupOutcome {
        val error = AccountRules.usernameError(username) ?: AccountRules.passwordError(password)
        if (error != null) return SignupOutcome.Invalid(error)
        val created = users.insertIfFree(username, hasher.hash(password), clock.instant())
        return if (created == null) SignupOutcome.Taken else SignupOutcome.Created(created.username)
    }
}
