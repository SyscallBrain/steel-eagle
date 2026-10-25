package pt.tiago.steeleagle.core.sprite

/**
 * One ASCII-art sprite. Each character is a palette key; '.' is transparent.
 * Rows all have the same [width].
 */
data class SpriteFrame(
    val name: String,
    val width: Int,
    val height: Int,
    val rows: List<String>,
)

/**
 * Parses the project's text sprite format:
 *
 * ```
 * # comment
 * sprite tank_up_0
 * .KK.
 * KYYK
 * end
 * ```
 */
object AsciiSpriteParser {

    fun parse(text: String): List<SpriteFrame> {
        val frames = mutableListOf<SpriteFrame>()
        val names = mutableSetOf<String>()
        var currentName: String? = null
        var currentRows = mutableListOf<String>()

        fun finishSprite(lineNumber: Int) {
            val name = currentName
                ?: throw IllegalArgumentException("line $lineNumber: 'end' without 'sprite'")
            require(currentRows.isNotEmpty()) { "sprite '$name' has no rows" }
            val width = currentRows.first().length
            require(currentRows.all { it.length == width }) {
                "sprite '$name' has rows with different widths"
            }
            frames += SpriteFrame(name, width, currentRows.size, currentRows.toList())
            currentName = null
            currentRows = mutableListOf()
        }

        text.lines().forEachIndexed { index, raw ->
            val lineNumber = index + 1
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed

            when {
                line.startsWith("sprite ") -> {
                    require(currentName == null) {
                        "line $lineNumber: new sprite before previous 'end'"
                    }
                    val name = line.removePrefix("sprite ").trim()
                    require(name.isNotEmpty()) { "line $lineNumber: sprite without a name" }
                    require(names.add(name)) { "line $lineNumber: duplicate sprite '$name'" }
                    currentName = name
                    currentRows = mutableListOf()
                }

                line == "end" -> finishSprite(lineNumber)

                else -> {
                    val name = currentName
                        ?: throw IllegalArgumentException("line $lineNumber: row outside a sprite")
                    require(!line.any { it.isWhitespace() }) {
                        "line $lineNumber: whitespace inside sprite '$name'"
                    }
                    currentRows += line
                }
            }
        }

        require(currentName == null) { "sprite '$currentName' is missing 'end'" }
        return frames
    }

    /** Rotates [frame] 90 degrees clockwise, keeping its name. */
    fun rotate90(frame: SpriteFrame): SpriteFrame {
        val rows = List(frame.width) { x ->
            buildString(frame.height) {
                for (y in frame.height - 1 downTo 0) {
                    append(frame.rows[y][x])
                }
            }
        }
        return SpriteFrame(frame.name, frame.height, frame.width, rows)
    }
}
