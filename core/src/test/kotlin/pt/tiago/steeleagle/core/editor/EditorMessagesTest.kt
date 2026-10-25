package pt.tiago.steeleagle.core.editor

import pt.tiago.steeleagle.core.level.LevelValidator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EditorMessagesTest {

    @Test
    fun `spawn and path errors have short readable messages`() {
        val map = EditorMap.template()
        map.stamp(Brush.BRICK_FULL, 0, 0)
        map.stamp(Brush.BRICK_FULL, 8, 24)
        val level = map.toLevel("Broken", "B*20")

        val messages = EditorMessages.friendly(LevelValidator.validate(level).errors)

        assertTrue("ENEMY SPAWN 1 BLOCKED" in messages)
        assertTrue("PLAYER 1 SPAWN BLOCKED" in messages)
    }

    @Test
    fun `a wall across the field reports no path`() {
        val map = EditorMap.template()
        for (x in 0 until EditorMap.SIZE) {
            map.stamp(Brush.STEEL_TALL, x, 12)
        }
        val level = map.toLevel("Walled", "B*20")

        val messages = EditorMessages.friendly(LevelValidator.validate(level).errors)

        assertTrue("NO PATH TO THE EAGLE" in messages)
    }

    @Test
    fun `a blocked eagle area is reported`() {
        val map = EditorMap.template()
        map.stamp(Brush.STEEL_FULL, 12, 24)
        val level = map.toLevel("Eagle", "B*20")

        val messages = EditorMessages.friendly(LevelValidator.validate(level).errors)

        assertTrue("EAGLE AREA BLOCKED" in messages)
    }

    @Test
    fun `a short roster says the total must be twenty`() {
        val level = EditorMap.template().toLevel("Few", "B*19")

        val messages = EditorMessages.friendly(LevelValidator.validate(level).errors)

        assertEquals(listOf("ENEMY ROSTER MUST TOTAL 20"), messages)
    }

    @Test
    fun `messages only use glyphs the font can draw`() {
        val map = EditorMap.template()
        map.stamp(Brush.BRICK_FULL, 0, 0)
        val level = map.toLevel("Broken", "B*19")
        val safe = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 :./-!?+"

        val messages = EditorMessages.friendly(LevelValidator.validate(level).errors)

        assertFalse(messages.isEmpty())
        for (message in messages) {
            assertTrue(message.all { it in safe }, message)
        }
    }

    @Test
    fun `long words are split at the line limit`() {
        val lines = EditorMessages.wrap("A VERYLONGWORDTHATNEEDSSPLITTING OK", 10)

        assertTrue(lines.all { it.length <= 10 }, lines.toString())
        assertEquals("A", lines[0])
        assertEquals("VERYLONGWO", lines[1])
        assertTrue(lines.last().endsWith("OK"))
    }

    @Test
    fun `host errors are stripped to glyphs the font can draw`() {
        val safe = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 :./-!?+"

        val message = EditorMessages.sanitize("java.io.FileNotFoundException: /x (y)")

        assertTrue(message.all { it in safe }, message)
        assertTrue(message.contains("FILE"))
    }

    @Test
    fun `an empty error list still shows something`() {
        assertEquals(listOf("UNKNOWN ERROR"), EditorMessages.friendly(emptyList()))
    }
}
