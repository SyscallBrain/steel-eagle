package pt.tiago.steeleagle.flow

/**
 * On-screen IPv4 keyboard for the manual LAN join screen: digits, dot, DEL and
 * OK in a 4-column grid. Kept free of Android for the JVM tests.
 */
class IpEntry(initial: String = "") {

    enum class Action { APPEND, DELETE, CONFIRM, NONE }

    var text: String = initial.filter { it.isDigit() || it == '.' }.take(MAX_LENGTH)
        private set

    var cursor: Int = 0
        private set

    val cells: List<String> = CELLS

    fun move(dx: Int, dy: Int) {
        if (dx == 0 && dy == 0) return
        val row = cursor / COLUMNS
        val column = cursor % COLUMNS
        val nextRow = row + dy
        if (nextRow !in 0 until ROWS) return
        var nextColumn = column + dx
        if (nextColumn !in 0 until COLUMNS) return
        if (nextRow == ROWS - 1 && nextColumn >= LAST_ROW_CELLS) {
            nextColumn = LAST_ROW_CELLS - 1
        }
        cursor = nextRow * COLUMNS + nextColumn
    }

    /** Touch path: move to a cell and press it in one tap. */
    fun selectCell(index: Int): Action {
        if (index !in cells.indices) return Action.NONE
        move(index % COLUMNS - cursor % COLUMNS, index / COLUMNS - cursor / COLUMNS)
        return select()
    }

    fun select(): Action = when (val label = cells[cursor]) {
        "DEL" -> {
            if (text.isNotEmpty()) text = text.dropLast(1)
            Action.DELETE
        }

        "OK" -> Action.CONFIRM
        else -> {
            if (text.length < MAX_LENGTH) text += label
            Action.APPEND
        }
    }

    fun setText(value: String) {
        text = value.filter { it.isDigit() || it == '.' }.take(MAX_LENGTH)
    }

    /** The typed address, or null when it is not a valid IPv4 literal. */
    fun result(): String? {
        val parts = text.split('.')
        if (parts.size != 4) return null
        for (part in parts) {
            if (part.isEmpty() || part.length > 3) return null
            val value = part.toIntOrNull() ?: return null
            if (value !in 0..255) return null
        }
        return text
    }

    private companion object {
        const val COLUMNS = 4
        const val ROWS = 4
        const val LAST_ROW_CELLS = 1
        const val MAX_LENGTH = 15

        val CELLS = listOf(
            "1", "2", "3", "4",
            "5", "6", "7", "8",
            "9", "0", ".", "DEL",
            "OK",
        )
    }
}
