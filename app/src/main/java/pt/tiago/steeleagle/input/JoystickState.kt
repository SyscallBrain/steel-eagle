package pt.tiago.steeleagle.input

import pt.tiago.steeleagle.core.model.Dir
import pt.tiago.steeleagle.core.model.PlayerInput
import kotlin.math.hypot
import kotlin.math.roundToInt

data class JoystickVisual(
    val active: Boolean,
    val baseX: Int,
    val baseY: Int,
    val knobX: Int,
    val knobY: Int,
)

/**
 * Pure floating-joystick state. The base appears where the finger first lands
 * in the joystick zone and is dragged along when the finger leaves its ring;
 * the direction is the dominant axis of the base-to-finger vector, with
 * hysteresis near the diagonals.
 */
class JoystickState {

    private var layout: TouchLayout? = null
    private var joystickPointer = NO_POINTER
    private var firePointer = NO_POINTER

    private var baseX = 0
    private var baseY = 0
    private var knobX = 0
    private var knobY = 0
    private var direction: Dir? = null
    private var firing = false

    @Synchronized
    fun setLayout(next: TouchLayout?) {
        if (layout != next) {
            layout = next
            releaseAll()
        }
    }

    @Synchronized
    fun onDown(pointerId: Int, x: Int, y: Int) {
        val current = layout ?: return

        if (current.fireContains(x, y)) {
            if (firePointer == NO_POINTER) {
                firePointer = pointerId
                firing = true
            }
            return
        }

        if (!current.joystickZone.contains(x, y) || joystickPointer != NO_POINTER) {
            return
        }

        joystickPointer = pointerId
        baseX = x
        baseY = y
        knobX = x
        knobY = y
        direction = null
    }

    @Synchronized
    fun onMove(pointerId: Int, x: Int, y: Int) {
        if (pointerId != joystickPointer) return
        val current = layout ?: return

        val radius = current.dpad.radius.coerceAtLeast(1)
        var dx = x - baseX
        var dy = y - baseY

        // The base trails a finger that leaves the ring, so reversing needs
        // only a short move back instead of the whole way to the old base.
        val length = hypot(dx.toDouble(), dy.toDouble())
        if (length > radius) {
            baseX = x - (dx * radius / length).roundToInt()
            baseY = y - (dy * radius / length).roundToInt()
            dx = x - baseX
            dy = y - baseY
        }
        knobX = x
        knobY = y

        val deadzone = (radius * DEADZONE_FRACTION).toInt()
        direction = TouchDirection.quantize(dx, dy, deadzone, direction)
    }

    @Synchronized
    fun onUp(pointerId: Int) {
        if (pointerId == joystickPointer) {
            joystickPointer = NO_POINTER
            direction = null
        }
        if (pointerId == firePointer) {
            firePointer = NO_POINTER
            firing = false
        }
    }

    @Synchronized
    fun cancel() {
        releaseAll()
    }

    @Synchronized
    fun sample(): PlayerInput = PlayerInput(direction, firing)

    @Synchronized
    fun visual(): JoystickVisual? {
        val current = layout ?: return null
        return if (joystickPointer == NO_POINTER) {
            JoystickVisual(
                active = false,
                baseX = current.dpad.centerX,
                baseY = current.dpad.centerY,
                knobX = current.dpad.centerX,
                knobY = current.dpad.centerY,
            )
        } else {
            JoystickVisual(
                active = true,
                baseX = baseX,
                baseY = baseY,
                knobX = knobX,
                knobY = knobY,
            )
        }
    }

    @Synchronized
    fun currentLayout(): TouchLayout? = layout

    @Synchronized
    fun reset() {
        releaseAll()
    }

    private fun releaseAll() {
        joystickPointer = NO_POINTER
        firePointer = NO_POINTER
        direction = null
        firing = false
    }

    private companion object {
        const val NO_POINTER = -1
        const val DEADZONE_FRACTION = 0.16f
    }
}
