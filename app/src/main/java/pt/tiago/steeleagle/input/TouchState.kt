package pt.tiago.steeleagle.input

import pt.tiago.steeleagle.core.model.Dir
import pt.tiago.steeleagle.core.model.PlayerInput

/**
 * Pure touch state: one pointer for the D-pad and another for the fire button.
 * The layout is screen-space geometry and can be swapped on rotation.
 */
class TouchState {

    @Volatile
    var layout: TouchLayout? = null
        private set

    private var dpadPointer = NO_POINTER
    private var firePointer = NO_POINTER
    private var direction: Dir? = null
    private var firing = false

    fun setLayout(layout: TouchLayout?) {
        if (this.layout != layout) {
            this.layout = layout
            releaseAll()
        }
    }

    fun onDown(pointerId: Int, x: Int, y: Int) {
        val layout = layout ?: return
        when {
            layout.dpad.contains(x, y) -> {
                dpadPointer = pointerId
                updateDirection(x, y, layout.dpad)
            }
            layout.fireContains(x, y) -> {
                firePointer = pointerId
                firing = true
            }
        }
    }

    fun onMove(pointerId: Int, x: Int, y: Int) {
        val layout = layout ?: return
        if (pointerId == dpadPointer) {
            updateDirection(x, y, layout.dpad)
        }
    }

    fun onUp(pointerId: Int) {
        if (pointerId == dpadPointer) {
            dpadPointer = NO_POINTER
            direction = null
        }
        if (pointerId == firePointer) {
            firePointer = NO_POINTER
            firing = false
        }
    }

    fun cancel() {
        releaseAll()
    }

    fun sample(): PlayerInput = PlayerInput(direction, firing)

    fun reset() {
        releaseAll()
    }

    private fun updateDirection(x: Int, y: Int, dpad: ControlCircle) {
        val dx = x - dpad.centerX
        val dy = y - dpad.centerY
        val deadzone = (dpad.radius * DEADZONE_FRACTION).toInt()
        direction = TouchDirection.quantize(dx, dy, deadzone, direction)
    }

    private fun releaseAll() {
        dpadPointer = NO_POINTER
        firePointer = NO_POINTER
        direction = null
        firing = false
    }

    private companion object {
        const val NO_POINTER = -1
        const val DEADZONE_FRACTION = 0.22f
    }
}
