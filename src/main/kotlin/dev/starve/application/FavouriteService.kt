package dev.starve.application

import java.time.Clock

/** Stars and unstars items. Favouriting leaves read state alone. */
class FavouriteService(
    private val favourites: FavouriteRepository,
    private val clock: Clock,
) {
    /** Returns false when [owner] has no such item. */
    fun favourite(
        owner: Long,
        id: Long,
    ): Boolean = favourites.favourite(owner, id, clock.instant())

    /** Returns false when [owner] has no such item. */
    fun unfavourite(
        owner: Long,
        id: Long,
    ): Boolean = favourites.unfavourite(owner, id)
}
