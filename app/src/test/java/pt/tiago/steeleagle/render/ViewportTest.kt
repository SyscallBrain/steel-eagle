package pt.tiago.steeleagle.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewportTest {

    @Test
    fun `fits a 20 by 9 phone with integer scaling`() {
        val viewport = Viewport.fit(2400, 1080)

        assertEquals(4, viewport.scale)
        assertEquals(1024, viewport.width)
        assertEquals(896, viewport.height)
        assertEquals(688, viewport.left)
        assertEquals(92, viewport.top)
    }

    @Test
    fun `fits a 16 by 10 tablet with integer scaling`() {
        val viewport = Viewport.fit(2560, 1600)

        assertEquals(7, viewport.scale)
        assertEquals(1792, viewport.width)
        assertEquals(1568, viewport.height)
        assertEquals(384, viewport.left)
        assertEquals(16, viewport.top)
    }

    @Test
    fun `fits a small landscape screen`() {
        val viewport = Viewport.fit(800, 480)

        assertEquals(2, viewport.scale)
        assertEquals(512, viewport.width)
        assertEquals(448, viewport.height)
        assertEquals(144, viewport.left)
        assertEquals(16, viewport.top)
    }

    @Test
    fun `has no letterbox at an exact multiple`() {
        val viewport = Viewport.fit(512, 448)

        assertEquals(2, viewport.scale)
        assertEquals(0, viewport.left)
        assertEquals(0, viewport.top)
    }

    @Test
    fun `never returns a scale below one`() {
        val viewport = Viewport.fit(100, 100)

        assertEquals(1, viewport.scale)
        assertTrue(viewport.width >= 256)
        assertTrue(viewport.height >= 224)
    }

    @Test
    fun `contains only points inside the scaled image`() {
        val viewport = Viewport.fit(2400, 1080)

        assertTrue(viewport.contains(688, 92))
        assertTrue(viewport.contains(688 + 1024 - 1, 92 + 896 - 1))
        assertTrue(!viewport.contains(687, 500))
        assertTrue(!viewport.contains(2000, 500))
    }
}
