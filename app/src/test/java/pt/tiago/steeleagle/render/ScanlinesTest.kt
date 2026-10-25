package pt.tiago.steeleagle.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanlinesTest {

    @Test
    fun `one line per logical row at the bottom of its scaled row`() {
        val points = Scanlines.points(left = 10, top = 6, width = 512, height = 448, rows = 224)

        assertEquals(224 * 4, points.size)
        assertEquals(10f, points[0])
        assertEquals(7f, points[1])
        assertEquals(521f, points[2])
        assertEquals(7f, points[3])

        val last = (224 - 1) * 4
        assertEquals(6f + 224 * 2 - 1f, points[last + 1])
    }

    @Test
    fun `an image without at least two pixels per row produces no lines`() {
        assertEquals(0, Scanlines.points(0, 0, 320, 100, rows = 224).size)
        assertEquals(0, Scanlines.points(0, 0, 0, 448, rows = 224).size)
        assertEquals(0, Scanlines.points(0, 0, 320, 224, rows = 224).size)
    }

    @Test
    fun `lines stay inside the destination rectangle`() {
        val points = Scanlines.points(4, 8, 300, 448, rows = 224)

        assertEquals(224 * 4, points.size)
        for (index in points.indices step 4) {
            assertEquals(4f, points[index])
            assertEquals(303f, points[index + 2])
            assertTrue(points[index + 1] >= 8f)
            assertTrue(points[index + 1] < 8f + 448f)
        }
    }
}
