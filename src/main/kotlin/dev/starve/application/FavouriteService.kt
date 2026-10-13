package dev.starve.application

import java.time.Clock

/** Stars and unstars items. Favouriting leaves read state alone. */
class FavouriteService(
    private val items: ItemRepository,
    private val clock: Clock,
) {
    /** Returns false when the item does not exist. */
    fun favourite(id: Long): Boolean = items.favourite(id, clock.instant())

    /** Returns false when the item does not exist. */
    fun unfavourite(id: Long): Boolean = items.unfavourite(id)
}
