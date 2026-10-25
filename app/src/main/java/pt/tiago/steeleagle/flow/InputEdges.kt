package pt.tiago.steeleagle.flow

import pt.tiago.steeleagle.core.model.Dir
import pt.tiago.steeleagle.core.model.PlayerInput

/** Edge-triggered view of the player input: one action per press. */
data class InputEdgesFrame(
    val direction: Dir?,
    val fire: Boolean,
)

/**
 * Turns the held PlayerInput into menu actions. A fire press is consumed once
 * and stays locked until the button is released, so switching screens while
 * holding fire never activates the next screen by accident.
 */
class InputEdges {
    private var lastDirection: Dir? = null
    private var lastFire = false
    private var fireLocked = false

    fun update(input: PlayerInput): InputEdgesFrame {
        val direction = if (input.direction != null && input.direction != lastDirection) {
            input.direction
        } else {
            null
        }

        if (!input.fire) fireLocked = false
        val fire = input.fire && !lastFire && !fireLocked

        lastDirection = input.direction
        lastFire = input.fire
        return InputEdgesFrame(direction, fire)
    }

    fun lockFire() {
        fireLocked = true
    }

    fun reset() {
        lastDirection = null
        lastFire = false
        fireLocked = false
    }
}
