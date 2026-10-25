package pt.tiago.steeleagle.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint

/**
 * 8x8 bitmap font. Glyph names come from the `glyph_*` sprites in the atlas;
 * unknown characters are skipped so a typo never crashes a frame. ASCII glyphs
 * are resolved once so drawing a frame does not build sprite names.
 */
class PixelFont(private val atlas: SpriteAtlas) {

    val cellWidth: Int = 8
    val cellHeight: Int = 8

    private val glyphs: Array<Bitmap?> = Array(ASCII_LIMIT) { code ->
        atlas.bitmapOrNull("glyph_${glyphName(code.toChar())}")
    }

    fun draw(canvas: Canvas, text: String, x: Int, y: Int, paint: Paint? = null) {
        var cursor = x
        for (char in text) {
            val bitmap = glyph(char)
            if (bitmap != null) {
                canvas.drawBitmap(bitmap, cursor.toFloat(), y.toFloat(), paint)
            }
            cursor += cellWidth
        }
    }

    fun width(text: String): Int = text.length * cellWidth

    fun glyph(char: Char): Bitmap? {
        val upper = char.uppercaseChar()
        if (upper.code < ASCII_LIMIT) return glyphs[upper.code]
        return atlas.bitmapOrNull("glyph_${glyphName(upper)}")
    }

    private fun glyphName(char: Char): String = when (char) {
        ' ' -> "SPACE"
        ':' -> "COLON"
        '.' -> "DOT"
        '-' -> "DASH"
        '/' -> "SLASH"
        '!' -> "BANG"
        '?' -> "QUESTION"
        '+' -> "PLUS"
        else -> char.toString()
    }

    private companion object {
        const val ASCII_LIMIT = 128
    }
}
