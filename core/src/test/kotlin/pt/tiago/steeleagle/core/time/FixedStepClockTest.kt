package pt.tiago.steeleagle.core.time

import kotlin.test.Test
import kotlin.test.assertEquals

class FixedStepClockTest {

    @Test
    fun `produces one step per sixtieth of a second`() {
        val clock = FixedStepClock()

        assertEquals(1, clock.advance(16_666_667L))
    }

    @Test
    fun `produces sixty steps for one second`() {
        val clock = FixedStepClock()
        var total = 0

        repeat(60) {
            total += clock.advance(1_000_000_000L / 60)
        }

        assertEquals(60, total)
    }

    @Test
    fun `accumulates deltas smaller than one step`() {
        val clock = FixedStepClock()

        assertEquals(0, clock.advance(8_000_000L))
        assertEquals(1, clock.advance(9_000_000L))
    }

    @Test
    fun `ignores zero and negative deltas`() {
        val clock = FixedStepClock()

        assertEquals(0, clock.advance(0L))
        assertEquals(0, clock.advance(-1_000_000L))
        assertEquals(0, clock.advance(1_000_000L))
    }

    @Test
    fun `caps catch-up steps and drops the backlog`() {
        val clock = FixedStepClock(maxStepsPerAdvance = 5)

        assertEquals(5, clock.advance(1_000_000_000L))
        assertEquals(1, clock.advance(16_666_667L))
    }
}
