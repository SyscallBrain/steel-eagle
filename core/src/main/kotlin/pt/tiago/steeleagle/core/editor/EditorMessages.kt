package pt.tiago.steeleagle.core.editor

/**
 * Turns [pt.tiago.steeleagle.core.level.LevelValidator] messages into short
 * editor messages. The bitmap font has no brackets or commas, so only glyphs
 * the font can draw are kept.
 */
object EditorMessages {

    private const val SAFE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 :./-!?+"

    fun friendly(errors: List<String>): List<String> {
        if (errors.isEmpty()) return listOf("UNKNOWN ERROR")
        return errors.map(::one)
    }

    private fun one(error: String): String = when {
        error.startsWith("enemy spawn ") && error.contains("must be empty") ->
            "ENEMY SPAWN ${spawnNumber(error)} BLOCKED"

        error.startsWith("player ") && error.contains("must be empty") ->
            "PLAYER ${spawnNumber(error)} SPAWN BLOCKED"

        error.startsWith("eagle ") && error.contains("must be empty") ->
            "EAGLE AREA BLOCKED"

        error.startsWith("no path from an enemy spawn") ->
            "NO PATH TO THE EAGLE"

        error.startsWith("no path from player ") ->
            "NO PATH FROM PLAYER ${spawnNumber(error)}"

        error.startsWith("enemies:") && error.contains("roster must have") ->
            "ENEMY ROSTER MUST TOTAL 20"

        else -> sanitize(error)
    }

    /** Reads the N of `enemy spawn N` or `player N`. */
    private fun spawnNumber(error: String): String {
        val digits = error.dropWhile { !it.isDigit() }.takeWhile { it.isDigit() }
        return digits.ifEmpty { "1" }
    }

    fun sanitize(text: String): String {
        val clean = text.uppercase().map { if (it in SAFE) it else ' ' }
        return clean.joinToString("").trim()
    }

    /** Word wrap that also splits tokens longer than [limit]. */
    fun wrap(text: String, limit: Int): List<String> {
        require(limit > 0) { "limit must be positive" }
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in text.split(' ')) {
            var rest = word
            while (rest.length > limit) {
                if (current.isNotEmpty()) {
                    lines += current.toString()
                    current = StringBuilder()
                }
                lines += rest.take(limit)
                rest = rest.drop(limit)
            }
            if (current.isEmpty()) {
                current.append(rest)
            } else if (current.length + 1 + rest.length <= limit) {
                current.append(' ').append(rest)
            } else {
                lines += current.toString()
                current = StringBuilder(rest)
            }
        }
        if (current.isNotEmpty()) lines += current.toString()
        return lines
    }
}
