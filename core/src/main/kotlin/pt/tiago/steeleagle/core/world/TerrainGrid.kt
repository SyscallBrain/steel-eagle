package pt.tiago.steeleagle.core.world

import pt.tiago.steeleagle.core.model.Dir

/** Terrain types of the 4 px destruction grid. */
enum class Terrain {
    EMPTY,
    BRICK,
    STEEL,
    WATER,
    TREES,
    ICE,
}

/**
 * 52x52 grid of 4 px terrain pieces. A level's 26x26 grid of 8 px cells
 * expands into four pieces per cell.
 */
class TerrainGrid private constructor(
    private val cells: IntArray,
    version: Int = 0,
) {
    var version: Int = version
        private set

    /** Cells changed since [drainChanges], in the 4 px grid. */
    private val changedCells = LinkedHashSet<Int>()

    fun drainChanges(): List<Int> = changedCells.toList().also { changedCells.clear() }

    /** Terrain of a 4 px cell addressed by its row-major index. */
    fun terrainAt(index: Int): Terrain = Terrain.entries[cells[index]]

    /** Applies one network delta cell; ignored when the index is out of range. */
    fun setAt(index: Int, terrain: Terrain) {
        if (index !in cells.indices) return

        set(index % SIZE, index / SIZE, terrain)
    }

    fun get(x: Int, y: Int): Terrain =
        if (x in 0 until SIZE && y in 0 until SIZE) {
            Terrain.entries[cells[y * SIZE + x]]
        } else {
            Terrain.EMPTY
        }

    fun set(x: Int, y: Int, terrain: Terrain) {
        if (x !in 0 until SIZE || y !in 0 until SIZE) return
        if (get(x, y) == terrain) return
        putRaw(x, y, terrain)
        changedCells += y * SIZE + x
        version++
    }

    /** Sets an entire 8 px level cell (four pieces). */
    fun set8(blockX: Int, blockY: Int, terrain: Terrain) {
        for (dy in 0..1) {
            for (dx in 0..1) {
                set(blockX * 2 + dx, blockY * 2 + dy, terrain)
            }
        }
    }

    fun blocksTank(x: Int, y: Int): Boolean = when (get(x, y)) {
        Terrain.BRICK, Terrain.STEEL, Terrain.WATER -> true
        else -> false
    }

    fun blocksBullet(x: Int, y: Int): Boolean = when (get(x, y)) {
        Terrain.BRICK, Terrain.STEEL -> true
        else -> false
    }

    /**
     * Removes an 8 px wide by 4 px deep strip around the hit piece. The depth
     * is the hit cell itself; the width is the perpendicular pair that belongs
     * to the same 8 px brick cell.
     */
    fun destroyBrick(hitX: Int, hitY: Int, direction: Dir) {
        when (direction) {
            Dir.LEFT, Dir.RIGHT -> {
                val y0 = hitY and EVEN_MASK
                set(hitX, y0, Terrain.EMPTY)
                set(hitX, y0 + 1, Terrain.EMPTY)
            }
            Dir.UP, Dir.DOWN -> {
                val x0 = hitX and EVEN_MASK
                set(x0, hitY, Terrain.EMPTY)
                set(x0 + 1, hitY, Terrain.EMPTY)
            }
        }
    }

    /** Removes a full 8 px cell (steel or trees with the level-3 weapon). */
    fun destroyCell8(hitX: Int, hitY: Int) {
        val x0 = hitX and EVEN_MASK
        val y0 = hitY and EVEN_MASK
        for (y in y0..y0 + 1) {
            for (x in x0..x0 + 1) {
                set(x, y, Terrain.EMPTY)
            }
        }
    }

    fun contentHash(): Int = cells.contentHashCode()

    /** Row-major terrain ordinals for a network full-state message. */
    fun toBytes(): ByteArray = ByteArray(cells.size) { cells[it].toByte() }

    /** Replaces every cell from a full network state without logging changes. */
    fun applyFull(bytes: ByteArray) {
        require(bytes.size == cells.size) {
            "terrain must have ${cells.size} cells, got ${bytes.size}"
        }
        for (index in bytes.indices) {
            putRaw(index % SIZE, index / SIZE, Terrain.entries[bytes[index].toInt()])
        }
        version++
    }

    private fun putRaw(x: Int, y: Int, terrain: Terrain) {
        if (x in 0 until SIZE && y in 0 until SIZE) {
            cells[y * SIZE + x] = terrain.ordinal
        }
    }

    companion object {
        const val SIZE = 52
        const val LEVEL_SIZE = 26
        private const val EVEN_MASK = -2

        fun empty(): TerrainGrid = TerrainGrid(IntArray(SIZE * SIZE))

        fun fromRows(rows: List<String>): TerrainGrid {
            require(rows.size == LEVEL_SIZE) { "level must have $LEVEL_SIZE rows" }
            val grid = empty()
            for (y in rows.indices) {
                val row = rows[y]
                require(row.length == LEVEL_SIZE) { "row $y must have $LEVEL_SIZE cells" }
                for (x in row.indices) {
                    val terrain = terrainFor(row[x])
                    grid.putRaw(x * 2, y * 2, terrain)
                    grid.putRaw(x * 2 + 1, y * 2, terrain)
                    grid.putRaw(x * 2, y * 2 + 1, terrain)
                    grid.putRaw(x * 2 + 1, y * 2 + 1, terrain)
                }
            }
            return grid
        }

        private fun terrainFor(char: Char): Terrain = when (char) {
            '.' -> Terrain.EMPTY
            'B' -> Terrain.BRICK
            'S' -> Terrain.STEEL
            'W' -> Terrain.WATER
            'T' -> Terrain.TREES
            'I' -> Terrain.ICE
            else -> throw IllegalArgumentException("unknown terrain character '$char'")
        }
    }
}
