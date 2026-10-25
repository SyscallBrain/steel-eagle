package pt.tiago.steeleagle.flow

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TapGuardTest {

    @Test
    fun `blocks for the requested number of ticks`() {
        val guard = TapGuard(durationTicks = 3)

        assertFalse(guard.blocks())
        guard.arm()
        assertTrue(guard.blocks())

        guard.tick()
        guard.tick()
        assertTrue(guard.blocks())
        guard.tick()
        assertFalse(guard.blocks())
    }

    @Test
    fun `a new transition restarts the lockout`() {
        val guard = TapGuard(durationTicks = 3)
        guard.arm()
        guard.tick()

        guard.arm()
        guard.tick()
        guard.tick()
        assertTrue(guard.blocks())
    }

    @Test
    fun `reset releases the guard immediately`() {
        val guard = TapGuard()
        guard.arm()

        guard.reset()

        assertFalse(guard.blocks())
    }
}
