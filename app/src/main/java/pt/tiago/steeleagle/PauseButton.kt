package pt.tiago.steeleagle

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import pt.tiago.steeleagle.render.Viewport

/**
 * Small pause button drawn in real screen pixels. It prefers the right
 * letterbox margin and falls back to a translucent overlay on the game image.
 */
class PauseButton(private val density: Float) {

    @Volatile
    private var bounds: Rect? = null

    @Volatile
    private var overlay = false

    private val fill = Paint().apply { isAntiAlias = false }
    private val stroke = Paint().apply {
        isAntiAlias = false
        style = Paint.Style.STROKE
    }
    private val bar = Paint().apply { isAntiAlias = false }

    fun layout(screenWidth: Int, screenHeight: Int, viewport: Viewport) {
        val size = (44 * density).toInt().coerceAtLeast(44)
        val margin = (8 * density).toInt().coerceAtLeast(4)
        val rightMargin = screenWidth - (viewport.left + viewport.width)
        val leftMargin = viewport.left
        val top = viewport.top + margin

        val rightFits = rightMargin >= size + margin * 2
        val leftFits = leftMargin >= size + margin * 2
        overlay = !rightFits && !leftFits

        bounds = when {
            rightFits -> Rect(
                viewport.left + viewport.width + (rightMargin - size) / 2,
                top,
                viewport.left + viewport.width + (rightMargin + size) / 2,
                top + size,
            )

            leftFits -> Rect(
                (leftMargin - size) / 2,
                top,
                (leftMargin + size) / 2,
                top + size,
            )

            else -> Rect(
                viewport.left + viewport.width - size - margin,
                top,
                viewport.left + viewport.width - margin,
                top + size,
            )
        }
    }

    fun draw(canvas: Canvas) {
        val rect = bounds ?: return
        val alpha = if (overlay) 150 else 235

        fill.color = 0xFF101010.toInt()
        fill.alpha = alpha
        canvas.drawRect(rect, fill)

        stroke.color = 0xFFC8C8C8.toInt()
        stroke.alpha = alpha
        stroke.strokeWidth = (2 * density).coerceAtLeast(2f)
        canvas.drawRect(rect, stroke)

        val barWidth = (rect.width() * 0.12f).toInt().coerceAtLeast(2)
        val barHeight = (rect.height() * 0.42f).toInt().coerceAtLeast(6)
        val gap = (rect.width() * 0.12f).toInt().coerceAtLeast(2)
        val centerX = rect.centerX()
        val centerY = rect.centerY()
        bar.color = 0xFFFFFFFF.toInt()
        bar.alpha = alpha
        canvas.drawRect(
            (centerX - gap - barWidth).toFloat(),
            (centerY - barHeight / 2).toFloat(),
            (centerX - gap).toFloat(),
            (centerY + barHeight / 2).toFloat(),
            bar,
        )
        canvas.drawRect(
            (centerX + gap).toFloat(),
            (centerY - barHeight / 2).toFloat(),
            (centerX + gap + barWidth).toFloat(),
            (centerY + barHeight / 2).toFloat(),
            bar,
        )
    }

    fun contains(x: Int, y: Int): Boolean = bounds?.contains(x, y) == true
}
