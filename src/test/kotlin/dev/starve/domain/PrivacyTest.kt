package dev.starve.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.net.InetAddress

class PrivacyTest {
    @Test
    fun `utm parameters and click ids are stripped, everything else keeps its place`() {
        val post = "https://example.com/post"
        assertEquals("$post?id=7", TrackingParams.strip("$post?utm_source=rss&id=7"))
        assertEquals(
            "https://example.com/p?a=1&b=2#top",
            TrackingParams.strip("https://example.com/p?a=1&fbclid=x&UTM_Medium=y&b=2&gclid=z#top"),
        )
        assertEquals("$post#top", TrackingParams.strip("$post?utm_campaign=c&mc_eid=e#top"))
    }

    @Test
    fun `a link without tracking, without a query or that does not parse is unchanged`() {
        listOf("https://example.com/p?id=7&utm=keep", "https://example.com/p", "https://exa mple.com/?utm_x=1", "")
            .forEach { assertEquals(it, TrackingParams.strip(it)) }
    }

    @Test
    fun `empty query pieces are dropped along with tracking ones`() =
        assertEquals("https://example.com/p?a=1", TrackingParams.strip("https://example.com/p?a=1&&utm_term=t"))

    @Test
    fun `tracking names are utm_ prefixed or known, ignoring case`() {
        assertTrue(TrackingParams.isTracking("utm_source"))
        assertTrue(TrackingParams.isTracking("FBCLID"))
        assertTrue(TrackingParams.isTracking("mc_cid"))
        assertFalse(TrackingParams.isTracking("utm"))
        assertFalse(TrackingParams.isTracking("id"))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "127.0.0.1", "10.1.2.3", "172.16.0.1", "172.31.255.255", "192.168.1.1", "169.254.169.254", "0.0.0.0",
            "100.64.0.1", "100.127.255.255", "224.0.0.1", "::1", "fe80::1", "fc00::1", "fd12:3456::1", "::",
        ],
    )
    fun `the home network, the machine itself and multicast are not public`(address: String) =
        assertFalse(AddressRules.isPublic(InetAddress.getByName(address)), address)

    @ParameterizedTest
    @ValueSource(strings = ["93.184.216.34", "8.8.8.8", "100.63.255.255", "100.128.0.1", "172.32.0.1", "2606:4700::1"])
    fun `other addresses are public`(address: String) {
        assertTrue(AddressRules.isPublic(InetAddress.getByName(address)), address)
    }

    @Test
    fun `image types are accepted by their bare media type, ignoring case and parameters`() {
        assertEquals("image/png", ImageRules.acceptedType("image/png"))
        assertEquals("image/jpeg", ImageRules.acceptedType(" Image/JPEG ; charset=x"))
        assertEquals("image/svg+xml", ImageRules.acceptedType("image/svg+xml"))
        assertEquals("image/x-icon", ImageRules.acceptedType("image/x-icon"))
        assertNull(ImageRules.acceptedType("text/html"))
        assertNull(ImageRules.acceptedType("image/tiff"))
        assertNull(ImageRules.acceptedType(null))
    }
}
