package dev.starve.infra

import dev.starve.application.AppMetadata
import dev.starve.application.ImageSigner
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Signs image URLs with an HMAC-SHA256 of the username and the URL, cut to 16 bytes of url-safe base64, under a key
 * generated once and kept in app_metadata. Signing is cheap enough for every image of every timeline row.
 */
class HmacImageSigner(
    private val key: ByteArray,
) : ImageSigner {
    override fun sign(
        username: String,
        url: String,
    ): String {
        val mac = Mac.getInstance(ALGORITHM).apply { init(SecretKeySpec(key, ALGORITHM)) }
        val digest = mac.doFinal("$username\n$url".toByteArray(Charsets.UTF_8)).copyOf(SIGNATURE_BYTES)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    /** Compared in constant time. */
    override fun verifies(
        username: String,
        url: String,
        signature: String,
    ): Boolean {
        val expected = sign(username, url).toByteArray(Charsets.UTF_8)
        return MessageDigest.isEqual(expected, signature.toByteArray(Charsets.UTF_8))
    }

    companion object {
        /** The app_metadata key the signing key is kept under. */
        const val KEY_NAME = "image_key"
        private const val ALGORITHM = "HmacSHA256"
        private const val SIGNATURE_BYTES = 16
        private const val KEY_BYTES = 32

        /** The signer under the stored key, which is generated and stored on the first call. */
        fun stored(
            metadata: AppMetadata,
            random: SecureRandom = SecureRandom(),
        ): HmacImageSigner {
            if (metadata.find(KEY_NAME) == null) {
                val bytes = ByteArray(KEY_BYTES).also { random.nextBytes(it) }
                metadata.putIfAbsent(KEY_NAME, Base64.getEncoder().encodeToString(bytes))
            }
            val key = checkNotNull(metadata.find(KEY_NAME)) { "image key missing after it was stored" }
            return HmacImageSigner(Base64.getDecoder().decode(key))
        }
    }
}
