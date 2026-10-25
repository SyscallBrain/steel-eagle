package pt.tiago.steeleagle.core.level

import pt.tiago.steeleagle.core.rules.EnemyRoster

/** Result of validating one level; the editor shows these messages. */
data class LevelValidation(val errors: List<String>) {
    val isValid: Boolean
        get() = errors.isEmpty()
}

/**
 * Gameplay validation. Structural issues (size and
 * characters) are checked here too so editor-made levels can be validated
 * without going through LevelParser first.
 *
 * The standard brick eagle wall and the content tier/density rules are project
 * policies enforced by the asset tests and the level generator, not gameplay
 * requirements. The eagle-exterior target follows the standard wall ring;
 * custom editor surrounds will need a parameterized target.
 */
object LevelValidator {

    const val SIZE = 26

    private val TERRAIN = setOf('.', 'B', 'S', 'W', 'T', 'I')
    private val ENEMY_PASSABLE = setOf('.', 'B', 'T', 'I')
    private val PLAYER_PASSABLE = setOf('.', 'B', 'T', 'I')

    private val ENEMY_SPAWNS = listOf(0 to 0, 12 to 0, 24 to 0)
    private val PLAYER_SPAWNS = listOf(8 to 24, 16 to 24)
    private val EAGLE_CELLS = areaCells(12, 24).toSet()

    /** Cells immediately around the standard eagle wall, on the field side. */
    private val EAGLE_EXTERIOR: Set<Pair<Int, Int>> = buildSet {
        for (x in 10..15) {
            for (y in 22..25) {
                if (x == 10 || x == 15 || y == 22) add(x to y)
            }
        }
    }

    fun validate(level: Level): LevelValidation {
        val errors = mutableListOf<String>()
        val map = level.map

        if (map.size != SIZE) {
            errors += "map must have $SIZE rows, got ${map.size}"
            return LevelValidation(errors)
        }

        map.forEachIndexed { y, row ->
            if (row.length != SIZE) {
                errors += "row $y must have $SIZE cells, got ${row.length}"
            } else {
                row.forEachIndexed { x, char ->
                    if (char !in TERRAIN) {
                        errors += "row $y column $x has invalid terrain '$char'"
                    }
                }
            }
        }
        if (errors.isNotEmpty()) return LevelValidation(errors)

        try {
            EnemyRoster.parse(level.enemies, seed = 1)
        } catch (exception: IllegalArgumentException) {
            errors += "enemies: ${exception.message}"
        }

        for ((label, spawn) in enemySpawnZones()) {
            validateFree(errors, map, label, spawn.first, spawn.second)
        }
        for ((label, spawn) in playerSpawnZones()) {
            validateFree(errors, map, label, spawn.first, spawn.second)
        }
        validateFree(errors, map, "eagle", 12, 24)

        val enemySources = ENEMY_SPAWNS.flatMap { areaCells(it.first, it.second) }.toSet()
        if (!reachable(map, enemySources, EAGLE_CELLS, ENEMY_PASSABLE)) {
            errors += "no path from an enemy spawn to the eagle"
        }

        for ((label, spawn) in playerSpawnZones()) {
            val sources = areaCells(spawn.first, spawn.second).toSet()
            if (!reachable(map, sources, EAGLE_EXTERIOR, PLAYER_PASSABLE)) {
                errors += "no path from $label spawn to the eagle exterior"
            }
        }

        return LevelValidation(errors)
    }

    private fun enemySpawnZones(): List<Pair<String, Pair<Int, Int>>> =
        ENEMY_SPAWNS.mapIndexed { index, spawn ->
            "enemy spawn ${index + 1}" to spawn
        }

    private fun playerSpawnZones(): List<Pair<String, Pair<Int, Int>>> =
        PLAYER_SPAWNS.mapIndexed { index, spawn ->
            "player ${index + 1}" to spawn
        }

    private fun validateFree(
        errors: MutableList<String>,
        map: List<String>,
        label: String,
        x: Int,
        y: Int,
    ) {
        for ((cellX, cellY) in areaCells(x, y)) {
            if (map[cellY][cellX] != '.') {
                errors += "$label at ($x,$y) must be empty"
                return
            }
        }
    }

    private fun reachable(
        map: List<String>,
        sources: Set<Pair<Int, Int>>,
        targets: Set<Pair<Int, Int>>,
        passable: Set<Char>,
    ): Boolean {
        val visited = Array(SIZE) { BooleanArray(SIZE) }
        val queue = ArrayDeque<Pair<Int, Int>>()

        for (source in sources) {
            val (x, y) = source
            if (x !in 0 until SIZE || y !in 0 until SIZE) continue
            if (map[y][x] !in passable || visited[y][x]) continue
            visited[y][x] = true
            queue.addLast(source)
        }

        while (queue.isNotEmpty()) {
            val (x, y) = queue.removeFirst()
            if ((x to y) in targets) return true

            for ((dx, dy) in DIRECTIONS) {
                val nextX = x + dx
                val nextY = y + dy
                if (nextX !in 0 until SIZE || nextY !in 0 until SIZE) continue
                if (visited[nextY][nextX]) continue
                if (map[nextY][nextX] !in passable) continue
                visited[nextY][nextX] = true
                queue.addLast(nextX to nextY)
            }
        }
        return false
    }

    private fun areaCells(x: Int, y: Int): List<Pair<Int, Int>> = listOf(
        x to y,
        x + 1 to y,
        x to y + 1,
        x + 1 to y + 1,
    )

    private val DIRECTIONS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
}
