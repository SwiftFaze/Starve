package dev.starve.domain

import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.Locale

/** A stored account. [passwordHash] is the BCrypt hash, never the password itself. */
data class User(
    val id: Long,
    val username: String,
    val passwordHash: String,
    val admin: Boolean,
)

/** Username and password rules, and the messages shown when a rule fails. */
object AccountRules {
    const val USERNAME_ERROR = "Username must be 3-32 letters, digits, '.', '_' or '-'"
    const val PASSWORD_SHORT = "Password must be at least 10 characters"
    const val PASSWORD_LONG = "Password is too long"
    const val LOGIN_FAILED = "Invalid username or password"
    const val TOO_MANY_ATTEMPTS = "Too many failed attempts. Try again later."
    const val INVALID_SETUP_CODE = "Invalid setup code"

    private const val MIN_PASSWORD_CHARS = 10
    private const val MAX_PASSWORD_BYTES = 72
    private val USERNAME = Regex("[A-Za-z0-9._-]{3,32}")

    /** The error for [username], or null when it is allowed. */
    fun usernameError(username: String): String? = if (USERNAME.matches(username)) null else USERNAME_ERROR

    /** The error for [password], or null when it is allowed. Length counts characters; the cap counts UTF-8 bytes. */
    fun passwordError(password: String): String? =
        when {
            password.codePointCount(0, password.length) < MIN_PASSWORD_CHARS -> PASSWORD_SHORT
            password.toByteArray(Charsets.UTF_8).size > MAX_PASSWORD_BYTES -> PASSWORD_LONG
            else -> null
        }
}

/** The one-time setup code: `XXXX-XXXX` from an alphabet without the look-alikes 0, O, 1, I and L. */
object SetupCodeRules {
    private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    private const val HALF = 4

    fun generate(random: SecureRandom): String {
        val chars = CharArray(2 * HALF) { ALPHABET[random.nextInt(ALPHABET.length)] }
        return String(chars, 0, HALF) + "-" + String(chars, HALF, HALF)
    }

    /** Whether [given] names [expected]: case-insensitive, hyphen optional, surrounding spaces ignored. */
    fun matches(
        expected: String,
        given: String,
    ): Boolean = MessageDigest.isEqual(normalise(expected).toByteArray(), normalise(given).toByteArray())

    private fun normalise(code: String): String = code.trim().uppercase(Locale.ROOT).replace("-", "")
}

/** The brute-force policy: this many consecutive failures block a key for [BLOCK_FOR]. */
object ThrottleRules {
    const val MAX_FAILURES = 5
    private const val BLOCK_MINUTES = 15L
    val BLOCK_FOR: Duration = Duration.ofMinutes(BLOCK_MINUTES)
}

/** Consecutive failures for one throttle key, and when its block ends (null when not blocked). */
data class FailureCount(
    val failures: Int = 0,
    val blockedUntil: Instant? = null,
) {
    fun isBlocked(now: Instant): Boolean = blockedUntil?.isAfter(now) == true

    /** The state after one more failure at [now]. Failures during a block change nothing. */
    fun failed(now: Instant): FailureCount {
        if (isBlocked(now)) return this
        val count = (if (blockedUntil != null) 0 else failures) + 1
        return if (count >= ThrottleRules.MAX_FAILURES) {
            FailureCount(count, now.plus(ThrottleRules.BLOCK_FOR))
        } else {
            FailureCount(count, null)
        }
    }
}
