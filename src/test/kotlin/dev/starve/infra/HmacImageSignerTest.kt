package dev.starve.infra

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.security.SecureRandom

class HmacImageSignerTest {
    private val signer = HmacImageSigner(ByteArray(32) { it.toByte() })

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
        assertNotEquals(signature, HmacImageSigner(ByteArray(32)).sign("rob", "https://a.example/x.png"))
    }

    @Test
    fun `the stored key is generated once and reused`() {
        val metadata = FakeMetadata()
        val first = HmacImageSigner.stored(metadata, SecureRandom())
        val key = metadata.find(HmacImageSigner.KEY_NAME)

        val second = HmacImageSigner.stored(metadata, SecureRandom())

        assertEquals(key, metadata.find(HmacImageSigner.KEY_NAME))
        assertEquals(first.sign("rob", "u"), second.sign("rob", "u"))
    }
}
