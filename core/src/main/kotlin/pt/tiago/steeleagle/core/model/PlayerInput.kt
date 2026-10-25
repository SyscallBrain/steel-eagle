package pt.tiago.steeleagle.core.model

/** One player's input for a single simulation tick. */
data class PlayerInput(
    val direction: Dir?,
    val fire: Boolean,
) {
    companion object {
        val NONE = PlayerInput(direction = null, fire = false)
    }
}
