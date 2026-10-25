package pt.tiago.steeleagle.flow

/** Arcade-style three letter initials entry, one slot at a time. */
class InitialsEntry(initial: String = "AAA") {
    private val letters = CharArray(SLOTS) { index ->
        initial.uppercase().getOrElse(index) { 'A' }.coerceIn('A', 'Z')
    }

    var slot: Int = 0
        private set

    val text: String
        get() = letters.concatToString()

    fun moveSlot(delta: Int) {
        slot = Math.floorMod(slot + delta, SLOTS)
    }

    fun changeLetter(delta: Int) {
        val current = letters[slot] - 'A'
        letters[slot] = 'A' + Math.floorMod(current + delta, ALPHABET)
    }

    /** Moves to the next slot; true when the last slot was confirmed. */
    fun confirm(): Boolean {
        if (slot < SLOTS - 1) {
            slot++
            return false
        }
        return true
    }

    fun selectSlot(index: Int) {
        require(index in 0 until SLOTS) { "slot out of range" }
        slot = index
    }

    private companion object {
        const val SLOTS = 3
        const val ALPHABET = 26
    }
}
