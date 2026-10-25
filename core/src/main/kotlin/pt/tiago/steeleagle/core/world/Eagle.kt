package pt.tiago.steeleagle.core.world

import pt.tiago.steeleagle.core.model.Fixed

/** The base at block (6,12), 16x16 px. */
class Eagle(
    val x: Int = 96 * Fixed.SUBPIXELS,
    val y: Int = 192 * Fixed.SUBPIXELS,
    val sizeSub: Int = Fixed.TANK_SUBPIXELS,
) {
    var alive: Boolean = true

    val left: Int get() = x
    val top: Int get() = y
    val right: Int get() = x + sizeSub
    val bottom: Int get() = y + sizeSub
}
