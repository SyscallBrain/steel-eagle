package pt.tiago.steeleagle.core.level

/** Serializes a [Level] back to the text format used by assets and user files. */
object LevelWriter {

    fun serialize(level: Level): String = buildString {
        appendLine("# Steel Eagle level")
        appendLine("name: ${level.name.replace('\n', ' ').trim()}")
        appendLine("enemies: ${level.enemies}")
        appendLine("map:")
        for (row in level.map) appendLine(row)
    }
}
