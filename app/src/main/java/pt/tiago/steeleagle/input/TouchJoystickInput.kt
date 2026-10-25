package pt.tiago.steeleagle.input

import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import pt.tiago.steeleagle.core.model.PlayerInput

/**
 * Floating virtual joystick with a fixed fire button. The base appears under
 * the first finger in the joystick zone; a second finger can hold fire.
 */
class TouchJoystickInput : VirtualTouchInput {

    private val buttons = TouchButtons()

    private val state = JoystickState()

    private val fill = Paint().apply {
        isAntiAlias = false
        style = Paint.Style.FILL
    }
    private val stroke = Paint().apply {
        isAntiAlias = false
        style = Paint.Style.STROKE
    }

    override fun setLayout(layout: TouchLayout) {
        state.setLayout(layout)
    }

    override fun sample(): PlayerInput = state.sample()

    override fun onTouchEvent(event: MotionEvent): Boolean {
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
        return true
    }

    override fun reset() {
        state.reset()
    }

    override fun draw(canvas: Canvas) {
        val layout = state.currentLayout() ?: return
        val visual = state.visual() ?: return
        val alpha = layout.alpha

        buttons.drawFire(canvas, layout, alpha, state.sample().fire)
        drawJoystick(canvas, layout.dpad.radius, visual, alpha)
    }

    private fun drawJoystick(
        canvas: Canvas,
        radius: Int,
        visual: JoystickVisual,
        alpha: Int,
    ) {
        val baseAlpha = if (visual.active) alpha else alpha / 3
        val strokeWidth = (radius * 0.05f).coerceAtLeast(2f)

        fill.color = JOYSTICK_FILL
        fill.alpha = baseAlpha / 2
        canvas.drawCircle(
            visual.baseX.toFloat(),
            visual.baseY.toFloat(),
            radius.toFloat(),
            fill,
        )

        stroke.color = JOYSTICK_BORDER
        stroke.alpha = baseAlpha
        stroke.strokeWidth = strokeWidth
        canvas.drawCircle(
            visual.baseX.toFloat(),
            visual.baseY.toFloat(),
            radius.toFloat(),
            stroke,
        )

        if (!visual.active) {
            fill.color = JOYSTICK_KNOB
            fill.alpha = baseAlpha
            canvas.drawCircle(
                visual.baseX.toFloat(),
                visual.baseY.toFloat(),
                radius * 0.12f,
                fill,
            )
            return
        }

        stroke.color = JOYSTICK_KNOB
        stroke.alpha = alpha
        stroke.strokeWidth = strokeWidth * 0.7f
        canvas.drawLine(
            visual.baseX.toFloat(),
            visual.baseY.toFloat(),
            visual.knobX.toFloat(),
            visual.knobY.toFloat(),
            stroke,
        )

        fill.color = JOYSTICK_KNOB
        fill.alpha = alpha
        canvas.drawCircle(
            visual.knobX.toFloat(),
            visual.knobY.toFloat(),
            radius * 0.36f,
            fill,
        )
    }

    private companion object {
        const val JOYSTICK_FILL = 0xFF202020.toInt()
        const val JOYSTICK_BORDER = 0xFFC8C8C8.toInt()
        const val JOYSTICK_KNOB = 0xFF8C8C8C.toInt()
    }
}
