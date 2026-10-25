package pt.tiago.steeleagle.core.editor

/**
 * In-game level-name keyboard: 10 columns of letters/digits plus SPACE, DEL and
 * OK. Used by the editor save screen so no system dialog is needed.
 */
class NameEntry(initial: String = "") {

    enum class Action { APPEND, DELETE, CONFIRM, NONE }

    var text: String = initial.uppercase().take(MAX_LENGTH)
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

    fun select(): Action = when (val label = cells[cursor]) {
        "SPACE" -> {
            if (text.length < MAX_LENGTH) text += " "
            Action.APPEND
        }

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
        text = value.uppercase().take(MAX_LENGTH)
    }

    /** Trimmed name, or null when the field is blank. */
    fun result(): String? = text.trim().takeIf { it.isNotEmpty() }

    private companion object {
        const val COLUMNS = 10
        const val ROWS = 4
        const val LAST_ROW_CELLS = 9
        const val MAX_LENGTH = 12

        val CELLS = buildList {
            for (char in 'A'..'Z') add(char.toString())
            for (digit in '0'..'9') add(digit.toString())
            add("SPACE")
            add("DEL")
            add("OK")
        }
    }
}
