package pt.tiago.steeleagle

import pt.tiago.steeleagle.core.game.GameSession
import pt.tiago.steeleagle.core.level.Level
import pt.tiago.steeleagle.core.level.LevelParser
import pt.tiago.steeleagle.core.level.LevelValidator
import pt.tiago.steeleagle.core.model.PlayerInput
import pt.tiago.steeleagle.core.rules.Difficulty
import pt.tiago.steeleagle.core.rules.EnemyRoster
import pt.tiago.steeleagle.core.rules.EnemyType
import pt.tiago.steeleagle.core.world.InputFrame
import pt.tiago.steeleagle.core.world.World
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AllLevelsTest {

    private val levelsDir = File("src/main/assets/levels")

    private val levels: List<Level> by lazy {
        levelsDir.listFiles { file -> file.extension == "txt" }
            .orEmpty()
            .sortedBy { it.name }
            .map { file ->
                LevelParser.parse(file.readText())
            }
    }

    @Test
    fun `the catalog has exactly fifty numbered levels`() {
        val files = levelsDir.listFiles { file -> file.extension == "txt" }
            .orEmpty()
            .sortedBy { it.name }

        assertEquals(50, files.size)
        assertEquals(
            (1..50).map { "%03d.txt".format(it) },
            files.map { it.name },
        )
        assertEquals(50, levels.size)
        assertEquals(50, levels.map { it.name }.toSet().size)
    }

    @Test
    fun `all fifty maps are distinct`() {
        val maps = levels.map { level -> level.map.joinToString("\n") }

        assertEquals(50, maps.toSet().size)
    }

    @Test
    fun `all levels pass the validator`() {
        val failures = levels.mapIndexedNotNull { index, level ->
            val validation = LevelValidator.validate(level)
            if (validation.isValid) {
                null
            } else {
                "level ${index + 1}: ${validation.errors.joinToString("; ")}"
            }
        }

        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `all levels keep the standard brick eagle wall`() {
        for ((index, level) in levels.withIndex()) {
            for (x in 11..14) {
                assertEquals("level ${index + 1} wall top at $x", 'B', level.map[23][x])
            }
            for (y in 24..25) {
                assertEquals("level ${index + 1} wall left at $y", 'B', level.map[y][11])
                assertEquals("level ${index + 1} wall right at $y", 'B', level.map[y][14])
            }
        }
    }

    @Test
    fun `enemy mix follows the difficulty curve`() {
        for ((index, level) in levels.withIndex()) {
            val number = index + 1
            val types = EnemyRoster.parse(level.enemies, seed = 1).map { it.type }
            val basic = types.count { it == EnemyType.BASIC }
            val fast = types.count { it == EnemyType.FAST }
            val power = types.count { it == EnemyType.POWER }
            val armored = types.count { it == EnemyType.ARMORED }

            assertEquals("level $number roster", 20, types.size)
            when (number) {
                in 1..10 -> {
                    assertEquals("level $number armored", 0, armored)
                    assertTrue("level $number power", power <= 5)
                    assertTrue("level $number basic/fast", basic + fast >= 13)
                }

                in 11..15 -> {
                    assertTrue("level $number power", power >= 6)
                    assertEquals("level $number armored", 0, armored)
                }

                in 16..25 -> {
                    assertTrue("level $number power", power >= 8)
                    assertTrue("level $number armored", armored in 1..3)
                }

                in 26..40 -> {
                    assertTrue("level $number power", power >= 8)
                    assertTrue("level $number armored", armored >= 4)
                    assertTrue("level $number ice", level.map.any { 'I' in it })
                }

                else -> {
                    assertTrue("level $number power", power >= 8)
                    assertTrue("level $number armored", armored >= 8)
                    assertTrue("level $number mix", power + armored >= 16)
                }
            }
        }
    }

    @Test
    fun `terrain mix follows the tier curve`() {
        for ((index, level) in levels.withIndex()) {
            val number = index + 1
            if (number in 11..25) {
                for (char in "SWT") {
                    assertTrue(
                        "level $number needs at least 12 $char",
                        count(level, char) >= 12,
                    )
                }
            }
            if (number in 26..40) {
                assertTrue("level $number needs ice", count(level, 'I') >= 12)
                for (char in "SWT") {
                    assertTrue(
                        "level $number needs at least 8 $char",
                        count(level, char) >= 8,
                    )
                }
            }
            if (number >= 41) {
                for (char in "SWTI") {
                    assertTrue(
                        "level $number needs at least 12 $char",
                        count(level, char) >= 12,
                    )
                }
            }
            if (number in 2..10) {
                assertEquals("level $number steel", 0, count(level, 'S'))
                assertEquals("level $number water", 0, count(level, 'W'))
            }
        }
    }

    @Test
    fun `density rises by tier and stays inside its band`() {
        // 001 is the hand-made tutorial; the curve covers 002-050.
        val density = levels.map { level -> density(level) }
        val tierOne = density.subList(1, 10)
        val tierTwo = density.subList(10, 25)
        val tierThree = density.subList(25, 40)
        val tierFour = density.subList(40, 50)

        assertTrue("tier averages", tierOne.average() < tierTwo.average())
        assertTrue("tier averages", tierTwo.average() < tierThree.average())
        assertTrue("tier averages", tierThree.average() < tierFour.average())

        assertBand(tierOne, 0.28, 0.38)
        assertBand(tierTwo, 0.32, 0.46)
        assertBand(tierThree, 0.40, 0.54)
        assertBand(tierFour, 0.47, 0.60)
    }

    @Test
    fun `generated levels keep the reserved generator frame`() {
        for (index in 1 until levels.size) {
            val map = levels[index].map
            val number = index + 1
            for (y in 0..3) {
                assertTrue("level $number top row $y", map[y].all { it == '.' })
            }
            assertTrue("level $number row 22", map[22].all { it == '.' })
            for (y in 0..22) {
                assertTrue("level $number left margin $y", map[y][0] == '.')
                assertTrue("level $number left margin $y", map[y][1] == '.')
                assertTrue("level $number right margin $y", map[y][24] == '.')
                assertTrue("level $number right margin $y", map[y][25] == '.')
            }
        }
    }

    @Test
    fun `the campaign cycles after stage fifty`() {
        val session = GameSession(
            levels = levels,
            seed = 1,
            difficulty = Difficulty.NORMAL,
            startLevelIndex = 49,
        )

        assertEquals(50, session.levelNumber)
        session.nextLevel()

        assertEquals(1, session.levelNumber)
        assertEquals(1, session.cycle)
    }

    @Test
    fun `every level builds a world and spawns an enemy`() {
        for ((index, level) in levels.withIndex()) {
            val world = World.fromLevel(level, seed = 1, levelNumber = index + 1)
            assertEquals("level ${index + 1} roster", 20, world.spawnDirector.total)
            repeat(300) {
                world.step(InputFrame(PlayerInput.NONE))
            }
            assertTrue(
                "level ${index + 1} never spawned",
                world.spawnDirector.spawnedCount > 0,
            )
        }
    }

    private fun count(level: Level, char: Char): Int =
        level.map.sumOf { row -> row.count { it == char } }

    private fun density(level: Level): Double {
        var filled = 0
        for (y in 4..21) {
            for (x in 2..23) {
                if (level.map[y][x] != '.') filled++
            }
        }
        return filled / (18.0 * 22.0)
    }

    private fun assertBand(values: List<Double>, low: Double, high: Double) {
        for (value in values) {
            assertTrue("density $value outside $low..$high", value in low..high)
        }
    }
}
