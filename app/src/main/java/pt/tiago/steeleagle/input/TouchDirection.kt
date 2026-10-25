package pt.tiago.steeleagle.input

import pt.tiago.steeleagle.core.model.Dir
import kotlin.math.abs

/**
 * Four-way quantization for touch pads with hysteresis. Near a diagonal the
 * plain dominant-axis rule flips between two directions on every pixel of
 * jitter, which makes the tank stutter; the held axis is kept until the other
 * axis is clearly stronger.
 */
object TouchDirection {

    /** The other axis must exceed the held one by this factor (about 51 degrees). */
    const val HYSTERESIS = 1.25f

    fun quantize(dx: Int, dy: Int, deadzone: Int, current: Dir?): Dir? {
        if (abs(dx) < deadzone && abs(dy) < deadzone) return null
        if (current != null) {
            if (current.dx != 0 && abs(dy) <= abs(dx) * HYSTERESIS) {
                return if (dx > 0) Dir.RIGHT else Dir.LEFT
            }
            if (current.dy != 0 && abs(dx) <= abs(dy) * HYSTERESIS) {
                return if (dy > 0) Dir.DOWN else Dir.UP
            }
        }
        return Dir.fromVector(dx, dy)
    }
}
