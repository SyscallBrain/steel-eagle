package pt.tiago.steeleagle.render

/**
 * Palette keys used by the ASCII sprite files. The colours are inspired by the
 * NES palette but chosen for Steel Eagle.
 */
object Palette {
    const val TRANSPARENT = 0

    fun colorFor(key: Char): Int = when (key) {
        '.' -> TRANSPARENT
        'K' -> 0xFF000000.toInt()
        'D' -> 0xFF4A4A4A.toInt()
        'L' -> 0xFFB8B8B8.toInt()
        'Y' -> 0xFFFCD800.toInt()
        'O' -> 0xFFE09000.toInt()
        'W' -> 0xFFFFFFFF.toInt()
        'R' -> 0xFFD82800.toInt()
        'G' -> 0xFF58D854.toInt()
        'E' -> 0xFF007800.toInt()
        'B' -> 0xFF8C4A00.toInt()
        'C' -> 0xFF00B8F8.toInt()
        'S' -> 0xFF3CBCFC.toInt()
        else -> throw IllegalArgumentException("unknown palette key '$key'")
    }
}
