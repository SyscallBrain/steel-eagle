package pt.tiago.steeleagle.core.editor

import pt.tiago.steeleagle.core.level.Level

/**
 * Editable 26x26 level grid. A cell is the 8x8 px terrain block of the level
 * format; brushes stamp whole, half and quarter patterns and are clipped at
 * the field border.
 */
class EditorMap private constructor(
    private val cells: Array<CharArray>,
) {
    init {
        require(cells.size == SIZE) { "map must have $SIZE rows" }
        cells.forEach { row ->
            require(row.size == SIZE) { "row must have $SIZE cells" }
        }
    }

    constructor(rows: List<String>) : this(rows.map { it.toCharArray() }.toTypedArray())

    /** Bumped whenever a stamp changes a cell; the renderer uses it as a cache key. */
    var version: Int = 0
        private set

    fun get(x: Int, y: Int): Char =
        if (x in 0 until SIZE && y in 0 until SIZE) cells[y][x] else EMPTY

    /**
     * Stamps [brush] with its top-left corner at (x, y). Returns true when at
     * least one cell changed; cells outside the field are ignored.
     */
    fun stamp(brush: Brush, x: Int, y: Int): Boolean {
        var changed = false
        for (dy in 0 until brush.height) {
            for (dx in 0 until brush.width) {
                val cellX = x + dx
                val cellY = y + dy
                if (cellX !in 0 until SIZE || cellY !in 0 until SIZE) continue
                if (cells[cellY][cellX] == brush.terrain) continue
                cells[cellY][cellX] = brush.terrain
                changed = true
            }
        }
        if (changed) version++
        return changed
    }

    fun rows(): List<String> = cells.map { String(it) }

    fun copy(): EditorMap = EditorMap(cells.map { it.copyOf() }.toTypedArray())

    fun toLevel(name: String, enemies: String): Level = Level(
        name = name,
        enemies = enemies,
        map = rows(),
    )

    companion object {
        const val SIZE = 26
        const val EMPTY = '.'

        /** Empty field with the standard brick eagle fort, ready to draw on. */
        fun template(): EditorMap {
            val rows = MutableList(SIZE) { EMPTY.toString().repeat(SIZE) }
            for ((offset, row) in EAGLE_FORT.withIndex()) {
                rows[EAGLE_FORT_TOP + offset] = row
            }
            return EditorMap(rows)
        }

        fun fromLevel(level: Level): EditorMap = EditorMap(level.map)

        /** Rows 20..25 of the standard eagle wall, as in level 001. */
        private val EAGLE_FORT = listOf(
            "........BBBBBBBB..........",
            "........B......B..........",
            "........BBBBBBBB..........",
            "...........BBBB...........",
            "...........B..B...........",
            "...........B..B...........",
        )
        private const val EAGLE_FORT_TOP = 20
    }
}
