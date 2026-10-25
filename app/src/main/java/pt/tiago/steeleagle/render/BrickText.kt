package pt.tiago.steeleagle.render

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.Shader

/**
 * Renders a short string as large letters filled with the brick terrain tile.
 * Used for the title and game-over logos. The atlas glyphs are white masks, so
 * a brick BitmapShader is painted on top with SRC_ATOP.
 */
class BrickText(private val atlas: SpriteAtlas, private val font: PixelFont) {

    private val cache = HashMap<String, Bitmap>()

    private val glyphPaint = Paint().apply {
        isAntiAlias = false
        isFilterBitmap = false
    }

    private val brickPaint = Paint().apply {
        isAntiAlias = false
        isFilterBitmap = false
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
    }

    fun bitmap(text: String, scale: Int): Bitmap {
        require(scale > 0) { "scale must be positive" }
        val key = "$scale:$text"
        return cache.getOrPut(key) { build(text, scale) }
    }

    private fun build(text: String, scale: Int): Bitmap {
        val width = ((text.length * GLYPH_STEP - 1) * scale).coerceAtLeast(1)
        val height = GLYPH_HEIGHT * scale
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        for ((index, char) in text.withIndex()) {
            val glyph = font.glyph(char) ?: continue
            val left = index * GLYPH_STEP * scale
            canvas.drawBitmap(
                glyph,
                null,
                Rect(left, 0, left + glyph.width * scale, glyph.height * scale),
                glyphPaint,
            )
        }

        val brick = atlas.bitmap("terrain_brick")
        brickPaint.shader = BitmapShader(brick, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), brickPaint)
        return bitmap
    }

    private companion object {
        const val GLYPH_STEP = 6
        const val GLYPH_HEIGHT = 8
    }
}
