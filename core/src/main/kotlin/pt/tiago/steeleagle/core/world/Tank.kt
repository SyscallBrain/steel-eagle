package pt.tiago.steeleagle.core.world

import pt.tiago.steeleagle.core.model.Dir
import pt.tiago.steeleagle.core.model.Fixed
import pt.tiago.steeleagle.core.rules.EnemyType

/** One tank in the simulation, positioned in 1/16 px units. */
class Tank(
    var x: Int,
    var y: Int,
    var dir: Dir = Dir.UP,
    val isPlayer: Boolean = true,
    var weaponLevel: Int = 0,
    val speed: Int = Fixed.PLAYER_SPEED,
    val type: EnemyType? = null,
    var hp: Int = 1,
    val flashing: Boolean = false,
) {
    var trackFrame: Int = 0
        private set

    internal var trackTicks: Int = 0

    internal var slideRemaining: Int = 0
    internal var wasMoving: Boolean = false

    /** Read-only view for the app (engine hum and effects). */
    val isMoving: Boolean get() = wasMoving

    var alive: Boolean = true
    var shieldTicks: Int = 0
    var stunTicks: Int = 0
    var droppedPowerUp: Boolean = false
    var bulletSpeed: Int = Fixed.BULLET_SPEED
    var hasBoat: Boolean = false
    var escapingWater: Boolean = false
    var aiEnabled: Boolean = true
    internal var aiTimer: Int = 0

    val sizeSub: Int = Fixed.TANK_SUBPIXELS

    val left: Int get() = x
    val top: Int get() = y
    val right: Int get() = x + sizeSub
    val bottom: Int get() = y + sizeSub

    fun advanceTrack() {
        trackTicks++
        if (trackTicks >= Fixed.TRACK_TICKS_PER_FRAME) {
            trackTicks = 0
            trackFrame = 1 - trackFrame
        }
    }

    internal fun resetTracks() {
        trackFrame = 0
        trackTicks = 0
    }

    /** Restores the visual track frame after a network snapshot. */
    internal fun restoreTrack(frame: Int) {
        trackFrame = frame.coerceIn(0, 1)
    }
}
