package pt.tiago.steeleagle.input

import pt.tiago.steeleagle.core.model.Dir
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GamepadStateTest {

    private val directions = mapOf(1 to Dir.UP, 2 to Dir.RIGHT)

    @Test
    fun `stick and hat are quantized with a dead zone`() {
        assertNull(GamepadState.quantize(0.4f, 0f))
        assertNull(GamepadState.quantize(0f, -0.4f))
        assertEquals(Dir.RIGHT, GamepadState.quantize(0.6f, 0f))
        assertEquals(Dir.UP, GamepadState.quantize(0f, -0.9f))
        assertEquals(Dir.DOWN, GamepadState.quantize(0.6f, 0.7f))
        assertEquals(Dir.RIGHT, GamepadState.quantize(0.9f, 0.8f))
        assertEquals(Dir.LEFT, GamepadState.quantize(-0.6f, 0.2f))
    }

    @Test
    fun `pressed d-pad direction wins over the stick`() {
        val state = GamepadState()
        state.setAxes(x = 0.9f, y = 0f, hatX = 0f, hatY = 0f)
        state.press(1)

        assertEquals(Dir.UP, state.sample(directions::get).direction)

        state.release(1)
        assertEquals(Dir.RIGHT, state.sample(directions::get).direction)
    }

    @Test
    fun `hat wins over the stick`() {
        val state = GamepadState()
        state.setAxes(x = 0.9f, y = 0f, hatX = 0f, hatY = -1f)

        assertEquals(Dir.UP, state.sample(directions::get).direction)
    }

    @Test
    fun `fire stays on while any button is held`() {
        val state = GamepadState()

        state.pressFire(10)
        state.pressFire(11)
        state.releaseFire(10)
        assertTrue(state.sample(directions::get).fire)

        state.releaseFire(11)
        assertEquals(false, state.sample(directions::get).fire)
    }

    @Test
    fun `reset clears axes and buttons`() {
        val state = GamepadState()
        state.setAxes(0.9f, 0f, 0f, 0f)
        state.pressFire(10)

        state.reset()

        assertNull(state.sample(directions::get).direction)
        assertEquals(false, state.sample(directions::get).fire)
    }
}
