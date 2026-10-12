package dev.starve.domain

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * The deck's stand-in waveform for players that report none (YouTube, or SoundCloud before its
 * own arrives): bar heights in percent, shaped by slow swells and seeded by the item id so an
 * item always looks the same.
 */
object Waveform {
    const val BARS = 120
    const val MIN_HEIGHT = 8
    private const val FULL = 100

    // How many swells cross the waveform: at least this many, up to this many more.
    private const val SWELLS = 3.0
    private const val MORE_SWELLS = 4.0

    // Quiet passages keep this share of full height; the rest follows the swell.
    private const val FLOOR = 0.3

    // Each bar keeps at least this share of its swell; the rest is random.
    private const val STEADY = 0.45

    fun bars(itemId: Long): List<Int> {
        val random = Random(itemId)
        val phase = random.nextDouble(2 * PI)
        val swells = SWELLS + random.nextDouble(MORE_SWELLS)
        return List(BARS) { bar ->
            val at = bar.toDouble() / BARS
            val envelope = FLOOR + (1 - FLOOR) * abs(sin(at * swells + phase))
            val height = envelope * (STEADY + (1 - STEADY) * random.nextDouble()) * FULL
            height.roundToInt().coerceIn(MIN_HEIGHT, FULL)
        }
    }
}
