package pt.tiago.steeleagle.core.world

import pt.tiago.steeleagle.core.model.Dir
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TerrainGridTest {

    @Test
    fun `expands an 8 px level cell into four 4 px pieces`() {
        val rows = MutableList(26) { ".".repeat(26) }
        rows[10] = ".".repeat(10) + "B" + ".".repeat(15)

        val grid = TerrainGrid.fromRows(rows)

        assertEquals(Terrain.BRICK, grid.get(20, 20))
        assertEquals(Terrain.BRICK, grid.get(21, 20))
        assertEquals(Terrain.BRICK, grid.get(20, 21))
        assertEquals(Terrain.BRICK, grid.get(21, 21))
        assertEquals(Terrain.EMPTY, grid.get(22, 20))
    }

    @Test
    fun `horizontal brick hit removes an 8 by 4 strip`() {
        val grid = TerrainGrid.empty()
        grid.set8(10, 10, Terrain.BRICK)

        grid.destroyBrick(20, 20, Dir.RIGHT)

        assertEquals(Terrain.EMPTY, grid.get(20, 20))
        assertEquals(Terrain.EMPTY, grid.get(20, 21))
        assertEquals(Terrain.BRICK, grid.get(21, 20))
        assertEquals(Terrain.BRICK, grid.get(21, 21))
    }

    @Test
    fun `brick hit on an odd coordinate still removes the right pair`() {
        val grid = TerrainGrid.empty()
        grid.set8(10, 10, Terrain.BRICK)

        grid.destroyBrick(21, 21, Dir.RIGHT)

        assertEquals(Terrain.EMPTY, grid.get(21, 20))
        assertEquals(Terrain.EMPTY, grid.get(21, 21))
        assertEquals(Terrain.BRICK, grid.get(20, 20))
        assertEquals(Terrain.BRICK, grid.get(20, 21))
    }

    @Test
    fun `vertical brick hit removes an 8 by 4 strip`() {
        val grid = TerrainGrid.empty()
        grid.set8(10, 10, Terrain.BRICK)

        grid.destroyBrick(20, 20, Dir.DOWN)

        assertEquals(Terrain.EMPTY, grid.get(20, 20))
        assertEquals(Terrain.EMPTY, grid.get(21, 20))
        assertEquals(Terrain.BRICK, grid.get(20, 21))
        assertEquals(Terrain.BRICK, grid.get(21, 21))
    }

    @Test
    fun `destroying an 8 px cell clears all four pieces`() {
        val grid = TerrainGrid.empty()
        grid.set8(10, 10, Terrain.STEEL)

        grid.destroyCell8(21, 21)

        for (y in 20..21) {
            for (x in 20..21) {
                assertEquals(Terrain.EMPTY, grid.get(x, y))
            }
        }
    }

    @Test
    fun `terrain blocks the right actors`() {
        val grid = TerrainGrid.empty()
        grid.set8(1, 1, Terrain.BRICK)
        grid.set8(2, 1, Terrain.STEEL)
        grid.set8(3, 1, Terrain.WATER)
        grid.set8(4, 1, Terrain.TREES)
        grid.set8(5, 1, Terrain.ICE)

        assertTrue(grid.blocksTank(2, 2))
        assertTrue(grid.blocksBullet(2, 2))
        assertTrue(grid.blocksTank(4, 2))
        assertTrue(grid.blocksBullet(4, 2))
        assertTrue(grid.blocksTank(6, 2))
        assertTrue(!grid.blocksBullet(6, 2))
        assertTrue(!grid.blocksTank(8, 2))
        assertTrue(!grid.blocksBullet(8, 2))
        assertTrue(!grid.blocksTank(10, 2))
        assertTrue(!grid.blocksBullet(10, 2))
    }

    @Test
    fun `version changes only when terrain changes`() {
        val grid = TerrainGrid.empty()
        val before = grid.version

        grid.set(0, 0, Terrain.BRICK)
        assertNotEquals(before, grid.version)

        val after = grid.version
        grid.set(0, 0, Terrain.BRICK)
        assertEquals(after, grid.version)
    }

    @Test
    fun `content hash is stable for equal grids`() {
        val first = TerrainGrid.empty()
        val second = TerrainGrid.empty()
        first.set8(3, 4, Terrain.WATER)
        second.set8(3, 4, Terrain.WATER)

        assertEquals(first.contentHash(), second.contentHash())

        second.set8(3, 4, Terrain.EMPTY)
        assertNotEquals(first.contentHash(), second.contentHash())
    }
}
