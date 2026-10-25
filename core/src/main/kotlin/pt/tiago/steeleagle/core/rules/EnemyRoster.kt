package pt.tiago.steeleagle.core.rules

import pt.tiago.steeleagle.core.model.Rng

/** One enemy slot in the level's roster, including its flashing drop flag. */
data class EnemySpawn(
    val type: EnemyType,
    val flashing: Boolean,
)

/**
 * Expands the level's `enemies:` spec and shuffles it with a fixed seed.
 * Enemies 4, 11 and 18 are the flashing ones.
 */
object EnemyRoster {
    const val TOTAL = 20
    private val FLASHING_INDICES = setOf(3, 10, 17)

    fun parse(spec: String, seed: Int): List<EnemySpawn> {
        val types = expandTypes(spec)
        require(types.size == TOTAL) {
            "enemy roster must have $TOTAL enemies, got ${types.size}"
        }

        val rng = Rng(seed)
        val shuffled = types.toMutableList()
        for (index in shuffled.lastIndex downTo 1) {
            val other = rng.nextInt(index + 1)
            val value = shuffled[index]
            shuffled[index] = shuffled[other]
            shuffled[other] = value
        }

        return shuffled.mapIndexed { index, type ->
            EnemySpawn(type = type, flashing = index in FLASHING_INDICES)
        }
    }

    fun typeFor(char: Char): EnemyType = when (char.uppercaseChar()) {
        'B' -> EnemyType.BASIC
        'F' -> EnemyType.FAST
        'P' -> EnemyType.POWER
        'A' -> EnemyType.ARMORED
        else -> throw IllegalArgumentException("unknown enemy type '$char'")
    }

    /** Expands a spec without requiring the full 20; the editor uses it. */
    fun expandTypes(spec: String): List<EnemyType> {
        if ('*' !in spec) {
            return spec.filterNot { it.isWhitespace() }.map(::typeFor)
        }

        val types = mutableListOf<EnemyType>()
        for (token in spec.split(WHITESPACE)) {
            if (token.isBlank()) continue
            val parts = token.split('*')
            require(parts.size == 2 && parts[0].length == 1) {
                "bad enemy token '$token'"
            }
            val type = typeFor(parts[0].first())
            val count = parts[1].toIntOrNull()
                ?: throw IllegalArgumentException("bad enemy count in '$token'")
            require(count > 0) { "enemy count must be positive in '$token'" }
            repeat(count) { types += type }
        }
        return types
    }

    private val WHITESPACE = Regex("\\s+")
}
