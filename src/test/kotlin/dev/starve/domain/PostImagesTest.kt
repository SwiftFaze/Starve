package dev.starve.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PostImagesTest {
    private val thumb = "https://img.example/hero.jpg"

    @Test
    fun `the feed image is the header when the body does not show it`() {
        assertEquals(thumb, PostImages.headerImage(thumb, "<p>Hello</p>"))
        assertEquals(thumb, PostImages.headerImage(thumb, null))
    }

    @Test
    fun `no header when there is no feed image`() {
        assertNull(PostImages.headerImage(null, "<p>Hello</p>"))
    }

    @Test
    fun `the feed image is not repeated when the body already shows that exact image`() {
        assertNull(PostImages.headerImage(thumb, "<p>Hello</p><img src=\"$thumb\">"))
    }

    @Test
    fun `a body image with another url does not suppress the header`() {
        assertEquals(thumb, PostImages.headerImage(thumb, "<img src=\"https://img.example/other.jpg\">"))
    }

    @Test
    fun `an ampersand in the url is compared in its escaped form, as a sanitised body writes it`() {
        val url = "https://img.example/p.jpg?a=1&b=2"
        assertNull(PostImages.headerImage(url, "<img src=\"https://img.example/p.jpg?a=1&amp;b=2\">"))
    }
}
