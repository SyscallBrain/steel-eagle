package pt.tiago.steeleagle.input

import pt.tiago.steeleagle.core.model.Dir
import pt.tiago.steeleagle.core.model.PlayerInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class JoystickStateTest {

    private val layout = TouchLayout(
        dpad = ControlCircle(centerX = 200, centerY = 200, radius = 100),
        fire = ControlCircle(centerX = 600, centerY = 200, radius = 60),
        overlay = false,
        opacity = 70,
        sizeStep = 1,
        joystickZone = ControlArea(0, 0, 500, 400),
    )

    @Test
    fun `a touch in the zone starts a floating base`() {
        val state = JoystickState()
        state.setLayout(layout)

        state.onDown(pointerId = 0, x = 180, y = 220)

        assertEquals(PlayerInput.NONE, state.sample())
        assertEquals(
            JoystickVisual(active = true, baseX = 180, baseY = 220, knobX = 180, knobY = 220),
            state.visual(),
        )
    }

    @Test
    fun `moving the finger quantizes the dominant axis`() {
        val state = JoystickState()
        state.setLayout(layout)
        state.onDown(0, 200, 200)

        state.onMove(0, 320, 210)
        assertEquals(Dir.RIGHT, state.sample().direction)

        state.onMove(0, 205, 80)
        assertEquals(Dir.UP, state.sample().direction)

        // Neutral is wherever the (trailing) base is now.
        val base = requireNotNull(state.visual())
        state.onMove(0, base.baseX + 3, base.baseY - 3)
        assertNull(state.sample().direction)
    }

    @Test
    fun `the base follows a finger that leaves the ring`() {
        val state = JoystickState()
        state.setLayout(layout)
        state.onDown(0, 200, 200)

        state.onMove(0, 450, 200)

        val visual = requireNotNull(state.visual())
        assertEquals(350, visual.baseX)
        assertEquals(200, visual.baseY)
        assertEquals(450, visual.knobX)
        assertEquals(Dir.RIGHT, state.sample().direction)
    }

    @Test
    fun `after an overshoot a short move back reverses the direction`() {
        val state = JoystickState()
        state.setLayout(layout)
        state.onDown(0, 200, 200)
        state.onMove(0, 450, 200)

        // 50 px back from the far edge: the old fixed base (x 200) would
        // still read RIGHT; the trailing base (x 350) reads LEFT.
        state.onMove(0, 330, 200)

        assertEquals(Dir.LEFT, state.sample().direction)
    }

    @Test
    fun `diagonal jitter keeps the held direction`() {
        val state = JoystickState()
        state.setLayout(layout)
        state.onDown(0, 200, 200)
        state.onMove(0, 260, 240)
        assertEquals(Dir.RIGHT, state.sample().direction)

        // Just past 45 degrees: the plain dominant-axis rule would flip to DOWN.
        state.onMove(0, 260, 265)
        assertEquals(Dir.RIGHT, state.sample().direction)

        state.onMove(0, 240, 280)
        assertEquals(Dir.DOWN, state.sample().direction)
    }

    @Test
    fun `a b buttons fire from either button`() {
        val state = JoystickState()
        val ab = layout.copy(abButtons = true)
        state.setLayout(ab)

        state.onDown(1, ab.buttonB.centerX, ab.buttonB.centerY)
        assertEquals(true, state.sample().fire)
        state.onUp(1)
        state.onDown(2, ab.buttonA.centerX, ab.buttonA.centerY)
        assertEquals(true, state.sample().fire)
    }

    @Test
    fun `fire uses a second pointer and survives joystick movement`() {
        val state = JoystickState()
        state.setLayout(layout)
        state.onDown(0, 200, 200)
        state.onMove(0, 300, 200)

        state.onDown(1, 600, 200)
        assertEquals(PlayerInput(Dir.RIGHT, true), state.sample())

        state.onUp(1)
        assertEquals(PlayerInput(Dir.RIGHT, false), state.sample())

        state.onUp(0)
        assertEquals(PlayerInput.NONE, state.sample())
    }

    @Test
    fun `touches outside the zone and fire are ignored`() {
        val state = JoystickState()
        state.setLayout(layout)

        state.onDown(0, 550, 350)

        assertEquals(PlayerInput.NONE, state.sample())
        assertFalse(requireNotNull(state.visual()).active)
    }

    @Test
    fun `release and layout changes reset the stick`() {
        val state = JoystickState()
        state.setLayout(layout)
        state.onDown(0, 200, 200)
        state.onMove(0, 300, 200)
        state.onDown(1, 600, 200)

        state.cancel()

        assertEquals(PlayerInput.NONE, state.sample())
        assertFalse(requireNotNull(state.visual()).active)

        state.onDown(0, 200, 200)
        state.setLayout(layout.copy(opacity = 50))
        assertEquals(PlayerInput.NONE, state.sample())
        assertFalse(requireNotNull(state.visual()).active)
    }

    @Test
    fun `inactive layout shows the hint position`() {
        val state = JoystickState()
        state.setLayout(layout)

        val visual = requireNotNull(state.visual())

        assertFalse(visual.active)
        assertEquals(200, visual.baseX)
        assertEquals(200, visual.baseY)
    }
}
