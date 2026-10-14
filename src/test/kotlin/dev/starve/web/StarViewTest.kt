package dev.starve.web

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StarViewTest {
    @Test
    fun `an unfavourited star posts to favourite`() {
        assertEquals("/items/7/favourite", StarView("star-7", 7, false).action)
    }

    @Test
    fun `a favourited star posts to unfavourite`() {
        assertEquals("/items/7/unfavourite", StarView("star-7", 7, true).action)
    }

    @Test
    fun `the reader's star tells the endpoint it came from the reader`() {
        assertEquals("/items/7/unfavourite?from=reader", StarView("reader-star", 7, true, fromReader = true).action)
    }
}
