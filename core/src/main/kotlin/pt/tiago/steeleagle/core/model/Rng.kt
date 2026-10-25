package pt.tiago.steeleagle.core.model

/**
 * Deterministic xorshift32 random number generator. Seed 0 is remapped because
 * xorshift cannot leave the all-zero state.
 */
class Rng(seed: Int) {
    var state: Int = if (seed == 0) FALLBACK_SEED else seed
        private set

    fun nextInt(): Int {
        var value = state
        value = value xor (value shl 13)
        value = value xor (value ushr 17)
        value = value xor (value shl 5)
        state = value
        return value
    }

    fun nextInt(bound: Int): Int {
        require(bound > 0) { "bound must be positive" }
        return (nextInt() ushr 1) % bound
    }

    private companion object {
        const val FALLBACK_SEED = 0x1F123BB5
    }
}
