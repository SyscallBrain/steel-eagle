package pt.tiago.steeleagle.render

/**
 * Integer-scaled, letterboxed placement of the 256x224 logical image.
 * All values are in real screen pixels; the scale is never fractional.
 */
data class Viewport(
    val scale: Int,
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
) {
    fun contains(x: Int, y: Int): Boolean =
        x >= left && x < left + width && y >= top && y < top + height

    companion object {
        const val LOGICAL_WIDTH = 256
        const val LOGICAL_HEIGHT = 224

        fun fit(
            screenWidth: Int,
            screenHeight: Int,
            logicalWidth: Int = LOGICAL_WIDTH,
            logicalHeight: Int = LOGICAL_HEIGHT,
        ): Viewport {
            require(screenWidth > 0 && screenHeight > 0) { "screen must be non-empty" }
            require(logicalWidth > 0 && logicalHeight > 0) { "logical size must be positive" }

            val scale = maxOf(
                1,
                minOf(screenWidth / logicalWidth, screenHeight / logicalHeight),
            )
            val width = logicalWidth * scale
            val height = logicalHeight * scale
            return Viewport(
                scale = scale,
                left = (screenWidth - width) / 2,
                top = (screenHeight - height) / 2,
                width = width,
                height = height,
            )
        }
    }
}
