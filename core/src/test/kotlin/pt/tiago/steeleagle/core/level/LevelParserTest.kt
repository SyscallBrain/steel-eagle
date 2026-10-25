package pt.tiago.steeleagle.core.level

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LevelParserTest {

    private val emptyMap = List(26) { ".".repeat(26) }

    @Test
    fun `parses name enemies and map`() {
        val text = buildString {
            appendLine("# Steel Eagle level")
            appendLine("name: Front Line")
            appendLine("enemies: B*14 F*4 P*2")
            appendLine("map:")
            emptyMap.forEach { appendLine(it) }
        }

        val level = LevelParser.parse(text)

        assertEquals("Front Line", level.name)
        assertEquals("B*14 F*4 P*2", level.enemies)
        assertEquals(26, level.map.size)
        assertEquals(".".repeat(26), level.map[0])
    }

    @Test
    fun `rejects a row with the wrong width`() {
        val rows = emptyMap.toMutableList()
        rows[3] = ".".repeat(25)

        assertFailsWith<IllegalArgumentException> { LevelParser.parse(levelText(rows)) }
    }

    @Test
    fun `rejects the wrong number of rows`() {
        assertFailsWith<IllegalArgumentException> {
            LevelParser.parse(levelText(emptyMap.dropLast(1)))
        }
    }

    @Test
    fun `rejects an unknown terrain character`() {
        val rows = emptyMap.toMutableList()
        rows[5] = ".".repeat(10) + "X" + ".".repeat(15)

        assertFailsWith<IllegalArgumentException> { LevelParser.parse(levelText(rows)) }
    }

    @Test
    fun `rejects a missing map`() {
        assertFailsWith<IllegalArgumentException> {
            LevelParser.parse("name: Broken\nenemies: B*20\n")
        }
    }

    @Test
    fun `rejects a missing name`() {
        assertFailsWith<IllegalArgumentException> {
            LevelParser.parse(levelText(emptyMap).replace("name: Test\n", ""))
        }
    }

    private fun levelText(rows: List<String>): String = buildString {
        appendLine("name: Test")
        appendLine("enemies: B*20")
        appendLine("map:")
        rows.forEach { appendLine(it) }
    }
}
