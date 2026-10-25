package pt.tiago.steeleagle.input

import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import pt.tiago.steeleagle.core.model.PlayerInput

/**
 * Basic virtual D-pad and fire button. Each control tracks its own pointer id
 * so both can be used at the same time. The layout is computed in real screen
 * pixels by [TouchLayout]; the state machine lives in [TouchState] so it can be
 * tested on the JVM.
 */
/** [cross] draws the NES cross pad instead of the round D-pad. */
class TouchDpadInput(private val cross: Boolean = false) : VirtualTouchInput {

    private val buttons = TouchButtons()

    private val state = TouchState()

    private val fill = Paint().apply {
        isAntiAlias = false
        style = Paint.Style.FILL
    }
    private val stroke = Paint().apply {
        isAntiAlias = false
        style = Paint.Style.STROKE
    }

    override fun setLayout(layout: TouchLayout) {
        synchronized(state) { state.setLayout(layout) }
    }

    override fun sample(): PlayerInput = synchronized(state) { state.sample() }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        synchronized(state) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    val index = event.actionIndex
                    state.onDown(
                        pointerId = event.getPointerId(index),
                        x = event.getX(index).toInt(),
                        y = event.getY(index).toInt(),
                    )
                }

                MotionEvent.ACTION_MOVE -> {
                    for (index in 0 until event.pointerCount) {
                        state.onMove(
                            pointerId = event.getPointerId(index),
                            x = event.getX(index).toInt(),
                            y = event.getY(index).toInt(),
                        )
                    }
                }

                MotionEvent.ACTION_POINTER_UP ->
                    state.onUp(event.getPointerId(event.actionIndex))

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> state.cancel()
            }
        }
        return true
    }

    override fun reset() {
        synchronized(state) { state.reset() }
    }

    override fun draw(canvas: Canvas) {
        val layout = state.layout ?: return
        val alpha = layout.alpha
        val held = sample()
        if (cross) {
            buttons.drawCross(canvas, layout.dpad, alpha, held.direction)
        } else {
            drawDpad(canvas, layout.dpad, alpha)
        }
        buttons.drawFire(canvas, layout, alpha, held.fire)
    }

    private fun drawDpad(canvas: Canvas, circle: ControlCircle, alpha: Int) {
        val radius = circle.radius
        val arm = (radius * ARM_FRACTION).toInt().coerceAtLeast(3)
        val halfArm = arm / 2
        val cx = circle.centerX
        val cy = circle.centerY

        fill.color = DPAD_FILL
        fill.alpha = alpha / 2
        canvas.drawCircle(cx.toFloat(), cy.toFloat(), radius.toFloat(), fill)

        fill.color = DPAD_ARM
        fill.alpha = alpha
        canvas.drawRect(
            (cx - radius).toFloat(),
            (cy - halfArm).toFloat(),
            (cx + radius).toFloat(),
            (cy + halfArm).toFloat(),
            fill,
        )
        canvas.drawRect(
            (cx - halfArm).toFloat(),
            (cy - radius).toFloat(),
            (cx + halfArm).toFloat(),
            (cy + radius).toFloat(),
            fill,
        )

        fill.color = DPAD_CENTER
        fill.alpha = alpha
        canvas.drawRect(
            (cx - arm).toFloat(),
            (cy - arm).toFloat(),
            (cx + arm).toFloat(),
            (cy + arm).toFloat(),
            fill,
        )

        stroke.color = DPAD_BORDER
        stroke.alpha = alpha
        stroke.strokeWidth = (radius * 0.05f).coerceAtLeast(2f)
        canvas.drawCircle(cx.toFloat(), cy.toFloat(), radius.toFloat(), stroke)
    }

    private companion object {
        const val ARM_FRACTION = 0.36f

        const val DPAD_FILL = 0xFF202020.toInt()
        const val DPAD_ARM = 0xFF4A4A4A.toInt()
        const val DPAD_CENTER = 0xFF8C8C8C.toInt()
        const val DPAD_BORDER = 0xFFC8C8C8.toInt()
    }
}
