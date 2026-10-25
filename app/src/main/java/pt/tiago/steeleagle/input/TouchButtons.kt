package pt.tiago.steeleagle.input

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import pt.tiago.steeleagle.core.model.Dir

/**
 * Drawing shared by the touch pads: the round fire button, the NES-style A/B
 * pair and the NES cross pad. Pressed parts are drawn brighter so a touch
 * gives immediate feedback.
 */
class TouchButtons {

    private val fill = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    private val stroke = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
    }
    private val label = Paint().apply {
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val arrow = Path()

    fun drawFire(canvas: Canvas, layout: TouchLayout, alpha: Int, pressed: Boolean) {
        if (layout.abButtons) {
            drawAb(canvas, layout.buttonB, "B", alpha, pressed)
            drawAb(canvas, layout.buttonA, "A", alpha, pressed)
            return
        }
        val circle = layout.fire
        fill.color = if (pressed) FIRE_PRESSED else FIRE_FILL
        fill.alpha = alpha
        canvas.drawCircle(
            circle.centerX.toFloat(),
            circle.centerY.toFloat(),
            circle.radius.toFloat(),
            fill,
        )

        stroke.color = FIRE_BORDER
        stroke.alpha = alpha
        stroke.strokeWidth = (circle.radius * 0.08f).coerceAtLeast(2f)
        canvas.drawCircle(
            circle.centerX.toFloat(),
            circle.centerY.toFloat(),
            circle.radius - stroke.strokeWidth,
            stroke,
        )

        fill.color = FIRE_BORDER
        fill.alpha = alpha
        canvas.drawCircle(
            circle.centerX.toFloat(),
            circle.centerY.toFloat(),
            circle.radius * 0.26f,
            fill,
        )
    }

    private fun drawAb(
        canvas: Canvas,
        circle: ControlCircle,
        text: String,
        alpha: Int,
        pressed: Boolean,
    ) {
        val cx = circle.centerX.toFloat()
        val cy = circle.centerY.toFloat()
        val radius = circle.radius.toFloat()

        fill.color = AB_RING
        fill.alpha = alpha
        canvas.drawCircle(cx, cy, radius, fill)

        fill.color = if (pressed) AB_PRESSED else AB_FILL
        fill.alpha = alpha
        canvas.drawCircle(cx, cy, radius * 0.84f, fill)

        label.color = AB_LABEL
        label.alpha = alpha
        label.textSize = radius * 0.9f
        canvas.drawText(text, cx, cy - (label.ascent() + label.descent()) / 2f, label)
    }

    /** NES cross: four arms, the held one lit, with a dimple in the middle. */
    fun drawCross(canvas: Canvas, circle: ControlCircle, alpha: Int, held: Dir?) {
        val radius = circle.radius.toFloat()
        val half = radius * CROSS_ARM_HALF
        val cx = circle.centerX.toFloat()
        val cy = circle.centerY.toFloat()

        fill.color = CROSS_BODY
        fill.alpha = alpha
        canvas.drawRect(cx - radius, cy - half, cx + radius, cy + half, fill)
        canvas.drawRect(cx - half, cy - radius, cx + half, cy + radius, fill)

        if (held != null) {
            fill.color = CROSS_PRESSED
            fill.alpha = alpha
            when (held) {
                Dir.UP -> canvas.drawRect(cx - half, cy - radius, cx + half, cy - half, fill)
                Dir.DOWN -> canvas.drawRect(cx - half, cy + half, cx + half, cy + radius, fill)
                Dir.LEFT -> canvas.drawRect(cx - radius, cy - half, cx - half, cy + half, fill)
                Dir.RIGHT -> canvas.drawRect(cx + half, cy - half, cx + radius, cy + half, fill)
            }
        }

        stroke.color = CROSS_BORDER
        stroke.alpha = alpha
        stroke.strokeWidth = (radius * 0.04f).coerceAtLeast(2f)
        canvas.drawRect(cx - radius, cy - half, cx + radius, cy + half, stroke)
        canvas.drawRect(cx - half, cy - radius, cx + half, cy + radius, stroke)
        fill.color = CROSS_BODY
        fill.alpha = alpha
        canvas.drawRect(cx - half + stroke.strokeWidth, cy - half + stroke.strokeWidth,
            cx + half - stroke.strokeWidth, cy + half - stroke.strokeWidth, fill)

        fill.color = CROSS_ARROW
        fill.alpha = alpha
        val tip = radius * 0.9f
        val base = radius * 0.62f
        val wing = half * 0.5f
        drawArrow(canvas, cx, cy - tip, cx - wing, cy - base, cx + wing, cy - base)
        drawArrow(canvas, cx, cy + tip, cx - wing, cy + base, cx + wing, cy + base)
        drawArrow(canvas, cx - tip, cy, cx - base, cy - wing, cx - base, cy + wing)
        drawArrow(canvas, cx + tip, cy, cx + base, cy - wing, cx + base, cy + wing)

        stroke.color = CROSS_ARROW
        canvas.drawCircle(cx, cy, half * 0.5f, stroke)
    }

    private fun drawArrow(
        canvas: Canvas,
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
    ) {
        arrow.reset()
        arrow.moveTo(x0, y0)
        arrow.lineTo(x1, y1)
        arrow.lineTo(x2, y2)
        arrow.close()
        canvas.drawPath(arrow, fill)
    }

    private companion object {
        const val CROSS_ARM_HALF = 0.32f

        const val FIRE_FILL = 0xFFA02018.toInt()
        const val FIRE_PRESSED = 0xFFE04030.toInt()
        const val FIRE_BORDER = 0xFFFCD800.toInt()

        const val AB_RING = 0xFFD8D8D8.toInt()
        const val AB_FILL = 0xFFB01818.toInt()
        const val AB_PRESSED = 0xFFF04838.toInt()
        const val AB_LABEL = 0xFFFFFFFF.toInt()

        const val CROSS_BODY = 0xFF2A2A2A.toInt()
        const val CROSS_PRESSED = 0xFF6A6A6A.toInt()
        const val CROSS_BORDER = 0xFFC8C8C8.toInt()
        const val CROSS_ARROW = 0xFF8C8C8C.toInt()
    }
}
