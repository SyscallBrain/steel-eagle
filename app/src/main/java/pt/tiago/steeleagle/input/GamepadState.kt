package pt.tiago.steeleagle.input

import pt.tiago.steeleagle.core.model.Dir
import pt.tiago.steeleagle.core.model.PlayerInput
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pure gamepad state: D-pad keys, hat and left stick. D-pad keys win over the
 * hat, which wins over the stick. Axis values stay as floats only at this
 * boundary; the resulting direction is a four-way enum.
 */
class GamepadState {

    private val keys = KeyState()
    private var hatDirection: Dir? = null
    private var stickDirection: Dir? = null

    fun press(keyCode: Int) = keys.press(keyCode)

    fun release(keyCode: Int) = keys.release(keyCode)

    fun pressFire(keyCode: Int) = keys.pressFire(keyCode)

    fun releaseFire(keyCode: Int) = keys.releaseFire(keyCode)

    fun setAxes(x: Float, y: Float, hatX: Float, hatY: Float) {
        hatDirection = quantize(hatX, hatY)
        stickDirection = quantize(x, y)
    }

    fun sample(directionFor: (Int) -> Dir?): PlayerInput {
        val keysInput = keys.sample(directionFor)
        return PlayerInput(
            direction = keysInput.direction ?: hatDirection ?: stickDirection,
            fire = keysInput.fire,
        )
    }

    fun reset() {
        keys.reset()
        hatDirection = null
        stickDirection = null
    }

    companion object {
        const val DEADZONE = 0.5f

        fun quantize(x: Float, y: Float): Dir? {
            if (abs(x) < DEADZONE && abs(y) < DEADZONE) return null
            return Dir.fromVector((x * 1000f).roundToInt(), (y * 1000f).roundToInt())
        }
    }
}
