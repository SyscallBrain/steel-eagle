package pt.tiago.steeleagle.flow

/** Stage grid: ten stages per page, five columns by two rows. */
class LevelGrid(
    val levelCount: Int,
    maxUnlocked: Int,
) {
    init {
        require(levelCount > 0) { "there must be at least one level" }
    }

    val availableCount: Int = maxUnlocked.coerceIn(1, levelCount)

    var selected: Int = 0
        private set

    val page: Int
        get() = selected / PAGE_SIZE

    val pageCount: Int
        get() = (availableCount + PAGE_SIZE - 1) / PAGE_SIZE

    val pageStart: Int
        get() = page * PAGE_SIZE

    val levelNumber: Int
        get() = selected + 1

    fun move(horizontal: Int, vertical: Int) {
        val target = (selected + horizontal + vertical * COLUMNS)
            .coerceIn(0, availableCount - 1)
        selected = target
    }

    fun selectIndex(index: Int) {
        if (index in 0 until availableCount) selected = index
    }

    /**
     * A cell index inside the current page maps to a stage number. Returns null
     * for a cell outside the grid or a locked stage.
     */
    fun stageAt(cellIndex: Int): Int? {
        if (cellIndex !in 0 until PAGE_SIZE) return null
        val stage = pageStart + cellIndex + 1
        return if (stage <= availableCount) stage else null
    }

    private companion object {
        const val COLUMNS = 5
        const val ROWS = 2
        const val PAGE_SIZE = COLUMNS * ROWS
    }
}
