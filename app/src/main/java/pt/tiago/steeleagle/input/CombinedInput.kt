package pt.tiago.steeleagle.input

import pt.tiago.steeleagle.core.model.Dir
import pt.tiago.steeleagle.core.model.PlayerInput

/** Anything that can contribute one player's input. */
interface InputSource {
    fun sample(): PlayerInput
}

/**
 * Merges sources in priority order: the first non-null direction wins and fire
 * is OR-ed. The view builds this as gamepad, keyboard, touch.
 */
class CombinedInput(private val sources: List<InputSource>) : InputSource {
    override fun sample(): PlayerInput {
        var direction: Dir? = null
        var fire = false
        for (source in sources) {
            val input = source.sample()
            if (direction == null) direction = input.direction
            fire = fire || input.fire
        }
        return PlayerInput(direction, fire)
    }
}
