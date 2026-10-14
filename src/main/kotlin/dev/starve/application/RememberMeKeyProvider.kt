package dev.starve.application

import java.security.SecureRandom
import java.util.Base64

/** Gives the remember-me signing key: generated once on the first start, then reused by every later start. */
class RememberMeKeyProvider(
    private val metadata: AppMetadata,
    private val random: SecureRandom = SecureRandom(),
) {
    fun key(): String {
        metadata.find(NAME)?.let { return it }
        metadata.putIfAbsent(NAME, generate())
        return checkNotNull(metadata.find(NAME)) { "remember-me key missing after it was stored" }
    }

    private fun generate(): String {
        val bytes = ByteArray(KEY_BYTES).also { random.nextBytes(it) }
        return Base64.getEncoder().encodeToString(bytes)
    }

    companion object {
        const val NAME = "remember_me_key"
        private const val KEY_BYTES = 32
    }
}
