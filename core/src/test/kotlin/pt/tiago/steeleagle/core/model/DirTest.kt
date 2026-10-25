package pt.tiago.steeleagle.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DirTest {

    @Test
    fun `unit deltas point in the expected screen directions`() {
        assertEquals(0, Dir.UP.dx)
        assertEquals(-1, Dir.UP.dy)
        assertEquals(1, Dir.RIGHT.dx)
        assertEquals(0, Dir.RIGHT.dy)
        assertEquals(0, Dir.DOWN.dx)
        assertEquals(1, Dir.DOWN.dy)
        assertEquals(-1, Dir.LEFT.dx)
        assertEquals(0, Dir.LEFT.dy)
    }

    @Test
    fun `opposite flips both axes`() {
        assertEquals(Dir.DOWN, Dir.UP.opposite())
        assertEquals(Dir.LEFT, Dir.RIGHT.opposite())
        assertEquals(Dir.UP, Dir.DOWN.opposite())
        assertEquals(Dir.RIGHT, Dir.LEFT.opposite())
    }

    @Test
    fun `fromVector picks the dominant axis`() {
        assertEquals(Dir.RIGHT, Dir.fromVector(10, 2))
        assertEquals(Dir.LEFT, Dir.fromVector(-10, 2))
        assertEquals(Dir.DOWN, Dir.fromVector(1, 10))
        assertEquals(Dir.UP, Dir.fromVector(1, -10))
    }

    @Test
    fun `fromVector prefers horizontal on a tie`() {
        assertEquals(Dir.RIGHT, Dir.fromVector(5, 5))
        assertEquals(Dir.LEFT, Dir.fromVector(-5, -5))
    }

    @Test
    fun `fromVector returns null for a zero vector`() {
        assertNull(Dir.fromVector(0, 0))
    }
}
