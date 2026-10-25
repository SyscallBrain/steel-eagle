package pt.tiago.steeleagle.core.editor

import pt.tiago.steeleagle.core.level.Level
import pt.tiago.steeleagle.core.level.LevelValidator
import pt.tiago.steeleagle.core.level.UserLevel
import pt.tiago.steeleagle.core.rules.EnemyType

enum class EditorMode {
    FIELD,
    MENU,
    BRUSH,
    OPEN,
    ENEMIES,
    NAME,
    CONFIRM,
    MESSAGES,
}

enum class EditorCommand {
    UP,
    DOWN,
    LEFT,
    RIGHT,
    FIRE,
    BACK,
}

enum class EditorConfirm {
    NEW,
    OPEN,
    EXIT,
    DELETE,
}

/** Work the editor hands to the app: storage, playtest and leaving. */
sealed interface EditorEffect {
    data object ListLevels : EditorEffect
    data class Test(val level: Level) : EditorEffect
    data class Save(val fileName: String?, val level: Level) : EditorEffect
    data class Delete(val fileName: String) : EditorEffect
    data object Exit : EditorEffect
}

/**
 * Construction-mode state machine: field cursor, brush
 * palette, editor menu, open/save/delete, enemy roster, name entry and the
 * playtest hand-off. Kept free of Android so it can be tested on the JVM.
 */
class EditorSession(
    map: EditorMap = EditorMap.template(),
    enemies: EnemySetup = EnemySetup.allBasic(),
    name: String = "",
    fileName: String? = null,
) {
    var map: EditorMap = map
        private set

    var enemies: EnemySetup = enemies
        private set

    var name: String = name
        private set

    var fileName: String? = fileName
        private set

    var dirty: Boolean = false
        private set

    var mode: EditorMode = EditorMode.FIELD
        private set

    var cursorX: Int = START_CURSOR
        private set

    var cursorY: Int = START_CURSOR
        private set

    var brush: Brush = Brush.BRICK_FULL
        private set

    var menuIndex: Int = 0
        private set

    var openEntries: List<UserLevel> = emptyList()
        private set

    var openIndex: Int = 0
        private set

    var enemyIndex: Int = 0
        private set

    var confirm: EditorConfirm? = null
        private set

    var confirmIndex: Int = CONFIRM_NO
        private set

    var infoTitle: String = ""
        private set

    var messages: List<String> = emptyList()
        private set

    val nameEntry = NameEntry()

    val menuLabels: List<String> = MENU_LABELS

    val openPage: Int
        get() = openIndex / OPEN_PAGE_SIZE

    val openPageCount: Int
        get() = if (openEntries.isEmpty()) 1 else {
            (openEntries.size + OPEN_PAGE_SIZE - 1) / OPEN_PAGE_SIZE
        }

    val openPageStart: Int
        get() = openPage * OPEN_PAGE_SIZE

    fun handle(command: EditorCommand): EditorEffect? = when (mode) {
        EditorMode.FIELD -> handleField(command)
        EditorMode.MENU -> handleMenu(command)
        EditorMode.BRUSH -> handleBrush(command)
        EditorMode.OPEN -> handleOpen(command)
        EditorMode.ENEMIES -> handleEnemies(command)
        EditorMode.NAME -> handleName(command)
        EditorMode.CONFIRM -> handleConfirm(command)
        EditorMode.MESSAGES -> handleMessages(command)
    }

    // ------------------------------------------------------------------ field

    private fun handleField(command: EditorCommand): EditorEffect? {
        when (command) {
            EditorCommand.UP -> cursorY = (cursorY - 1).coerceAtLeast(0)
            EditorCommand.DOWN -> cursorY = (cursorY + 1).coerceAtMost(MAX_CELL)
            EditorCommand.LEFT -> cursorX = (cursorX - 1).coerceAtLeast(0)
            EditorCommand.RIGHT -> cursorX = (cursorX + 1).coerceAtMost(MAX_CELL)
            EditorCommand.FIRE -> paint()
            EditorCommand.BACK -> {
                mode = EditorMode.MENU
                menuIndex = 0
            }
        }
        return null
    }

    /** Moves the cursor to a cell and applies the brush, for touch editors. */
    fun paintAt(x: Int, y: Int) {
        if (mode != EditorMode.FIELD) return
        cursorX = x.coerceIn(0, MAX_CELL)
        cursorY = y.coerceIn(0, MAX_CELL)
        paint()
    }

    private fun paint() {
        if (map.stamp(brush, cursorX, cursorY)) dirty = true
    }

    // ------------------------------------------------------------------- menu

    private fun handleMenu(command: EditorCommand): EditorEffect? {
        when (command) {
            EditorCommand.UP -> menuIndex = Math.floorMod(menuIndex - 1, menuLabels.size)
            EditorCommand.DOWN -> menuIndex = Math.floorMod(menuIndex + 1, menuLabels.size)
            EditorCommand.FIRE -> return activateMenu(menuIndex)
            EditorCommand.BACK -> mode = EditorMode.FIELD
            EditorCommand.LEFT, EditorCommand.RIGHT -> Unit
        }
        return null
    }

    fun activateMenu(index: Int): EditorEffect? {
        menuIndex = index.coerceIn(0, menuLabels.lastIndex)
        return when (menuIndex) {
            MENU_NEW -> {
                if (dirty) requestConfirm(EditorConfirm.NEW) else newDocument()
                null
            }

            MENU_OPEN -> {
                if (dirty) {
                    requestConfirm(EditorConfirm.OPEN)
                    null
                } else {
                    requestOpen()
                }
            }

            MENU_SAVE -> save()
            MENU_ENEMIES -> {
                enemyIndex = 0
                mode = EditorMode.ENEMIES
                null
            }

            MENU_BRUSH -> {
                mode = EditorMode.BRUSH
                null
            }

            MENU_TEST -> test()
            MENU_DELETE -> {
                val open = fileName
                if (open == null) {
                    showMessages("CANNOT DELETE", listOf("NO SAVED LEVEL"))
                } else {
                    requestConfirm(EditorConfirm.DELETE)
                }
                null
            }

            MENU_EXIT -> if (dirty) {
                requestConfirm(EditorConfirm.EXIT)
                null
            } else {
                EditorEffect.Exit
            }

            else -> {
                mode = EditorMode.FIELD
                null
            }
        }
    }

    // ------------------------------------------------------------------ brush

    private fun handleBrush(command: EditorCommand): EditorEffect? {
        val index = brush.ordinal
        val row = index / PALETTE_COLUMNS
        val column = index % PALETTE_COLUMNS
        when (command) {
            EditorCommand.LEFT -> if (column > 0) selectBrush(index - 1)
            EditorCommand.RIGHT -> if (column < PALETTE_COLUMNS - 1) selectBrush(index + 1)
            EditorCommand.UP -> if (row > 0) selectBrush(index - PALETTE_COLUMNS)
            EditorCommand.DOWN -> if (row < PALETTE_ROWS - 1) selectBrush(index + PALETTE_COLUMNS)
            EditorCommand.FIRE -> mode = EditorMode.FIELD
            EditorCommand.BACK -> mode = EditorMode.FIELD
        }
        return null
    }

    fun selectBrush(index: Int) {
        brush = Brush.entries[index.coerceIn(0, Brush.entries.lastIndex)]
    }

    /** Picks a brush from the always-visible panel and returns to the field. */
    fun tapBrush(index: Int) {
        selectBrush(index)
        if (mode == EditorMode.BRUSH) mode = EditorMode.FIELD
    }

    // ------------------------------------------------------------------- open

    private fun handleOpen(command: EditorCommand): EditorEffect? {
        when (command) {
            EditorCommand.UP -> openIndex = (openIndex - 1).coerceAtLeast(0)
            EditorCommand.DOWN ->
                openIndex = (openIndex + 1).coerceAtMost((openEntries.size - 1).coerceAtLeast(0))
            EditorCommand.LEFT ->
                if (openPage > 0) openIndex = (openIndex - OPEN_PAGE_SIZE).coerceAtLeast(0)
            EditorCommand.RIGHT ->
                if (openPage < openPageCount - 1) {
                    openIndex = (openIndex + OPEN_PAGE_SIZE)
                        .coerceAtMost((openEntries.size - 1).coerceAtLeast(0))
                }
            EditorCommand.FIRE -> openLevelAt(openIndex)
            EditorCommand.BACK -> mode = EditorMode.MENU
        }
        return null
    }

    fun setOpenList(entries: List<UserLevel>) {
        openEntries = entries
        openIndex = openIndex.coerceIn(0, (entries.size - 1).coerceAtLeast(0))
        mode = EditorMode.OPEN
    }

    fun openLevelAt(index: Int) {
        val entry = openEntries.getOrNull(index) ?: return
        openIndex = index
        map = EditorMap.fromLevel(entry.level)
        enemies = runCatching { EnemySetup.fromSpec(entry.level.enemies) }
            .getOrDefault(EnemySetup.allBasic())
        name = entry.name
        fileName = entry.fileName
        dirty = false
        mode = EditorMode.FIELD
    }

    // ---------------------------------------------------------------- enemies

    private fun handleEnemies(command: EditorCommand): EditorEffect? {
        when (command) {
            EditorCommand.UP ->
                enemyIndex = Math.floorMod(enemyIndex - 1, EnemyType.entries.size)
            EditorCommand.DOWN ->
                enemyIndex = Math.floorMod(enemyIndex + 1, EnemyType.entries.size)
            EditorCommand.LEFT -> adjustEnemy(enemyIndex, -1)
            EditorCommand.RIGHT, EditorCommand.FIRE -> adjustEnemy(enemyIndex, 1)
            EditorCommand.BACK -> mode = EditorMode.MENU
        }
        return null
    }

    fun adjustEnemy(index: Int, delta: Int) {
        enemyIndex = index.coerceIn(0, EnemyType.entries.lastIndex)
        if (enemies.adjust(EnemyType.entries[enemyIndex], delta)) dirty = true
    }

    // ------------------------------------------------------------------- name

    private fun handleName(command: EditorCommand): EditorEffect? {
        when (command) {
            EditorCommand.UP -> nameEntry.move(0, -1)
            EditorCommand.DOWN -> nameEntry.move(0, 1)
            EditorCommand.LEFT -> nameEntry.move(-1, 0)
            EditorCommand.RIGHT -> nameEntry.move(1, 0)
            EditorCommand.FIRE -> if (nameEntry.select() == NameEntry.Action.CONFIRM) {
                return confirmName()
            }

            EditorCommand.BACK -> mode = EditorMode.MENU
        }
        return null
    }

    fun selectNameCell(index: Int): EditorEffect? {
        if (index !in nameEntry.cells.indices) return null
        nameEntry.move(
            dx = index % NAME_COLUMNS - nameEntry.cursor % NAME_COLUMNS,
            dy = index / NAME_COLUMNS - nameEntry.cursor / NAME_COLUMNS,
        )
        return if (nameEntry.select() == NameEntry.Action.CONFIRM) confirmName() else null
    }

    fun confirmName(): EditorEffect? {
        val result = nameEntry.result()
        if (result == null) {
            showMessages("NAME NEEDED", listOf("TYPE A NAME"))
            return null
        }
        name = result
        return saveNow(result)
    }

    // ---------------------------------------------------------------- confirm

    private fun handleConfirm(command: EditorCommand): EditorEffect? {
        when (command) {
            EditorCommand.LEFT, EditorCommand.RIGHT, EditorCommand.UP, EditorCommand.DOWN ->
                confirmIndex = if (confirmIndex == CONFIRM_YES) CONFIRM_NO else CONFIRM_YES

            EditorCommand.FIRE -> return chooseConfirm(confirmIndex == CONFIRM_YES)
            EditorCommand.BACK -> cancelConfirm()
        }
        return null
    }

    fun chooseConfirm(yes: Boolean): EditorEffect? {
        if (yes) {
            val action = confirm ?: return null
            confirm = null
            return when (action) {
                EditorConfirm.NEW -> {
                    newDocument()
                    null
                }

                EditorConfirm.OPEN -> requestOpen()

                EditorConfirm.EXIT -> EditorEffect.Exit
                EditorConfirm.DELETE -> {
                    val open = fileName
                    if (open == null) {
                        mode = EditorMode.MENU
                        null
                    } else {
                        EditorEffect.Delete(open)
                    }
                }
            }
        }
        cancelConfirm()
        return null
    }

    private fun cancelConfirm() {
        confirm = null
        mode = EditorMode.MENU
    }

    private fun requestConfirm(action: EditorConfirm) {
        confirm = action
        confirmIndex = CONFIRM_NO
        mode = EditorMode.CONFIRM
    }

    // --------------------------------------------------------------- messages

    private fun handleMessages(command: EditorCommand): EditorEffect? {
        when (command) {
            EditorCommand.FIRE, EditorCommand.BACK -> mode = EditorMode.MENU
            else -> Unit
        }
        return null
    }

    fun dismissMessages() {
        if (mode == EditorMode.MESSAGES) mode = EditorMode.MENU
    }

    /** Returns to the field from any overlay, used when entering the editor. */
    fun toField() {
        confirm = null
        mode = EditorMode.FIELD
    }

    private fun requestOpen(): EditorEffect {
        openIndex = 0
        mode = EditorMode.OPEN
        return EditorEffect.ListLevels
    }

    private fun showMessages(title: String, lines: List<String>) {
        infoTitle = title
        messages = lines.ifEmpty { listOf("UNKNOWN ERROR") }
        mode = EditorMode.MESSAGES
    }

    // ------------------------------------------------------------------ save

    private fun save(): EditorEffect? {
        nameEntry.setText(name.ifBlank { DEFAULT_NAME })
        mode = EditorMode.NAME
        return null
    }

    private fun saveNow(candidate: String): EditorEffect? {
        val level = map.toLevel(candidate, enemies.spec())
        val validation = LevelValidator.validate(level)
        if (!validation.isValid) {
            showMessages("CANNOT SAVE", EditorMessages.friendly(validation.errors))
            return null
        }
        return EditorEffect.Save(fileName, level)
    }

    private fun test(): EditorEffect? {
        val level = map.toLevel(name.ifBlank { DEFAULT_NAME }, enemies.spec())
        val validation = LevelValidator.validate(level)
        if (!validation.isValid) {
            showMessages("CANNOT TEST", EditorMessages.friendly(validation.errors))
            return null
        }
        return EditorEffect.Test(level)
    }

    fun onSaved(fileName: String, name: String) {
        this.fileName = fileName
        this.name = name
        dirty = false
        showMessages("SAVED", listOf(name))
    }

    fun onSaveFailed(message: String) {
        showMessages("CANNOT SAVE", EditorMessages.friendly(listOf(message)))
    }

    fun onDeleteFailed(message: String) {
        showMessages("CANNOT DELETE", EditorMessages.friendly(listOf(message)))
    }

    fun onDeleted() {
        val oldName = name
        fileName = null
        dirty = true
        showMessages("DELETED", listOf(oldName.ifBlank { "LEVEL" }))
    }

    // ------------------------------------------------------------------- new

    private fun newDocument() {
        map = EditorMap.template()
        enemies = EnemySetup.allBasic()
        name = ""
        fileName = null
        dirty = false
        cursorX = START_CURSOR
        cursorY = START_CURSOR
        brush = Brush.BRICK_FULL
        mode = EditorMode.FIELD
    }

    private companion object {
        const val START_CURSOR = 12
        const val MAX_CELL = EditorMap.SIZE - 1
        const val DEFAULT_NAME = "USER LEVEL"
        const val CONFIRM_YES = 0
        const val CONFIRM_NO = 1
        const val PALETTE_COLUMNS = 4
        const val PALETTE_ROWS = 3
        const val OPEN_PAGE_SIZE = 8
        const val NAME_COLUMNS = 10

        const val MENU_NEW = 0
        const val MENU_OPEN = 1
        const val MENU_SAVE = 2
        const val MENU_ENEMIES = 3
        const val MENU_BRUSH = 4
        const val MENU_TEST = 5
        const val MENU_DELETE = 6
        const val MENU_EXIT = 7

        val MENU_LABELS = listOf(
            "NEW",
            "OPEN",
            "SAVE",
            "ENEMIES",
            "BRUSH",
            "TEST",
            "DELETE",
            "EXIT",
            "BACK",
        )
    }
}
