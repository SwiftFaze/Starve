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
        assertEquals("/items/7/unfavourite?from=reader", StarView("reader-star-7", 7, true, from = "reader").action)
    }

    @Test
    fun `the deck's star tells the endpoint it came from the deck`() {
        assertEquals("/items/7/favourite?from=deck", StarPlace.DECK.view(7, false).action)
    }

    @Test
    fun `every place's star names its item in its id`() {
        assertEquals("star-7", StarPlace.ROW.view(7, false).domId)
        assertEquals("deck-star-7", StarPlace.DECK.view(7, false).domId)
        assertEquals("reader-star-7", StarPlace.READER.view(7, false).domId)
        assertEquals("detail-star-7", StarPlace.DETAIL.view(7, false).domId)
    }

    @Test
    fun `a from parameter names its place, and anything else is the timeline row`() {
        assertEquals(StarPlace.DECK, StarPlace.from("deck"))
        assertEquals(StarPlace.READER, StarPlace.from("reader"))
        assertEquals(StarPlace.DETAIL, StarPlace.from("detail"))
        assertEquals(StarPlace.ROW, StarPlace.from(null))
        assertEquals(StarPlace.ROW, StarPlace.from("elsewhere"))
    }

    @Test
    fun `a place's star carries the item, its state, its place and whether it is out of band`() {
        val star = StarPlace.DETAIL.view(7, true, oob = true)

        assertEquals(7L, star.itemId)
        assertEquals(true, star.favourite)
        assertEquals("detail", star.from)
        assertEquals(true, star.oob)
        assertEquals(false, StarPlace.ROW.view(7, false).favourite)
        assertEquals(false, StarPlace.ROW.view(7, false).oob)
        assertEquals(null, StarPlace.ROW.view(7, false).from)
    }
}
