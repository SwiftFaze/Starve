package dev.starve.application

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** An image as the proxy serves it: its bytes and their media type. */
class ProxiedImage(
    val bytes: ByteArray,
    val contentType: String,
)

/** Fetches an image from its own site for the proxy. */
fun interface ImageFetcher {
    /**
     * The image at the http(s) [url]; null when it cannot be fetched in time, is too large, is not an image, or its
     * host (or any it redirects to) is a private or loopback address.
     */
    fun fetch(url: String): ProxiedImage?
}

/** The proxy's store of fetched images, beside the database. */
interface ImageCache {
    /** The stored image of [url], or null when none is stored; finding it counts as a use. */
    fun find(url: String): ProxiedImage?

    fun store(
        url: String,
        image: ProxiedImage,
    )

    /**
     * Removes every stored image whose URL is not in [keep], then the least recently used ones until all of them
     * together take at most [maxBytes]; returns how many were removed.
     */
    fun prune(
        keep: Set<String>,
        maxBytes: Long,
    ): Int
}

/** Every image URL a stored item or source refers to: thumbnails, favicons and the images in post bodies. */
fun interface ImageReferences {
    fun all(): Set<String>
}

/** Rewrites the links and image sources of stored post HTML as it is shown. */
fun interface HtmlLinks {
    fun rewrite(
        html: String,
        image: (String) -> String,
        link: (String) -> String,
    ): String
}

/**
 * Signs image URLs for one viewer, so the proxy only ever fetches what Starve itself put on that viewer's page: an
 * HMAC of the username and the URL under a key generated once and kept in app_metadata.
 */
class ImageSigner(
    private val key: ByteArray,
) {
    fun sign(
        username: String,
        url: String,
    ): String {
        val mac = Mac.getInstance(ALGORITHM).apply { init(SecretKeySpec(key, ALGORITHM)) }
        val digest = mac.doFinal("$username\n$url".toByteArray(Charsets.UTF_8)).copyOf(SIGNATURE_BYTES)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    /** Whether [signature] is [url]'s for [username], compared in constant time. */
    fun verifies(
        username: String,
        url: String,
        signature: String,
    ): Boolean = MessageDigest.isEqual(sign(username, url).toByteArray(Charsets.UTF_8), signature.toByteArray(Charsets.UTF_8))

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
        ): ImageSigner {
            if (metadata.find(KEY_NAME) == null) {
                val bytes = ByteArray(KEY_BYTES).also { random.nextBytes(it) }
                metadata.putIfAbsent(KEY_NAME, Base64.getEncoder().encodeToString(bytes))
            }
            val key = checkNotNull(metadata.find(KEY_NAME)) { "image key missing after it was stored" }
            return ImageSigner(Base64.getDecoder().decode(key))
        }
    }
}

/** Serves the images Starve shows from its own cache, fetching each from its site once. */
class ImageProxyService(
    private val fetcher: ImageFetcher,
    private val cache: ImageCache,
) {
    fun image(url: String): ProxiedImage? = cache.find(url) ?: fetcher.fetch(url)?.also { cache.store(url, it) }
}

/** Keeps the image cache to what stored items and sources still show, within [maxBytes]. */
class ImageCacheService(
    private val cache: ImageCache,
    private val references: ImageReferences,
    private val maxBytes: Long,
) {
    fun prune(): Int = cache.prune(references.all(), maxBytes)
}
