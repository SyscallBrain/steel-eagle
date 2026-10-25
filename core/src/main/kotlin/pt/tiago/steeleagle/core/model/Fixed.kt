package pt.tiago.steeleagle.core.model

/** Fixed-point and gameplay constants shared by simulation and rendering. */
object Fixed {
    const val SUBPIXELS = 16

    const val FIELD_PIXELS = 208
    const val FIELD_SUBPIXELS = FIELD_PIXELS * SUBPIXELS

    const val TANK_PIXELS = 16
    const val TANK_SUBPIXELS = TANK_PIXELS * SUBPIXELS

    const val BULLET_PIXELS = 4
    const val BULLET_SUBPIXELS = BULLET_PIXELS * SUBPIXELS

    /** 0.75 px per tick, in 1/16 px units. */
    const val PLAYER_SPEED = 12

    /** 4 px per tick. */
    const val BULLET_SPEED = 64

    /** One 16 px block of ice sliding. */
    const val ICE_SLIDE_SUBPIXELS = 16 * SUBPIXELS

    const val TRACK_TICKS_PER_FRAME = 4
}
