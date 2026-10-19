package dev.starve.application

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
 * Signs image URLs for one viewer, so the proxy only ever fetches what Starve itself put on that viewer's page.
 */
interface ImageSigner {
    fun sign(
        username: String,
        url: String,
    ): String

    /** Whether [signature] is [url]'s for [username]. */
    fun verifies(
        username: String,
        url: String,
        signature: String,
    ): Boolean
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
