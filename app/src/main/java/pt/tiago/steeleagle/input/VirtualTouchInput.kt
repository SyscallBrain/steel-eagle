package pt.tiago.steeleagle.input

import android.graphics.Canvas
import android.view.MotionEvent

/** Touch control that can be drawn on top of the scaled game image. */
interface VirtualTouchInput : InputSource {
    fun setLayout(layout: TouchLayout)
    fun onTouchEvent(event: MotionEvent): Boolean
    fun draw(canvas: Canvas)
    fun reset()
}
