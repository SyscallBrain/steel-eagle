package pt.tiago.steeleagle.core.time

/**
 * Fixed-timestep accumulator for a deterministic 60 Hz simulation.
 * Deltas are integer nanoseconds; no floating point is involved.
 */
class FixedStepClock(
    ticksPerSecond: Int = 60,
    private val maxStepsPerAdvance: Int = 5,
) {
    private val stepNanos: Long = 1_000_000_000L / ticksPerSecond
    private var accumulator: Long = 0L

    init {
        require(ticksPerSecond > 0) { "ticksPerSecond must be positive" }
        require(maxStepsPerAdvance > 0) { "maxStepsPerAdvance must be positive" }
    }

    /**
     * Adds [deltaNanos] to the accumulator and returns how many simulation
     * steps should run. Excess backlog beyond [maxStepsPerAdvance] is dropped
     * so a slow frame cannot cause a spiral of death.
     */
    fun advance(deltaNanos: Long): Int {
        if (deltaNanos <= 0L) return 0

        accumulator += deltaNanos
        var steps = 0
        while (accumulator >= stepNanos && steps < maxStepsPerAdvance) {
            accumulator -= stepNanos
            steps++
        }
        if (steps == maxStepsPerAdvance) {
            accumulator %= stepNanos
        }
        return steps
    }
}
