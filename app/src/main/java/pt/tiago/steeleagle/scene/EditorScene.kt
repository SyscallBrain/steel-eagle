package pt.tiago.steeleagle.scene

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import pt.tiago.steeleagle.core.editor.Brush
import pt.tiago.steeleagle.core.editor.EditorCommand
import pt.tiago.steeleagle.core.editor.EditorConfirm
import pt.tiago.steeleagle.core.editor.EditorEffect
import pt.tiago.steeleagle.core.editor.EditorMessages
import pt.tiago.steeleagle.core.editor.EditorMode
import pt.tiago.steeleagle.core.editor.EditorMap
import pt.tiago.steeleagle.core.editor.EditorSession
import pt.tiago.steeleagle.core.editor.EnemySetup
import pt.tiago.steeleagle.core.level.Level
import pt.tiago.steeleagle.core.level.UserLevel
import pt.tiago.steeleagle.core.model.Dir
import pt.tiago.steeleagle.core.model.PlayerInput
import pt.tiago.steeleagle.core.rules.EnemyType
import pt.tiago.steeleagle.core.world.Terrain
import pt.tiago.steeleagle.core.world.TerrainGrid
import pt.tiago.steeleagle.render.PixelFont
import pt.tiago.steeleagle.render.SpriteAtlas
import pt.tiago.steeleagle.render.TerrainRenderer
import kotlin.math.roundToInt

/**
 * Construction mode: the 26x26 field with a tank cursor,
 * a 16 px brush palette, the editor menu, enemy setup, in-game name entry and
 * a playtest hand-off. The state machine lives in [EditorSession]; this class
 * maps input, touch and drawing.
 */
class EditorScene(
    private val atlas: SpriteAtlas,
    private val font: PixelFont,
    private val host: Host,
) {

    /** Storage and flow actions the editor needs from the app. */
    interface Host {
        fun userLevels(): List<UserLevel>
        fun saveLevel(fileName: String?, level: Level): SaveResult
        fun deleteLevel(fileName: String): Boolean
        fun testLevel(level: Level)
        fun exitEditor()
    }

    sealed interface SaveResult {
        data class Saved(val fileName: String, val name: String) : SaveResult
        data class Failed(val message: String) : SaveResult
    }

    val session = EditorSession()

    private val terrainRenderer = TerrainRenderer(atlas)
    private val grid = TerrainGrid.empty()
    private var gridMap: EditorMap? = null
    private var gridVersion = -1

    private val paint = Paint().apply {
        isAntiAlias = false
        isFilterBitmap = false
    }
    private val textPaint = Paint().apply {
        isAntiAlias = false
        isFilterBitmap = false
    }
    private val textPaints = listOf(WHITE, GREY, YELLOW, GREEN, DARK)
        .associateWith { color ->
            Paint().apply {
                isAntiAlias = false
                isFilterBitmap = false
                colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY)
            }
        }
    private val fillPaint = Paint().apply { isAntiAlias = false }
    private val strokePaint = Paint().apply {
        isAntiAlias = false
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val panelPaint = Paint().apply { isAntiAlias = false }
    private val cursorPaint = Paint().apply { isAntiAlias = false }

    private var heldDir: Dir? = null
    private var repeatTicks = 0
    private var lastFire = true
    private var tick = 0L
    private var painting = false
    private var lastPaintX = -1
    private var lastPaintY = -1

    /** Called when the flow enters the editor; swallows a held fire button. */
    fun reset() {
        heldDir = null
        repeatTicks = 0
        lastFire = true
        painting = false
        lastPaintX = -1
        lastPaintY = -1
    }

    /** Back from a playtest: swallow held input and show the field again. */
    fun returnFromTest() = showField()

    /** Shows the drawing field, clearing any menu left open in an earlier visit. */
    fun showField() {
        reset()
        session.toField()
    }

    fun update(input: PlayerInput) {
        tick++
        val direction = input.direction
        if (direction != heldDir) {
            heldDir = direction
            repeatTicks = REPEAT_DELAY
            if (direction != null) dispatch(commandFor(direction))
        } else if (direction != null) {
            repeatTicks--
            if (repeatTicks <= 0) {
                dispatch(commandFor(direction))
                repeatTicks = REPEAT_RATE
            }
        }

        val fire = input.fire && !lastFire
        lastFire = input.fire
        if (fire) dispatch(EditorCommand.FIRE)
    }

    /** Back button / gamepad select: menu from the field, previous screen inside menus. */
    fun back() {
        dispatch(EditorCommand.BACK)
    }

    fun tap(x: Int, y: Int): Boolean {
        when (session.mode) {
            EditorMode.FIELD -> return tapField(x, y)
            EditorMode.MENU -> return tapMenu(x, y)
            EditorMode.BRUSH -> return tapBrush(x, y)
            EditorMode.OPEN -> return tapOpen(x, y)
            EditorMode.ENEMIES -> return tapEnemies(x, y)
            EditorMode.NAME -> return tapName(x, y)
            EditorMode.CONFIRM -> return tapConfirm(x, y)
            EditorMode.MESSAGES -> {
                if (BACK_RECT.contains(x, y)) {
                    session.dismissMessages()
                    return true
                }
                return false
            }
        }
    }

    /** Continues a field stroke while the finger moves, filling skipped cells. */
    fun drag(x: Int, y: Int) {
        if (!painting || session.mode != EditorMode.FIELD) return
        if (x !in 0 until FIELD_PIXELS || y !in 0 until FIELD_PIXELS) return
        val cellX = x / CELL_PIXELS
        val cellY = y / CELL_PIXELS
        if (lastPaintX < 0) {
            paintCell(cellX, cellY)
            return
        }
        // paintCell moves lastPaintX/Y, so interpolate from a fixed start.
        val startX = lastPaintX
        val startY = lastPaintY
        val steps = maxOf(kotlin.math.abs(cellX - startX), kotlin.math.abs(cellY - startY))
        for (step in 1..steps) {
            val t = step.toFloat() / steps
            paintCell(
                (startX + (cellX - startX) * t).roundToInt(),
                (startY + (cellY - startY) * t).roundToInt(),
            )
        }
    }

    fun endDrag() {
        painting = false
        lastPaintX = -1
        lastPaintY = -1
    }

    private fun paintCell(x: Int, y: Int) {
        lastPaintX = x
        lastPaintY = y
        session.paintAt(x, y)
    }

    // ---------------------------------------------------------------- drawing

    fun draw(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)
        syncGrid()
        terrainRenderer.update(grid, tick)
        terrainRenderer.drawBase(canvas)
        terrainRenderer.drawTrees(canvas)
        drawSpawnMarkers(canvas)
        drawEagle(canvas)
        drawCursor(canvas)
        drawPanel(canvas)

        when (session.mode) {
            EditorMode.FIELD -> Unit
            EditorMode.MENU -> drawMenu(canvas)
            EditorMode.BRUSH -> drawBrushPalette(canvas)
            EditorMode.OPEN -> drawOpen(canvas)
            EditorMode.ENEMIES -> drawEnemies(canvas)
            EditorMode.NAME -> drawName(canvas)
            EditorMode.CONFIRM -> drawConfirm(canvas)
            EditorMode.MESSAGES -> drawMessages(canvas)
        }
    }

    private fun syncGrid() {
        val map = session.map
        if (gridMap !== map || gridVersion != map.version) {
            gridMap = map
            gridVersion = map.version
            for (y in 0 until TERRAIN_SIZE) {
                for (x in 0 until TERRAIN_SIZE) {
                    grid.set8(x, y, terrainOf(map.get(x, y)))
                }
            }
        }
    }

    private fun terrainOf(char: Char): Terrain = when (char) {
        'B' -> Terrain.BRICK
        'S' -> Terrain.STEEL
        'W' -> Terrain.WATER
        'T' -> Terrain.TREES
        'I' -> Terrain.ICE
        else -> Terrain.EMPTY
    }

    private fun drawSpawnMarkers(canvas: Canvas) {
        strokePaint.strokeWidth = 1f
        strokePaint.color = MARKER_ENEMY
        for ((x, y) in ENEMY_SPAWNS) drawMarker(canvas, x, y)
        strokePaint.color = MARKER_PLAYER
        for ((x, y) in PLAYER_SPAWNS) drawMarker(canvas, x, y)
    }

    /** Inset by half a pixel so the 1 px outline stays inside the field, clear of the panel. */
    private fun drawMarker(canvas: Canvas, x: Int, y: Int) {
        canvas.drawRect(
            x * CELL_PIXELS + 0.5f,
            y * CELL_PIXELS + 0.5f,
            (x + 2) * CELL_PIXELS - 0.5f,
            (y + 2) * CELL_PIXELS - 0.5f,
            strokePaint,
        )
    }

    private fun drawEagle(canvas: Canvas) {
        canvas.drawBitmap(
            atlas.bitmap("eagle_alive"),
            (EAGLE_X * CELL_PIXELS).toFloat(),
            (EAGLE_Y * CELL_PIXELS).toFloat(),
            null,
        )
    }

    private fun drawCursor(canvas: Canvas) {
        val brush = session.brush
        val left = session.cursorX * CELL_PIXELS
        val top = session.cursorY * CELL_PIXELS
        val right = ((session.cursorX + brush.width) * CELL_PIXELS).coerceAtMost(FIELD_PIXELS)
        val bottom = ((session.cursorY + brush.height) * CELL_PIXELS).coerceAtMost(FIELD_PIXELS)

        val tankX = (session.cursorX * CELL_PIXELS).coerceAtMost(FIELD_PIXELS - TANK_PIXELS)
        val tankY = (session.cursorY * CELL_PIXELS).coerceAtMost(FIELD_PIXELS - TANK_PIXELS)
        cursorPaint.alpha = 210
        canvas.drawBitmap(
            atlas.bitmap("tank_up_0"),
            tankX.toFloat(),
            tankY.toFloat(),
            cursorPaint,
        )

        strokePaint.strokeWidth = 2f
        strokePaint.color = CURSOR
        canvas.drawRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(), strokePaint)
        strokePaint.strokeWidth = 1f
    }

    private fun drawPanel(canvas: Canvas) {
        fillPaint.color = PANEL
        canvas.drawRect(PANEL_LEFT.toFloat(), 0f, LOGICAL_WIDTH.toFloat(), LOGICAL_HEIGHT.toFloat(), fillPaint)
        strokePaint.color = BORDER
        canvas.drawLine(PANEL_LEFT.toFloat(), 0f, PANEL_LEFT.toFloat(), LOGICAL_HEIGHT.toFloat(), strokePaint)

        for ((index, brush) in Brush.entries.withIndex()) {
            val left = paletteLeft(index)
            val top = paletteTop(index)
            if (brush == session.brush) {
                strokePaint.color = YELLOW
                canvas.drawRect(
                    (left - 2).toFloat(),
                    (top - 2).toFloat(),
                    (left + ICON + 2).toFloat(),
                    (top + ICON + 2).toFloat(),
                    strokePaint,
                )
            }
            fillPaint.color = SLOT
            canvas.drawRect(left.toFloat(), top.toFloat(), (left + ICON).toFloat(), (top + ICON).toFloat(), fillPaint)
            drawBrushPreview(canvas, brush, left + 2, top + 2, ICON - 4)
        }

        fillPaint.color = SLOT
        canvas.drawRect(MENU_RECT, fillPaint)
        strokePaint.color = BORDER
        canvas.drawRect(MENU_RECT, strokePaint)
        drawCenteredIn(canvas, "MENU", MENU_RECT, WHITE)
    }

    private fun drawBrushPreview(canvas: Canvas, brush: Brush, left: Int, top: Int, size: Int) {
        if (brush == Brush.ERASER) {
            strokePaint.color = GREY
            canvas.drawLine(
                left.toFloat(),
                top.toFloat(),
                (left + size).toFloat(),
                (top + size).toFloat(),
                strokePaint,
            )
            canvas.drawLine(
                (left + size).toFloat(),
                top.toFloat(),
                left.toFloat(),
                (top + size).toFloat(),
                strokePaint,
            )
            return
        }

        val cellsWide = brush.width
        val cellsHigh = brush.height
        val cell = size / maxOf(cellsWide, cellsHigh)
        val width = cell * cellsWide
        val height = cell * cellsHigh
        val originX = left + (size - width) / 2
        val originY = top + (size - height) / 2
        val bitmap = atlas.bitmap(terrainSprite(brush))
        val source = Rect(0, 0, bitmap.width, bitmap.height)
        for (dy in 0 until cellsHigh) {
            for (dx in 0 until cellsWide) {
                val dst = Rect(
                    originX + dx * cell,
                    originY + dy * cell,
                    originX + (dx + 1) * cell,
                    originY + (dy + 1) * cell,
                )
                canvas.drawBitmap(bitmap, source, dst, paint)
            }
        }
    }

    private fun terrainSprite(brush: Brush): String = when (brush.terrain) {
        'B' -> "terrain_brick"
        'S' -> "terrain_steel"
        'W' -> "terrain_water_0"
        'T' -> "terrain_trees"
        else -> "terrain_ice"
    }

    private fun drawMenu(canvas: Canvas) {
        dim(canvas)
        fillPaint.color = PANEL_DARK
        canvas.drawRect(MENU_PANEL, fillPaint)
        strokePaint.color = BORDER
        canvas.drawRect(MENU_PANEL, strokePaint)
        drawCentered(canvas, "CONSTRUCTION", 29, YELLOW)

        for ((index, label) in session.menuLabels.withIndex()) {
            val rect = menuRowRect(index)
            if (index == session.menuIndex) {
                fillPaint.color = SLOT_SELECTED
                canvas.drawRect(rect, fillPaint)
                strokePaint.color = YELLOW
                canvas.drawRect(rect, strokePaint)
            }
            drawText(canvas, label, 68, rect.top + 4, if (index == session.menuIndex) YELLOW else WHITE)
        }
        drawCentered(canvas, "FIRE SELECTS", 192, GREY)
    }

    private fun drawBrushPalette(canvas: Canvas) {
        dim(canvas)
        fillPaint.color = PANEL_DARK
        canvas.drawRect(BRUSH_PANEL, fillPaint)
        strokePaint.color = BORDER
        canvas.drawRect(BRUSH_PANEL, strokePaint)
        drawCentered(canvas, "BRUSH", BRUSH_PANEL.top + 8, YELLOW)

        for ((index, brush) in Brush.entries.withIndex()) {
            val left = brushGridLeft(index)
            val top = brushGridTop(index)
            fillPaint.color = SLOT
            canvas.drawRect(
                left.toFloat(),
                top.toFloat(),
                (left + BRUSH_CELL).toFloat(),
                (top + BRUSH_CELL).toFloat(),
                fillPaint,
            )
            if (brush == session.brush) {
                strokePaint.color = YELLOW
                strokePaint.strokeWidth = 2f
                canvas.drawRect(
                    left.toFloat(),
                    top.toFloat(),
                    (left + BRUSH_CELL).toFloat(),
                    (top + BRUSH_CELL).toFloat(),
                    strokePaint,
                )
                strokePaint.strokeWidth = 1f
            }
            drawBrushPreview(canvas, brush, left + 3, top + 3, BRUSH_CELL - 6)
        }

        drawCentered(canvas, session.brush.label, 172, WHITE)
        drawCentered(canvas, "FIRE SELECTS", 188, GREY)
    }

    private fun drawOpen(canvas: Canvas) {
        dim(canvas)
        fillPaint.color = PANEL_DARK
        canvas.drawRect(OPEN_PANEL, fillPaint)
        strokePaint.color = BORDER
        canvas.drawRect(OPEN_PANEL, strokePaint)
        drawCentered(canvas, "OPEN LEVEL", OPEN_PANEL.top + 8, YELLOW)

        if (session.openEntries.isEmpty()) {
            drawCentered(canvas, "NO LEVELS YET", 90, GREY)
            drawCentered(canvas, "SAVE ONE IN THE EDITOR", 106, GREY)
        } else {
            val start = session.openPageStart
            for (row in 0 until OPEN_PAGE_SIZE) {
                val index = start + row
                val entry = session.openEntries.getOrNull(index) ?: break
                val rect = openRowRect(row)
                if (index == session.openIndex) {
                    fillPaint.color = SLOT_SELECTED
                    canvas.drawRect(rect, fillPaint)
                    strokePaint.color = YELLOW
                    canvas.drawRect(rect, strokePaint)
                }
                val label = "${index + 1} ${entry.name}".take(26)
                drawText(canvas, label, rect.left + 6, rect.top + 4, if (index == session.openIndex) YELLOW else WHITE)
            }
            drawCentered(
                canvas,
                "PAGE ${session.openPage + 1}/${session.openPageCount}",
                162,
                GREY,
            )
        }

        if (session.openPage > 0) drawButton(canvas, OPEN_PREV_RECT, "PREV")
        drawButton(canvas, OPEN_BACK_RECT, "BACK")
        if (session.openPage < session.openPageCount - 1) {
            drawButton(canvas, OPEN_NEXT_RECT, "NEXT")
        }
    }

    private fun drawEnemies(canvas: Canvas) {
        dim(canvas)
        fillPaint.color = PANEL_DARK
        canvas.drawRect(ENEMIES_PANEL, fillPaint)
        strokePaint.color = BORDER
        canvas.drawRect(ENEMIES_PANEL, strokePaint)
        drawCentered(canvas, "ENEMIES", ENEMIES_PANEL.top + 8, YELLOW)

        for ((index, type) in EnemyType.entries.withIndex()) {
            val rect = enemyRowRect(index)
            if (index == session.enemyIndex) {
                fillPaint.color = SLOT_SELECTED
                canvas.drawRect(rect, fillPaint)
                strokePaint.color = YELLOW
                canvas.drawRect(rect, strokePaint)
            }
            drawText(canvas, typeLabel(type), 44, rect.top + 4, if (index == session.enemyIndex) YELLOW else WHITE)
            drawMiniButton(canvas, enemyMinusRect(index), "-")
            drawRight(
                canvas,
                session.enemies.count(type).toString().padStart(2, '0'),
                192,
                rect.top + 4,
                WHITE,
            )
            drawMiniButton(canvas, enemyPlusRect(index), "+")
        }

        val total = session.enemies.total
        drawCentered(
            canvas,
            "TOTAL $total/${EnemySetup.TOTAL}",
            152,
            if (total == EnemySetup.TOTAL) GREEN else YELLOW,
        )
        drawCentered(canvas, "LEFT RIGHT CHANGES", 166, GREY)
        drawButton(canvas, ENEMIES_BACK_RECT, "BACK")
    }

    private fun drawName(canvas: Canvas) {
        dim(canvas)
        fillPaint.color = PANEL_DARK
        canvas.drawRect(NAME_PANEL, fillPaint)
        strokePaint.color = BORDER
        canvas.drawRect(NAME_PANEL, strokePaint)
        drawCentered(canvas, "LEVEL NAME", NAME_PANEL.top + 6, YELLOW)
        drawCentered(canvas, "NAME: ${session.nameEntry.text}", 26, WHITE)

        for ((index, label) in session.nameEntry.cells.withIndex()) {
            val rect = nameCellRect(index)
            fillPaint.color = SLOT
            canvas.drawRect(rect, fillPaint)
            strokePaint.color = if (index == session.nameEntry.cursor) YELLOW else BORDER
            canvas.drawRect(rect, strokePaint)
            val text = when (label) {
                "SPACE" -> "SPC"
                else -> label
            }
            drawCenteredIn(canvas, text, rect, if (index == session.nameEntry.cursor) YELLOW else WHITE)
        }

        drawCentered(canvas, "FIRE TYPES", 142, GREY)
        drawCentered(canvas, "BACK CANCELS", 156, GREY)
    }

    private fun drawConfirm(canvas: Canvas) {
        dim(canvas)
        fillPaint.color = PANEL_DARK
        canvas.drawRect(CONFIRM_PANEL, fillPaint)
        strokePaint.color = BORDER
        canvas.drawRect(CONFIRM_PANEL, strokePaint)

        val title = when (session.confirm) {
            EditorConfirm.NEW -> "DISCARD CHANGES"
            EditorConfirm.OPEN -> "DISCARD AND OPEN"
            EditorConfirm.EXIT -> "DISCARD AND EXIT"
            EditorConfirm.DELETE -> "DELETE LEVEL"
            null -> "ARE YOU SURE"
        }
        drawCentered(canvas, title, 76, YELLOW)
        drawCentered(canvas, "THIS CANNOT BE UNDONE", 94, GREY)

        for (index in 0..1) {
            val rect = confirmRect(index)
            val selected = index == session.confirmIndex
            fillPaint.color = if (selected) SLOT_SELECTED else SLOT
            canvas.drawRect(rect, fillPaint)
            strokePaint.color = if (selected) YELLOW else BORDER
            canvas.drawRect(rect, strokePaint)
            drawCenteredIn(canvas, if (index == CONFIRM_YES) "YES" else "NO", rect, if (selected) YELLOW else WHITE)
        }
        drawCentered(canvas, "BACK CANCELS", 150, GREY)
    }

    private fun drawMessages(canvas: Canvas) {
        dim(canvas)
        fillPaint.color = PANEL_DARK
        canvas.drawRect(MESSAGES_PANEL, fillPaint)
        strokePaint.color = BORDER
        canvas.drawRect(MESSAGES_PANEL, strokePaint)
        drawCentered(canvas, session.infoTitle, MESSAGES_PANEL.top + 12, YELLOW)

        val wrapped = session.messages.flatMap { EditorMessages.wrap(it, MESSAGE_CHARS) }
        val shown = if (wrapped.size > MESSAGE_LINES) {
            wrapped.take(MESSAGE_LINES - 1) + listOf("+${wrapped.size - MESSAGE_LINES + 1} MORE")
        } else {
            wrapped
        }
        var y = MESSAGES_PANEL.top + 34
        for (line in shown) {
            drawCentered(canvas, line, y, WHITE)
            y += 12
        }
        drawButton(canvas, BACK_RECT, "BACK")
    }

    // ------------------------------------------------------------------ taps

    private fun tapField(x: Int, y: Int): Boolean {
        if (MENU_RECT.contains(x, y)) {
            dispatch(EditorCommand.BACK)
            return true
        }
        if (x >= PANEL_LEFT) {
            painting = false
            val index = brushAt(x, y) ?: return false
            session.tapBrush(index)
            return true
        }
        if (y >= FIELD_PIXELS) return false
        painting = true
        lastPaintX = x / CELL_PIXELS
        lastPaintY = y / CELL_PIXELS
        session.paintAt(lastPaintX, lastPaintY)
        return true
    }

    private fun tapMenu(x: Int, y: Int): Boolean {
        if (!MENU_PANEL.contains(x, y)) return false
        for (index in session.menuLabels.indices) {
            if (!menuRowRect(index).contains(x, y)) continue
            apply(session.activateMenu(index))
            return true
        }
        return false
    }

    private fun tapBrush(x: Int, y: Int): Boolean {
        if (!BRUSH_PANEL.contains(x, y)) return false
        for (index in Brush.entries.indices) {
            if (!brushGridRect(index).contains(x, y)) continue
            session.tapBrush(index)
            return true
        }
        return false
    }

    private fun tapOpen(x: Int, y: Int): Boolean {
        if (session.openPage > 0 && OPEN_PREV_RECT.contains(x, y)) {
            dispatch(EditorCommand.LEFT)
            return true
        }
        if (session.openPage < session.openPageCount - 1 && OPEN_NEXT_RECT.contains(x, y)) {
            dispatch(EditorCommand.RIGHT)
            return true
        }
        if (OPEN_BACK_RECT.contains(x, y)) {
            dispatch(EditorCommand.BACK)
            return true
        }
        for (row in 0 until OPEN_PAGE_SIZE) {
            if (!openRowRect(row).contains(x, y)) continue
            val index = session.openPageStart + row
            if (index < session.openEntries.size) session.openLevelAt(index)
            return true
        }
        return false
    }

    private fun tapEnemies(x: Int, y: Int): Boolean {
        if (ENEMIES_BACK_RECT.contains(x, y)) {
            dispatch(EditorCommand.BACK)
            return true
        }
        for (index in EnemyType.entries.indices) {
            val delta = when {
                enemyMinusRect(index).contains(x, y) -> -1
                enemyPlusRect(index).contains(x, y) -> 1
                enemyRowRect(index).contains(x, y) -> 0
                else -> continue
            }
            session.adjustEnemy(index, delta)
            return true
        }
        return false
    }

    private fun tapName(x: Int, y: Int): Boolean {
        for (index in session.nameEntry.cells.indices) {
            if (!nameCellRect(index).contains(x, y)) continue
            apply(session.selectNameCell(index))
            return true
        }
        return false
    }

    private fun tapConfirm(x: Int, y: Int): Boolean {
        for (index in 0..1) {
            if (!confirmRect(index).contains(x, y)) continue
            apply(session.chooseConfirm(index == CONFIRM_YES))
            return true
        }
        return false
    }

    // --------------------------------------------------------------- effects

    private fun dispatch(command: EditorCommand) {
        apply(session.handle(command))
    }

    private fun apply(effect: EditorEffect?) {
        when (effect) {
            null -> Unit
            EditorEffect.ListLevels -> session.setOpenList(host.userLevels())
            is EditorEffect.Test -> host.testLevel(effect.level)
            is EditorEffect.Save -> when (val result = host.saveLevel(effect.fileName, effect.level)) {
                is SaveResult.Saved -> session.onSaved(result.fileName, result.name)
                is SaveResult.Failed -> session.onSaveFailed(result.message)
            }

            is EditorEffect.Delete -> {
                if (host.deleteLevel(effect.fileName)) {
                    session.onDeleted()
                } else {
                    session.onDeleteFailed("FILE NOT REMOVED")
                }
            }

            EditorEffect.Exit -> host.exitEditor()
        }
    }

    // ---------------------------------------------------------------- drawing helpers

    private fun dim(canvas: Canvas) {
        panelPaint.color = OVERLAY
        canvas.drawRect(0f, 0f, LOGICAL_WIDTH.toFloat(), LOGICAL_HEIGHT.toFloat(), panelPaint)
    }

    private fun drawText(canvas: Canvas, text: String, x: Int, y: Int, color: Int) {
        val paint = textPaints[color] ?: textPaint
        font.draw(canvas, text, x, y, paint)
    }

    private fun drawCentered(canvas: Canvas, text: String, y: Int, color: Int) {
        drawText(canvas, text, (LOGICAL_WIDTH - font.width(text)) / 2, y, color)
    }

    private fun drawRight(canvas: Canvas, text: String, right: Int, y: Int, color: Int) {
        drawText(canvas, text, right - font.width(text), y, color)
    }

    private fun drawCenteredIn(canvas: Canvas, text: String, rect: Rect, color: Int) {
        val x = rect.centerX() - font.width(text) / 2
        val y = rect.centerY() - 4
        drawText(canvas, text, x, y, color)
    }

    private fun drawButton(canvas: Canvas, rect: Rect, label: String) {
        fillPaint.color = SLOT
        canvas.drawRect(rect, fillPaint)
        strokePaint.color = BORDER
        canvas.drawRect(rect, strokePaint)
        drawCenteredIn(canvas, label, rect, WHITE)
    }


    private fun brushAt(x: Int, y: Int): Int? {
        for (index in Brush.entries.indices) {
            if (brushGridRect(paletteLeft(index), paletteTop(index), ICON).contains(x, y)) {
                return index
            }
        }
        return null
    }

    private fun paletteLeft(index: Int): Int = PANEL_LEFT + 4 + (index % PALETTE_COLUMNS) * (ICON + 2)

    private fun paletteTop(index: Int): Int = 6 + (index / PALETTE_COLUMNS) * (ICON + 2)

    private fun brushGridLeft(index: Int): Int =
        BRUSH_GRID_LEFT + (index % BRUSH_COLUMNS) * (BRUSH_CELL + BRUSH_GAP)

    private fun brushGridTop(index: Int): Int =
        BRUSH_GRID_TOP + (index / BRUSH_COLUMNS) * (BRUSH_CELL + BRUSH_GAP)

    private fun brushGridRect(left: Int, top: Int, size: Int): Rect =
        Rect(left, top, left + size, top + size)

    private fun brushGridRect(index: Int): Rect =
        brushGridRect(brushGridLeft(index), brushGridTop(index), BRUSH_CELL)

    private fun menuRowRect(index: Int): Rect {
        val top = MENU_ITEMS_TOP + index * MENU_ROW
        return Rect(MENU_PANEL.left + 6, top - 3, MENU_PANEL.right - 6, top + 12)
    }

    private fun openRowRect(row: Int): Rect {
        val top = OPEN_ROWS_TOP + row * OPEN_ROW
        return Rect(OPEN_PANEL.left + 4, top - 3, OPEN_PANEL.right - 4, top + 12)
    }

    private fun enemyRowRect(index: Int): Rect {
        val top = ENEMY_ROWS_TOP + index * ENEMY_ROW
        return Rect(ENEMIES_PANEL.left + 4, top - 4, ENEMIES_PANEL.right - 4, top + 14)
    }

    private fun enemyMinusRect(index: Int): Rect {
        val top = ENEMY_ROWS_TOP + index * ENEMY_ROW
        return Rect(146, top - 2, 166, top + 14)
    }

    private fun enemyPlusRect(index: Int): Rect {
        val top = ENEMY_ROWS_TOP + index * ENEMY_ROW
        return Rect(196, top - 2, 216, top + 14)
    }

    private fun drawMiniButton(canvas: Canvas, rect: Rect, label: String) {
        fillPaint.color = SLOT
        canvas.drawRect(rect, fillPaint)
        strokePaint.color = BORDER
        canvas.drawRect(rect, strokePaint)
        drawCenteredIn(canvas, label, rect, WHITE)
    }

    private fun nameCellRect(index: Int): Rect {
        val column = index % NAME_COLUMNS
        val row = index / NAME_COLUMNS
        val left = NAME_GRID_LEFT + column * NAME_CELL_W
        val top = NAME_GRID_TOP + row * NAME_CELL_H
        return Rect(left, top, left + NAME_CELL_W - 1, top + NAME_CELL_H - 1)
    }

    private fun confirmRect(index: Int): Rect = if (index == CONFIRM_YES) {
        Rect(52, 112, 116, 132)
    } else {
        Rect(140, 112, 204, 132)
    }

    private fun commandFor(direction: Dir): EditorCommand = when (direction) {
        Dir.UP -> EditorCommand.UP
        Dir.DOWN -> EditorCommand.DOWN
        Dir.LEFT -> EditorCommand.LEFT
        Dir.RIGHT -> EditorCommand.RIGHT
    }

    private fun typeLabel(type: EnemyType): String = when (type) {
        EnemyType.BASIC -> "BASIC"
        EnemyType.FAST -> "FAST"
        EnemyType.POWER -> "POWER"
        EnemyType.ARMORED -> "ARMOR"
    }

    private companion object {
        const val LOGICAL_WIDTH = 256
        const val LOGICAL_HEIGHT = 224
        const val FIELD_PIXELS = 208
        const val CELL_PIXELS = 8
        const val TERRAIN_SIZE = 26
        const val TANK_PIXELS = 16
        const val PANEL_LEFT = 208
        const val ICON = 20
        const val PALETTE_COLUMNS = 2
        const val BRUSH_COLUMNS = 4
        const val BRUSH_CELL = 30
        const val BRUSH_GAP = 6
        const val BRUSH_GRID_LEFT = 53
        const val BRUSH_GRID_TOP = 62
        const val OPEN_PAGE_SIZE = 8
        const val OPEN_ROWS_TOP = 38
        const val OPEN_ROW = 16
        const val ENEMY_ROWS_TOP = 54
        const val ENEMY_ROW = 24
        const val NAME_COLUMNS = 10
        const val NAME_CELL_W = 24
        const val NAME_CELL_H = 20
        const val NAME_GRID_LEFT = 8
        const val NAME_GRID_TOP = 50
        const val CONFIRM_YES = 0
        const val MESSAGE_CHARS = 28
        const val MESSAGE_LINES = 7
        const val REPEAT_DELAY = 12
        const val REPEAT_RATE = 4

        const val EAGLE_X = 12
        const val EAGLE_Y = 24
        val ENEMY_SPAWNS = listOf(0 to 0, 12 to 0, 24 to 0)
        val PLAYER_SPAWNS = listOf(8 to 24, 16 to 24)

        val MENU_PANEL = Rect(52, 20, 204, 206)
        val MENU_ITEMS_TOP = 44
        val MENU_ROW = 16

        val BRUSH_PANEL = Rect(40, 40, 216, 196)

        val OPEN_PANEL = Rect(16, 16, 240, 208)
        val OPEN_PREV_RECT = Rect(20, 176, 64, 194)
        val OPEN_BACK_RECT = Rect(100, 176, 156, 194)
        val OPEN_NEXT_RECT = Rect(192, 176, 236, 194)

        val ENEMIES_PANEL = Rect(32, 24, 224, 206)
        val ENEMIES_BACK_RECT = Rect(96, 180, 160, 198)

        val NAME_PANEL = Rect(4, 4, 252, 168)

        val CONFIRM_PANEL = Rect(36, 60, 220, 162)

        val MESSAGES_PANEL = Rect(16, 40, 240, 190)
        val BACK_RECT = Rect(96, 166, 160, 184)

        val MENU_RECT = Rect(208, 142, 256, 162)

        const val WHITE = 0xFFFFFFFF.toInt()
        const val GREY = 0xFFA0A0A0.toInt()
        const val DARK = 0xFF606060.toInt()
        const val YELLOW = 0xFFFCD800.toInt()
        const val GREEN = 0xFF68D868.toInt()
        const val PANEL = 0xFF7C7C7C.toInt()
        const val PANEL_DARK = 0xFF101010.toInt()
        const val SLOT = 0xFF303030.toInt()
        const val SLOT_SELECTED = 0xFF5A4A10.toInt()
        const val BORDER = 0xFF8C8C8C.toInt()
        const val CURSOR = 0xFFFCD800.toInt()
        const val MARKER_ENEMY = 0xFFCC7C00.toInt()
        const val MARKER_PLAYER = 0xFF2C8C2C.toInt()
        const val OVERLAY = 0xCC000000.toInt()
    }
}
