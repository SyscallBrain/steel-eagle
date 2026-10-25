package pt.tiago.steeleagle.core.world

import pt.tiago.steeleagle.core.model.Fixed
import kotlin.test.Test
import kotlin.test.assertEquals

class TankTest {

    @Test
    fun `track frame toggles every four movement ticks`() {
        val tank = Tank(x = 0, y = 0)

        repeat(3) { tank.advanceTrack() }
        assertEquals(0, tank.trackFrame)

        tank.advanceTrack()
        assertEquals(1, tank.trackFrame)

        repeat(4) { tank.advanceTrack() }
        assertEquals(0, tank.trackFrame)
    }

    @Test
    fun `tank rect is sixteen pixels wide`() {
        val tank = Tank(
            x = 32 * Fixed.SUBPIXELS,
            y = 48 * Fixed.SUBPIXELS,
        )

        assertEquals(32 * Fixed.SUBPIXELS, tank.left)
        assertEquals(48 * Fixed.SUBPIXELS, tank.top)
        assertEquals(48 * Fixed.SUBPIXELS, tank.right)
        assertEquals(64 * Fixed.SUBPIXELS, tank.bottom)
    }
}
