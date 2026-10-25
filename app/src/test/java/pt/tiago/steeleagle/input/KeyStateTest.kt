package pt.tiago.steeleagle.input

import pt.tiago.steeleagle.core.model.Dir
import pt.tiago.steeleagle.core.model.PlayerInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyStateTest {

    private val directions = mapOf(
        1 to Dir.UP,
        2 to Dir.RIGHT,
        3 to Dir.UP,
        4 to Dir.DOWN,
    )

    @Test
    fun `last pressed direction wins`() {
        val state = KeyState()

        state.press(1)
        state.press(2)
        assertEquals(Dir.RIGHT, state.sample(directions::get).direction)

        state.release(2)
        assertEquals(Dir.UP, state.sample(directions::get).direction)
    }

    @Test
    fun `an alias keeps the direction held`() {
        val state = KeyState()

        state.press(1)
        state.press(3)
        state.release(1)

        assertEquals(Dir.UP, state.sample(directions::get).direction)

        state.release(3)
        assertEquals(null, state.sample(directions::get).direction)
    }

    @Test
    fun `fire stays on until every fire key is released`() {
        val state = KeyState()

        state.pressFire(10)
        state.pressFire(11)
        state.releaseFire(10)

        assertTrue(state.sample(directions::get).fire)

        state.releaseFire(11)
        assertFalse(state.sample(directions::get).fire)
    }

    @Test
    fun `reset clears directions and fire`() {
        val state = KeyState()
        state.press(1)
        state.pressFire(10)

        state.reset()

        assertEquals(PlayerInput.NONE, state.sample(directions::get))
    }
}
