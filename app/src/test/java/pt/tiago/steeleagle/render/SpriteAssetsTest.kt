package pt.tiago.steeleagle.render

import pt.tiago.steeleagle.core.level.LevelParser
import pt.tiago.steeleagle.core.model.Dir
import pt.tiago.steeleagle.core.rules.PowerUpType
import pt.tiago.steeleagle.core.sprite.AsciiSpriteParser
import pt.tiago.steeleagle.core.sprite.EnemySprites
import pt.tiago.steeleagle.core.sprite.SpriteFrame
import pt.tiago.steeleagle.core.sprite.TankSprites
import pt.tiago.steeleagle.core.world.Terrain
import pt.tiago.steeleagle.core.world.TerrainGrid
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpriteAssetsTest {

    private val spriteDir = File("src/main/assets/sprites")

    @Test
    fun `every sprite asset parses and uses known palette keys`() {
        assertTrue("missing $spriteDir", spriteDir.isDirectory)
        val files = spriteDir.listFiles { file -> file.extension == "txt" }.orEmpty()
        assertTrue(files.isNotEmpty())

        val frames = files.flatMap { file ->
            AsciiSpriteParser.parse(file.readText())
        }

        assertTrue(frames.isNotEmpty())
        for (frame in frames) {
            for (row in frame.rows) {
                for (key in row) {
                    Palette.colorFor(key)
                }
            }
        }
    }

    @Test
    fun `tank has two square frames for the atlas rotations`() {
        val frames = loadAll().filter { it.name.startsWith("tank_up_") }

        assertEquals(2, frames.size)
        for (frame in frames) {
            assertEquals(16, frame.width)
            assertEquals(16, frame.height)
        }
    }

    @Test
    fun `font glyphs are eight by eight`() {
        val glyphs = loadAll().filter { it.name.startsWith("glyph_") }

        assertEquals(44, glyphs.size)
        for (glyph in glyphs) {
            assertEquals(glyph.name, 8, glyph.width)
            assertEquals(glyph.name, 8, glyph.height)
        }
    }

    @Test
    fun `font covers every character used by the shell`() {
        val names = loadAll().map { it.name }.toSet()

        for (char in "STEEL EAGLEFPSD-PADFIRE0123456789:.-/!?+") {
            val name = when (char) {
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
            assertTrue("missing glyph for '$char'", "glyph_$name" in names)
        }
    }

    @Test
    fun `tank keeps its facing visible on a black field`() {
        val frames = loadAll()
        val up = frames.first { it.name == "tank_up_0" }
        val upFrame1 = frames.first { it.name == "tank_up_1" }
        val right = AsciiSpriteParser.rotate90(up)
        val down = AsciiSpriteParser.rotate90(right)
        val left = AsciiSpriteParser.rotate90(down)

        assertNotEquals(visibleMask(up), visibleMask(down))
        assertNotEquals(visibleMask(right), visibleMask(left))
        assertNotEquals(visibleMask(up), visibleMask(right))
        assertNotEquals(visibleMask(up), visibleMask(upFrame1))

        // The barrel must be body-coloured, otherwise it disappears on black.
        val barrelYellow = (0..2).sumOf { y -> up.rows[y].count { it == 'Y' } }
        assertTrue("barrel has no yellow pixels", barrelYellow >= 6)
    }

    @Test
    fun `atlas names cover every tank direction and frame`() {
        val frames = loadAll()
        val names = mutableSetOf<String>()

        for (frame in frames.filter { it.name.startsWith("tank_up_") }) {
            val frameIndex = frame.name.substringAfterLast('_').toInt()
            names += TankSprites.name(Dir.UP, frameIndex)

            var rotated = frame
            for (direction in listOf(Dir.RIGHT, Dir.DOWN, Dir.LEFT)) {
                rotated = AsciiSpriteParser.rotate90(rotated)
                names += TankSprites.name(direction, frameIndex)
            }
        }

        for (direction in Dir.entries) {
            for (frameIndex in 0..1) {
                val name = TankSprites.name(direction, frameIndex)
                assertTrue("missing $name", name in names)
            }
        }
    }

    @Test
    fun `terrain and actor sprites have expected sizes`() {
        val byName = loadAll().associateBy { it.name }

        for (name in listOf(
            "terrain_brick",
            "terrain_steel",
            "terrain_water_0",
            "terrain_water_1",
            "terrain_trees",
            "terrain_ice",
            "bullet",
        )) {
            val frame = requireNotNull(byName[name]) { "missing $name" }
            assertEquals(name, 4, frame.width)
            assertEquals(name, 4, frame.height)
        }

        for (name in listOf(
            "eagle_alive",
            "eagle_dead",
            "enemy_up_0",
            "enemy_up_1",
            "spawn_star_0",
            "spawn_star_1",
            "spawn_star_2",
            "spawn_star_3",
            "shield_0",
            "shield_1",
            "power_star",
            "power_grenade",
            "power_helmet",
            "power_shovel",
            "power_clock",
            "power_tank",
            "power_boat",
            "power_gun",
            "boat_ring",
            "explosion_small_0",
            "explosion_small_1",
            "explosion_small_2",
            "explosion_large_0",
            "explosion_large_1",
        )) {
            val frame = requireNotNull(byName[name]) { "missing $name" }
            assertEquals(name, 16, frame.width)
            assertEquals(name, 16, frame.height)
        }

        for (name in listOf("hud_enemy", "hud_flag")) {
            val frame = requireNotNull(byName[name]) { "missing $name" }
            assertEquals(name, 8, frame.width)
            assertEquals(name, 8, frame.height)
        }
    }

    @Test
    fun `every power up type has an icon`() {
        val names = loadAll().map { it.name }.toSet()

        for (type in PowerUpType.entries) {
            val name = PowerUpIcons.name(type)
            assertTrue("missing icon for $type", name in names)
        }
    }

    @Test
    fun `atlas names cover every enemy direction and frame`() {
        val frames = loadAll()
        val names = mutableSetOf<String>()

        for (frame in frames.filter { it.name.startsWith("enemy_up_") }) {
            val frameIndex = frame.name.substringAfterLast('_').toInt()
            names += EnemySprites.name(Dir.UP, frameIndex)

            var rotated = frame
            for (direction in listOf(Dir.RIGHT, Dir.DOWN, Dir.LEFT)) {
                rotated = AsciiSpriteParser.rotate90(rotated)
                names += EnemySprites.name(direction, frameIndex)
            }
        }

        for (direction in Dir.entries) {
            for (frameIndex in 0..1) {
                val name = EnemySprites.name(direction, frameIndex)
                assertTrue("missing $name", name in names)
            }
        }
    }

    @Test
    fun `test level keeps spawn and eagle zones clear`() {
        val file = File("src/main/assets/levels/001.txt")
        assertTrue("missing $file", file.isFile)

        val level = LevelParser.parse(file.readText())
        assertEquals(26, level.map.size)

        val grid = TerrainGrid.fromRows(level.map)
        for (blockY in 24..25) {
            for (blockX in 8..9) assertClear(grid, blockX, blockY)
            for (blockX in 12..13) assertClear(grid, blockX, blockY)
        }

        // Enemy spawns: blocks (0,0), (6,0) and (12,0).
        for (spawn in listOf(0 to 0, 6 to 0, 12 to 0)) {
            for (blockY in spawn.second * 2..spawn.second * 2 + 1) {
                for (blockX in spawn.first * 2..spawn.first * 2 + 1) {
                    assertClear(grid, blockX, blockY)
                }
            }
        }
    }

    private fun assertClear(grid: TerrainGrid, blockX: Int, blockY: Int) {
        for (dy in 0..1) {
            for (dx in 0..1) {
                assertEquals(
                    "block $blockX,$blockY must be empty",
                    Terrain.EMPTY,
                    grid.get(blockX * 2 + dx, blockY * 2 + dy),
                )
            }
        }
    }

    private fun visibleMask(frame: SpriteFrame): String =
        frame.rows.joinToString("\n") { row ->
            row.map { key -> if (key == '.' || key == 'K') '.' else key }.joinToString("")
        }

    private fun loadAll(): List<SpriteFrame> =
        spriteDir.listFiles { file -> file.extension == "txt" }.orEmpty()
            .sorted()
            .flatMap { AsciiSpriteParser.parse(it.readText()) }
}
