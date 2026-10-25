package pt.tiago.steeleagle.input

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import pt.tiago.steeleagle.core.model.Dir
import pt.tiago.steeleagle.core.model.PlayerInput

/**
 * Bluetooth/USB gamepad input: D-pad keys, left stick and hat axes, fire on A/B
 * or the shoulder buttons. One pad drives player one in phase 2; phase 10 will
 * add the second pad.
 */
class GamepadInput(
    private val acceptsDevice: (Int) -> Boolean = { true },
) : InputSource {

    private val state = GamepadState()

    @Synchronized
    fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!acceptsDevice(event.deviceId)) return false
        if (event.repeatCount > 0) return isHandled(keyCode)
        return when {
            keyCode in DIRECTIONS -> {
                state.press(keyCode)
                true
            }
            keyCode in FIRE_BUTTONS -> {
                state.pressFire(keyCode)
                true
            }
            else -> false
        }
    }

    @Synchronized
    fun onKeyUp(keyCode: Int, deviceId: Int): Boolean {
        if (!acceptsDevice(deviceId)) return false
        return when {
            keyCode in DIRECTIONS -> {
                state.release(keyCode)
                true
            }
            keyCode in FIRE_BUTTONS -> {
                state.releaseFire(keyCode)
                true
            }
            else -> false
        }
    }

    @Synchronized
    fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (!acceptsDevice(event.deviceId)) return false
        if (!isGamepad(event)) return false

        state.setAxes(
            x = event.getAxisValue(MotionEvent.AXIS_X),
            y = event.getAxisValue(MotionEvent.AXIS_Y),
            hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X),
            hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y),
        )
        return true
    }

    @Synchronized
    override fun sample(): PlayerInput = state.sample(DIRECTIONS::get)

    @Synchronized
    fun reset() {
        state.reset()
    }

    private fun isGamepad(event: MotionEvent): Boolean =
        event.isFromSource(InputDevice.SOURCE_GAMEPAD) ||
            event.isFromSource(InputDevice.SOURCE_JOYSTICK)

    private fun isHandled(keyCode: Int): Boolean =
        keyCode in DIRECTIONS || keyCode in FIRE_BUTTONS

    private companion object {
        val DIRECTIONS: Map<Int, Dir> = mapOf(
            KeyEvent.KEYCODE_DPAD_UP to Dir.UP,
            KeyEvent.KEYCODE_DPAD_RIGHT to Dir.RIGHT,
            KeyEvent.KEYCODE_DPAD_DOWN to Dir.DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT to Dir.LEFT,
        )

        val FIRE_BUTTONS: Set<Int> = setOf(
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_BUTTON_L1,
            KeyEvent.KEYCODE_BUTTON_R1,
        )
    }
}
