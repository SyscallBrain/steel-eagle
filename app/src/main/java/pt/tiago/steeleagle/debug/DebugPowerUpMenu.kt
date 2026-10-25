package pt.tiago.steeleagle.debug

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.MotionEvent
import pt.tiago.steeleagle.core.rules.PowerUpType
import pt.tiago.steeleagle.render.PowerUpIcons
import pt.tiago.steeleagle.render.SpriteAtlas

/**
 * Debug-only top row of eight power-up buttons. Tapping one asks the scene to
 * spawn that power-up near the player. The button list is published atomically
 * so the render thread never observes a half-written layout.
 */
class DebugPowerUpMenu(
    private val atlas: SpriteAtlas,
    private val density: Float,
) {
    @Volatile
    private var buttons: List<Pair<PowerUpType, Rect>> = emptyList()

    private val fill = Paint().apply { isAntiAlias = false }
    private val stroke = Paint().apply {
        isAntiAlias = false
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val iconPaint = Paint().apply {
        isAntiAlias = false
        isFilterBitmap = false
    }
    private val background = Paint().apply { color = 0xCC000000.toInt() }

    fun layout(screenWidth: Int, screenHeight: Int) {
        val size = (28 * density).toInt().coerceAtLeast(24)
        val gap = (4 * density).toInt().coerceAtLeast(2)
        val total = PowerUpType.entries.size * size +
            (PowerUpType.entries.size - 1) * gap
        val startX = ((screenWidth - total) / 2).coerceAtLeast(gap)
        val top = gap

        buttons = PowerUpType.entries.mapIndexed { index, type ->
            val left = startX + index * (size + gap)
            type to Rect(left, top, left + size, top + size)
        }
    }

    fun draw(canvas: Canvas) {
        val current = buttons
        if (current.isEmpty()) return

        val first = current.first().second
        val last = current.last().second
        canvas.drawRect(
            (first.left - 4).toFloat(),
            (first.top - 2).toFloat(),
            (last.right + 4).toFloat(),
            (last.bottom + 2).toFloat(),
            background,
        )

        for ((type, rect) in current) {
            fill.color = 0xFF303030.toInt()
            canvas.drawRect(rect, fill)

            val icon = atlas.bitmap(PowerUpIcons.name(type))
            canvas.drawBitmap(
                icon,
                null,
                Rect(rect.left + 4, rect.top + 4, rect.right - 4, rect.bottom - 4),
                iconPaint,
            )

            stroke.color = 0xFFC8C8C8.toInt()
            canvas.drawRect(rect, stroke)
        }
    }

    fun onTouch(event: MotionEvent, onSelect: (PowerUpType) -> Unit): Boolean {
        val action = event.actionMasked
        if (action != MotionEvent.ACTION_DOWN &&
            action != MotionEvent.ACTION_POINTER_DOWN
        ) {
            return false
        }

        val index = event.actionIndex
        val x = event.getX(index).toInt()
        val y = event.getY(index).toInt()
        for ((type, rect) in buttons) {
            if (rect.contains(x, y)) {
                onSelect(type)
                return true
            }
        }
        return false
    }
}
