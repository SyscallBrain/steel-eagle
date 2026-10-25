package pt.tiago.steeleagle.input

import android.view.KeyEvent
import pt.tiago.steeleagle.core.model.Dir
import pt.tiago.steeleagle.core.model.PlayerInput

/**
 * Arrow keys (and WASD) plus space/enter for development on a keyboard.
 * The most recently pressed held direction wins, like on the NES.
 */
class KeyboardInput : InputSource {

    private val state = KeyState()

    @Synchronized
    fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.repeatCount > 0) return isHandled(keyCode)
        return when {
            keyCode in DIRECTIONS -> {
                state.press(keyCode)
                true
            }
            keyCode == KeyEvent.KEYCODE_SPACE || keyCode == KeyEvent.KEYCODE_ENTER -> {
                state.pressFire(keyCode)
                true
            }
            else -> false
        }
    }

    @Synchronized
    fun onKeyUp(keyCode: Int): Boolean = when {
        keyCode in DIRECTIONS -> {
            state.release(keyCode)
            true
        }
        keyCode == KeyEvent.KEYCODE_SPACE || keyCode == KeyEvent.KEYCODE_ENTER -> {
            state.releaseFire(keyCode)
            true
        }
        else -> false
    }

    @Synchronized
    override fun sample(): PlayerInput = state.sample(DIRECTIONS::get)

    @Synchronized
    fun reset() {
        state.reset()
    }

    private fun isHandled(keyCode: Int): Boolean =
        keyCode in DIRECTIONS ||
            keyCode == KeyEvent.KEYCODE_SPACE ||
            keyCode == KeyEvent.KEYCODE_ENTER

    private companion object {
        val DIRECTIONS: Map<Int, Dir> = mapOf(
            KeyEvent.KEYCODE_DPAD_UP to Dir.UP,
            KeyEvent.KEYCODE_W to Dir.UP,
            KeyEvent.KEYCODE_DPAD_RIGHT to Dir.RIGHT,
            KeyEvent.KEYCODE_D to Dir.RIGHT,
            KeyEvent.KEYCODE_DPAD_DOWN to Dir.DOWN,
            KeyEvent.KEYCODE_S to Dir.DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT to Dir.LEFT,
            KeyEvent.KEYCODE_A to Dir.LEFT,
        )
    }
}
