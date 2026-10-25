package pt.tiago.steeleagle.flow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LevelGridTest {

    @Test
    fun `clamps the unlocked count to the catalog`() {
        assertEquals(1, LevelGrid(levelCount = 1, maxUnlocked = 10).availableCount)
        assertEquals(3, LevelGrid(levelCount = 5, maxUnlocked = 3).availableCount)
        assertEquals(1, LevelGrid(levelCount = 5, maxUnlocked = 0).availableCount)
    }

    @Test
    fun `moves by row and column without leaving the unlocked stages`() {
        val grid = LevelGrid(levelCount = 12, maxUnlocked = 12)

        grid.move(1, 0)
        assertEquals(2, grid.levelNumber)
        grid.move(0, 1)
        assertEquals(7, grid.levelNumber)
        grid.move(-9, 0)
        assertEquals(1, grid.levelNumber)
        grid.move(0, -1)
        assertEquals(1, grid.levelNumber)
    }

    @Test
    fun `changes page after ten stages`() {
        val grid = LevelGrid(levelCount = 25, maxUnlocked = 25)
        repeat(10) { grid.move(1, 0) }

        assertEquals(11, grid.levelNumber)
        assertEquals(1, grid.page)
        assertEquals(11, grid.stageAt(0))
        assertEquals(12, grid.stageAt(1))
        assertEquals(13, grid.stageAt(2))
    }

    @Test
    fun `page buttons move by a full page`() {
        val grid = LevelGrid(levelCount = 25, maxUnlocked = 25)

        grid.move(10, 0)
        assertEquals(11, grid.levelNumber)
        assertEquals(1, grid.page)

        grid.move(-10, 0)
        assertEquals(1, grid.levelNumber)
        assertEquals(0, grid.page)
        assertEquals(3, grid.pageCount)
    }

    @Test
    fun `counts a partial last page`() {
        val grid = LevelGrid(levelCount = 12, maxUnlocked = 12)

        assertEquals(2, grid.pageCount)
        grid.move(10, 0)
        assertEquals(11, grid.levelNumber)
        assertEquals(1, grid.page)
    }

    @Test
    fun `fifty stages span five pages and reach stage fifty`() {
        val grid = LevelGrid(levelCount = 50, maxUnlocked = 50)

        assertEquals(5, grid.pageCount)
        assertEquals(1, grid.stageAt(0))
        assertEquals(10, grid.stageAt(9))

        grid.move(40, 0)

        assertEquals(41, grid.levelNumber)
        assertEquals(4, grid.page)
        assertEquals(50, grid.stageAt(9))
    }

    @Test
    fun `locked or out of page cells return null`() {
        val grid = LevelGrid(levelCount = 3, maxUnlocked = 3)

        assertEquals(1, grid.stageAt(0))
        assertEquals(3, grid.stageAt(2))
        assertNull(grid.stageAt(3))
        assertNull(grid.stageAt(-1))
        assertNull(grid.stageAt(10))
    }
}
