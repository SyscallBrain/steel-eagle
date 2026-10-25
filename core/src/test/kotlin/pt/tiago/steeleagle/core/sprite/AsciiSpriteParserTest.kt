package pt.tiago.steeleagle.core.sprite

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AsciiSpriteParserTest {

    @Test
    fun `parses named sprites and ignores comments and blank lines`() {
        val text = """
            # tanks
            sprite tank_up_0
            .KK.
            KYYK
            end

            sprite tank_up_1
            .LL.
            LYYL
            end
        """.trimIndent()

        val frames = AsciiSpriteParser.parse(text)

        assertEquals(2, frames.size)
        assertEquals("tank_up_0", frames[0].name)
        assertEquals(4, frames[0].width)
        assertEquals(2, frames[0].height)
        assertEquals(listOf(".KK.", "KYYK"), frames[0].rows)
        assertEquals("tank_up_1", frames[1].name)
    }

    @Test
    fun `rejects rows with different widths`() {
        val text = """
            sprite broken
            KK
            KKK
            end
        """.trimIndent()

        assertFailsWith<IllegalArgumentException> { AsciiSpriteParser.parse(text) }
    }

    @Test
    fun `rejects an empty sprite`() {
        val text = """
            sprite empty
            end
        """.trimIndent()

        assertFailsWith<IllegalArgumentException> { AsciiSpriteParser.parse(text) }
    }

    @Test
    fun `rejects a sprite without an end marker`() {
        val text = """
            sprite open
            KK
            KK
        """.trimIndent()

        assertFailsWith<IllegalArgumentException> { AsciiSpriteParser.parse(text) }
    }

    @Test
    fun `rejects duplicate sprite names`() {
        val text = """
            sprite same
            KK
            end
            sprite same
            KK
            end
        """.trimIndent()

        assertFailsWith<IllegalArgumentException> { AsciiSpriteParser.parse(text) }
    }

    @Test
    fun `rotates a sprite clockwise`() {
        val frame = AsciiSpriteParser.parse(
            """
                sprite abc
                AB
                CD
                EF
                end
            """.trimIndent(),
        ).single()

        val rotated = AsciiSpriteParser.rotate90(frame)

        assertEquals(3, rotated.width)
        assertEquals(2, rotated.height)
        assertEquals(listOf("ECA", "FDB"), rotated.rows)
    }

    @Test
    fun `four rotations return the original sprite`() {
        val frame = AsciiSpriteParser.parse(
            """
                sprite abc
                AB
                CD
                EF
                end
            """.trimIndent(),
        ).single()

        val result = AsciiSpriteParser.rotate90(
            AsciiSpriteParser.rotate90(
                AsciiSpriteParser.rotate90(
                    AsciiSpriteParser.rotate90(frame),
                ),
            ),
        )

        assertEquals(frame, result)
    }
}
