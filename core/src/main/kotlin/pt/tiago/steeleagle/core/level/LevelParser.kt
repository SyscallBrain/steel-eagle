package pt.tiago.steeleagle.core.level

data class Level(
    val name: String,
    val enemies: String,
    val map: List<String>,
)

/**
 * Parses the Steel Eagle level text format. Structural validation only;
 * spawn/path validation belongs to LevelValidator in phase 8.
 */
object LevelParser {
    private const val SIZE = 26
    private const val TERRAIN_CHARS = ".BSWTI"

    fun parse(text: String): Level {
        var name: String? = null
        var enemies = ""
        var mapStarted = false
        val map = mutableListOf<String>()

        text.lines().forEachIndexed { index, raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed

            if (!mapStarted) {
                when {
                    line.startsWith("name:") -> name = line.removePrefix("name:").trim()
                    line.startsWith("enemies:") -> enemies = line.removePrefix("enemies:").trim()
                    line == "map:" -> mapStarted = true
                    else -> throw IllegalArgumentException(
                        "line ${index + 1}: unexpected '$line'",
                    )
                }
            } else {
                map += line
            }
        }

        val levelName = name?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("missing name")
        require(mapStarted) { "missing map" }
        require(map.size == SIZE) { "map must have $SIZE rows, got ${map.size}" }

        map.forEachIndexed { row, line ->
            require(line.length == SIZE) { "row $row must have $SIZE cells" }
            for (char in line) {
                require(char in TERRAIN_CHARS) {
                    "row $row has unknown terrain character '$char'"
                }
            }
        }

        return Level(levelName, enemies, map)
    }
}
