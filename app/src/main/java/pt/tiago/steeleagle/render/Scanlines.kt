package pt.tiago.steeleagle.render

/**
 * CRT scanline geometry for the scaled game image: one 1 px dark line at the
 * bottom of each logical row. The array is built once per viewport size and
 * reused every frame so the effect costs no allocations.
 */
object Scanlines {

    fun points(
        left: Int,
        top: Int,
        width: Int,
        height: Int,
        rows: Int = Viewport.LOGICAL_HEIGHT,
    ): FloatArray {
        if (width <= 0 || height <= 0 || rows <= 0) return FloatArray(0)
        val scale = height / rows
        // At one screen pixel per row the lines would cover the whole image.
        if (scale < 2) return FloatArray(0)
        val points = FloatArray(rows * 4)
        var index = 0
        for (row in 0 until rows) {
            val y = (top + row * scale + scale - 1).toFloat()
            points[index++] = left.toFloat()
            points[index++] = y
            points[index++] = (left + width - 1).toFloat()
            points[index++] = y
        }
        return points
    }
}
