package pt.tiago.steeleagle.core.world

import pt.tiago.steeleagle.core.rules.PowerUpType

/** A 16x16 pickup on the field, blinking until it expires. */
data class PowerUp(
    val type: PowerUpType,
    var x: Int,
    var y: Int,
    var ticksRemaining: Int,
) {
    fun isVisible(tick: Long): Boolean {
        val rate = if (ticksRemaining <= FAST_BLINK_TICKS) 4L else 8L
        return (tick / rate) % 2 == 0L
    }

    companion object {
        const val SIZE_PIXELS = 16
        const val FAST_BLINK_TICKS = 120
    }
}
