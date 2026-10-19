package dev.starve.application

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.security.SecureRandom

class ImageProxyTest {
    private val signer = ImageSigner(ByteArray(32) { it.toByte() })

    @Test
    fun `a signature verifies for its viewer and url only`() {
        val signature = signer.sign("rob", "https://a.example/x.png")

        assertTrue(signer.verifies("rob", "https://a.example/x.png", signature))
        assertFalse(signer.verifies("bea", "https://a.example/x.png", signature))
        assertFalse(signer.verifies("rob", "https://a.example/y.png", signature))
        assertFalse(signer.verifies("rob", "https://a.example/x.png", signature.dropLast(1) + "A"))
        assertFalse(signer.verifies("rob", "https://a.example/x.png", ""))
    }

    @Test
    fun `signatures are 16 bytes in url-safe base64, and differ by key`() {
        val signature = signer.sign("rob", "https://a.example/x.png")

        assertEquals(22, signature.length)
        assertTrue(signature.all { it.isLetterOrDigit() || it == '-' || it == '_' }, signature)
        assertNotEquals(signature, ImageSigner(ByteArray(32)).sign("rob", "https://a.example/x.png"))
    }

    @Test
    fun `the stored key is generated once and reused`() {
        val metadata = MemoryAppMetadata()
        val first = ImageSigner.stored(metadata, SecureRandom())
        val key = metadata.find(ImageSigner.KEY_NAME)

        val second = ImageSigner.stored(metadata, SecureRandom())

        assertEquals(key, metadata.find(ImageSigner.KEY_NAME))
        assertEquals(first.sign("rob", "u"), second.sign("rob", "u"))
    }

    private class Images(
        private val image: ProxiedImage?,
    ) : ImageFetcher {
        val asked = mutableListOf<String>()

        override fun fetch(url: String): ProxiedImage? {
            asked += url
            return image
        }
    }

    private class Cache : ImageCache {
        val stored = mutableMapOf<String, ProxiedImage>()
        var pruned: Pair<Set<String>, Long>? = null

        override fun find(url: String) = stored[url]

        override fun store(
            url: String,
            image: ProxiedImage,
        ) {
            stored[url] = image
        }

        override fun prune(
            keep: Set<String>,
            maxBytes: Long,
        ): Int {
            pruned = keep to maxBytes
            return 3
        }
    }

    @Test
    fun `an image is fetched once, then served from the cache`() {
        val image = ProxiedImage(byteArrayOf(1, 2), "image/png")
        val fetcher = Images(image)
        val cache = Cache()
        val proxy = ImageProxyService(fetcher, cache)

        assertSame(image, proxy.image("https://a.example/x.png"))
        assertSame(image, proxy.image("https://a.example/x.png"))

        assertEquals(listOf("https://a.example/x.png"), fetcher.asked)
        assertSame(image, cache.stored["https://a.example/x.png"])
    }

    @Test
    fun `an image that cannot be fetched is not stored`() {
        val cache = Cache()

        assertNull(ImageProxyService(Images(null), cache).image("https://a.example/x.png"))
        assertTrue(cache.stored.isEmpty())
    }

    @Test
    fun `pruning keeps what is referenced within the cap`() {
        val cache = Cache()

        val removed = ImageCacheService(cache, { setOf("https://a.example/x.png") }, 500).prune()

        assertEquals(3, removed)
        assertEquals(setOf("https://a.example/x.png") to 500L, cache.pruned)
    }
}

/** App metadata kept in a map. */
private class MemoryAppMetadata : AppMetadata {
    private val values = mutableMapOf<String, String>()

    override fun find(key: String) = values[key]

    override fun putIfAbsent(
        key: String,
        value: String,
    ) = values.putIfAbsent(key, value) == null

    override fun put(
        key: String,
        value: String,
    ) {
        values[key] = value
    }
}
