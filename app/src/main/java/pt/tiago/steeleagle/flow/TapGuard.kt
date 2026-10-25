package pt.tiago.steeleagle.flow

/**
 * Short lockout after a screen change. It covers the platform double-tap
 * window so a tap that opens a screen cannot immediately trigger the item that
 * happens to sit under the same finger on the next screen.
 */
class TapGuard(private val durationTicks: Int = DEFAULT_TICKS) {

    var remaining: Int = 0
        private set

    fun arm() {
        remaining = durationTicks
    }

    fun tick() {
        if (remaining > 0) remaining--
    }

    fun blocks(): Boolean = remaining > 0

    fun reset() {
        remaining = 0
    }

    private companion object {
        const val DEFAULT_TICKS = 20
    }
}
