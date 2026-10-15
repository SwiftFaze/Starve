package dev.starve.application

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.security.SecureRandom

private class MapMetadata(
    val values: MutableMap<String, String> = mutableMapOf(),
) : AppMetadata {
    override fun find(key: String): String? = values[key]

    override fun putIfAbsent(
        key: String,
        value: String,
    ): Boolean {
        if (values.containsKey(key)) return false
        values[key] = value
        return true
    }

    override fun put(
        key: String,
        value: String,
    ) {
        values[key] = value
    }
}

class RememberMeKeyProviderTest {
    @Test
    fun `the first start generates a key and stores it`() {
        val store = MapMetadata()
        val key = RememberMeKeyProvider(store, SecureRandom()).key()

        assertNotNull(key)
        assertEquals(key, store.values[RememberMeKeyProvider.NAME])
    }

    @Test
    fun `later starts reuse the stored key instead of generating another`() {
        val store = MapMetadata()
        val first = RememberMeKeyProvider(store, SecureRandom()).key()
        val second = RememberMeKeyProvider(store, SecureRandom()).key()

        assertEquals(first, second)
    }

    @Test
    fun `generated keys differ between databases`() {
        val a = RememberMeKeyProvider(MapMetadata(), SecureRandom()).key()
        val b = RememberMeKeyProvider(MapMetadata(), SecureRandom()).key()

        assertNotEquals(a, b)
    }

    @Test
    fun `a key stored by another writer wins over a freshly generated one`() {
        val store = MapMetadata(mutableMapOf(RememberMeKeyProvider.NAME to "already-there"))
        val provider = RememberMeKeyProvider(store, SecureRandom())

        assertEquals("already-there", provider.key())
        assertFalse(store.putIfAbsent(RememberMeKeyProvider.NAME, "other"))
    }
}
