package pt.tiago.steeleagle.core.level

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LevelValidatorTest {

    @Test
    fun `a standard open map is valid`() {
        val result = LevelValidator.validate(level(baseMap()))

        assertTrue(result.isValid, result.errors.toString())
    }

    @Test
    fun `brick alone does not cut the enemy path`() {
        val map = baseMap()
        for (x in 0 until LevelValidator.SIZE) map[10][x] = 'B'

        val result = LevelValidator.validate(level(map))

        assertTrue(result.isValid, result.errors.toString())
    }

    @Test
    fun `trees alone do not cut the enemy path`() {
        val map = baseMap()
        for (x in 0 until LevelValidator.SIZE) map[10][x] = 'T'

        val result = LevelValidator.validate(level(map))

        assertTrue(result.isValid, result.errors.toString())
    }

    @Test
    fun `ice alone does not cut the enemy path`() {
        val map = baseMap()
        for (x in 0 until LevelValidator.SIZE) map[10][x] = 'I'

        val result = LevelValidator.validate(level(map))

        assertTrue(result.isValid, result.errors.toString())
    }

    @Test
    fun `steel or water across the field cuts the enemy path`() {
        for (blocker in listOf('S', 'W')) {
            val map = baseMap()
            for (x in 0 until LevelValidator.SIZE) map[10][x] = blocker

            val result = LevelValidator.validate(level(map))

            assertFalse(result.isValid)
            assertTrue(
                result.errors.any { it.contains("enemy spawn") },
                result.errors.toString(),
            )
        }
    }

    @Test
    fun `blocked enemy spawn is reported on any cell of the 2x2 zone`() {
        for ((x, y) in listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1)) {
            val map = baseMap()
            map[y][x] = 'S'

            val result = LevelValidator.validate(level(map))

            assertFalse(result.isValid)
            assertTrue(
                result.errors.any { it.contains("enemy spawn 1") },
                result.errors.toString(),
            )
        }
    }

    @Test
    fun `every spawn zone is checked on every 2x2 cell`() {
        val zones = listOf(
            "enemy spawn 1" to (0 to 0),
            "enemy spawn 2" to (12 to 0),
            "enemy spawn 3" to (24 to 0),
            "player 1" to (8 to 24),
            "player 2" to (16 to 24),
            "eagle" to (12 to 24),
        )
        for ((label, origin) in zones) {
            for ((dx, dy) in listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1)) {
                val map = baseMap()
                map[origin.second + dy][origin.first + dx] = 'S'

                val result = LevelValidator.validate(level(map))

                assertFalse(result.isValid)
                assertTrue(
                    result.errors.any { it.contains(label) && it.contains("empty") },
                    "$label $dx,$dy: ${result.errors}",
                )
            }
        }
    }

    @Test
    fun `blocked eagle area is reported on any cell of the 2x2 zone`() {
        for ((x, y) in listOf(12 to 24, 13 to 24, 12 to 25, 13 to 25)) {
            val map = baseMap()
            map[y][x] = 'B'

            val result = LevelValidator.validate(level(map))

            assertFalse(result.isValid)
            assertTrue(result.errors.any { it.contains("eagle") }, result.errors.toString())
        }
    }

    @Test
    fun `blocked player spawn is reported on any cell of the 2x2 zone`() {
        for ((x, y) in listOf(8 to 24, 9 to 24, 8 to 25, 9 to 25)) {
            val map = baseMap()
            map[y][x] = 'S'

            val result = LevelValidator.validate(level(map))

            assertFalse(result.isValid)
            assertTrue(
                result.errors.any { it.contains("player 1") && it.contains("empty") },
                result.errors.toString(),
            )
        }
    }

    @Test
    fun `a boxed-in player spawn has no path to the eagle exterior`() {
        val map = baseMap()
        for (x in 7..10) map[23][x] = 'S'
        for (y in 24..25) {
            map[y][7] = 'S'
            map[y][10] = 'S'
        }

        val result = LevelValidator.validate(level(map))

        assertFalse(result.isValid)
        assertTrue(
            result.errors.any { it.contains("player 1") },
            result.errors.toString(),
        )
        assertFalse(result.errors.any { it.contains("player 2") })
    }

    @Test
    fun `player two has its own path check`() {
        val map = baseMap()
        for (x in 15..18) map[23][x] = 'S'
        for (y in 24..25) {
            map[y][15] = 'S'
            map[y][18] = 'S'
        }

        val result = LevelValidator.validate(level(map))

        assertFalse(result.isValid)
        assertTrue(
            result.errors.any { it.contains("player 2") },
            result.errors.toString(),
        )
        assertFalse(result.errors.any { it.contains("player 1") })
    }

    @Test
    fun `reports structural map errors`() {
        val shortRows = baseMap().dropLast(1)
        val shortRow = baseMap().also { it[5] = MutableList(25) { '.' } }
        val badChar = baseMap().also { it[5][0] = 'X' }

        assertFalse(LevelValidator.validate(level(shortRows)).isValid)
        assertFalse(LevelValidator.validate(level(shortRow)).isValid)
        assertFalse(LevelValidator.validate(level(badChar)).isValid)
    }

    @Test
    fun `reports an invalid enemy roster`() {
        val short = LevelValidator.validate(level(baseMap(), enemies = "B*19"))
        val long = LevelValidator.validate(level(baseMap(), enemies = "B*21"))
        val unknown = LevelValidator.validate(level(baseMap(), enemies = "X*20"))
        val malformed = LevelValidator.validate(level(baseMap(), enemies = "*20"))

        assertFalse(short.isValid)
        assertTrue(short.errors.any { it.contains("enemies") }, short.errors.toString())
        assertFalse(long.isValid)
        assertTrue(long.errors.any { it.contains("enemies") }, long.errors.toString())
        assertFalse(unknown.isValid)
        assertTrue(unknown.errors.any { it.contains("enemies") }, unknown.errors.toString())
        assertFalse(malformed.isValid)
        assertTrue(malformed.errors.any { it.contains("enemies") }, malformed.errors.toString())
    }

    private fun level(
        map: List<List<Char>>,
        enemies: String = "B*20",
    ): Level = Level(
        name = "test",
        enemies = enemies,
        map = map.map { it.joinToString("") },
    )

    private fun baseMap(): MutableList<MutableList<Char>> {
        val map = MutableList(LevelValidator.SIZE) {
            MutableList(LevelValidator.SIZE) { '.' }
        }
        for (x in 11..14) map[23][x] = 'B'
        for (y in 24..25) {
            map[y][11] = 'B'
            map[y][14] = 'B'
        }
        return map
    }
}
