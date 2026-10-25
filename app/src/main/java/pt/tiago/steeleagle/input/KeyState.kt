package pt.tiago.steeleagle.input

import pt.tiago.steeleagle.core.model.Dir
import pt.tiago.steeleagle.core.model.PlayerInput

/**
 * Pure key state: tracks physical key codes in press order and the set of fire
 * keys. Android key codes stay in the adapters so this can be tested on the JVM.
 */
class KeyState {

    private val held = mutableListOf<Int>()
    private val fireKeys = mutableSetOf<Int>()
    private var fire = false

    fun press(keyCode: Int) {
        held.remove(keyCode)
        held.add(keyCode)
    }

    fun release(keyCode: Int) {
        held.remove(keyCode)
    }

    fun pressFire(keyCode: Int) {
        fireKeys.add(keyCode)
        fire = true
    }

    fun releaseFire(keyCode: Int) {
        fireKeys.remove(keyCode)
        fire = fireKeys.isNotEmpty()
    }

    fun sample(directionFor: (Int) -> Dir?): PlayerInput =
        PlayerInput(held.lastOrNull()?.let(directionFor), fire)

    fun reset() {
        held.clear()
        fireKeys.clear()
        fire = false
    }
}
