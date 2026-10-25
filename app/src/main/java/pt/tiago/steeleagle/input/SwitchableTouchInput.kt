package pt.tiago.steeleagle.input

import android.graphics.Canvas
import android.view.MotionEvent
import pt.tiago.steeleagle.core.model.PlayerInput
import pt.tiago.steeleagle.storage.TouchControl

/**
 * Selects the round D-pad, the NES cross or the floating joystick without
 * rebuilding CombinedInput. The A/B variants share an input with their pad;
 * the layout carries the button style.
 */
class SwitchableTouchInput(
    private val dpad: TouchDpadInput,
    private val joystick: TouchJoystickInput,
    private val cross: TouchDpadInput = TouchDpadInput(cross = true),
) : VirtualTouchInput {

    @Volatile
    var mode: TouchControl = TouchControl.DPAD
        private set

    private val active: VirtualTouchInput
        get() = when {
            mode.joystick -> joystick
            mode.crossPad -> cross
            else -> dpad
        }

    @Synchronized
    fun select(next: TouchControl) {
        if (mode == next) return
        reset()
        mode = next
    }

    @Synchronized
    override fun sample(): PlayerInput = active.sample()

    @Synchronized
    override fun setLayout(layout: TouchLayout) {
        dpad.setLayout(layout)
        joystick.setLayout(layout)
        cross.setLayout(layout)
    }

    @Synchronized
    override fun onTouchEvent(event: MotionEvent): Boolean =
        active.onTouchEvent(event)

    @Synchronized
    override fun draw(canvas: Canvas) = active.draw(canvas)

    @Synchronized
    override fun reset() {
        dpad.reset()
        joystick.reset()
        cross.reset()
    }
}
