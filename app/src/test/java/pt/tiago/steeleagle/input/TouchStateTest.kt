package pt.tiago.steeleagle.input

import pt.tiago.steeleagle.core.model.Dir
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchStateTest {

    private val layout = TouchLayout(
        dpad = ControlCircle(centerX = 100, centerY = 100, radius = 50),
        fire = ControlCircle(centerX = 300, centerY = 100, radius = 40),
        overlay = false,
    )

    @Test
    fun `dpad and fire work with two pointers`() {
        val state = TouchState()
        state.setLayout(layout)

        state.onDown(pointerId = 0, x = 140, y = 100)
        state.onDown(pointerId = 1, x = 300, y = 100)

        assertEquals(Dir.RIGHT, state.sample().direction)
        assertTrue(state.sample().fire)

        state.onUp(pointerId = 1)
        assertEquals(Dir.RIGHT, state.sample().direction)
        assertFalse(state.sample().fire)

        state.onUp(pointerId = 0)
        assertNull(state.sample().direction)
    }

    @Test
    fun `moving the dpad pointer updates the direction`() {
        val state = TouchState()
        state.setLayout(layout)
        state.onDown(pointerId = 0, x = 140, y = 100)

        state.onMove(pointerId = 0, x = 60, y = 100)

        assertEquals(Dir.LEFT, state.sample().direction)
    }

    @Test
    fun `moving another pointer does not steer`() {
        val state = TouchState()
        state.setLayout(layout)
        state.onDown(pointerId = 0, x = 140, y = 100)

        state.onMove(pointerId = 1, x = 60, y = 100)

        assertEquals(Dir.RIGHT, state.sample().direction)
    }

    @Test
    fun `the centre of the dpad is neutral`() {
        val state = TouchState()
        state.setLayout(layout)

        state.onDown(pointerId = 0, x = 100, y = 100)

        assertNull(state.sample().direction)
    }

    @Test
    fun `cancel releases both controls`() {
        val state = TouchState()
        state.setLayout(layout)
        state.onDown(pointerId = 0, x = 140, y = 100)
        state.onDown(pointerId = 1, x = 300, y = 100)

        state.cancel()

        assertEquals(false, state.sample().fire)
        assertNull(state.sample().direction)
    }

    @Test
    fun `changing the layout releases held controls`() {
        val state = TouchState()
        state.setLayout(layout)
        state.onDown(pointerId = 0, x = 140, y = 100)
        state.onDown(pointerId = 1, x = 300, y = 100)

        state.setLayout(layout.copy(overlay = true))

        assertEquals(false, state.sample().fire)
        assertNull(state.sample().direction)
    }

    @Test
    fun `a b mode also fires on the rim of button a`() {
        val state = TouchState()
        val ab = layout.copy(abButtons = true)
        state.setLayout(ab)

        // Inside A, just outside the plain fire circle.
        state.onDown(pointerId = 0, x = 333, y = 75)

        assertTrue(state.sample().fire)
        assertFalse(layout.fireContains(333, 75))
    }

    @Test
    fun `a touch outside the controls is ignored`() {
        val state = TouchState()
        state.setLayout(layout)

        state.onDown(pointerId = 0, x = 10, y = 10)

        assertNull(state.sample().direction)
        assertEquals(false, state.sample().fire)
    }
}
