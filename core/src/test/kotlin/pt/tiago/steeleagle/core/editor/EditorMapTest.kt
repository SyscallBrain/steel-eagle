package pt.tiago.steeleagle.core.editor

import pt.tiago.steeleagle.core.level.LevelValidator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EditorMapTest {

    @Test
    fun `a new map starts from the eagle fort`() {
        val map = EditorMap.template()

        assertEquals(26, map.rows().size)
        assertTrue(map.rows().all { it.length == 26 })
        assertEquals('B', map.get(8, 20))
        assertEquals('B', map.get(11, 24))
        assertEquals('.', map.get(12, 24))
        assertEquals('.', map.get(13, 25))
        assertEquals('.', map.get(0, 0))
    }

    @Test
    fun `the template passes the gameplay validator`() {
        val level = EditorMap.template().toLevel("Template", EnemySetup.allBasic().spec())

        val validation = LevelValidator.validate(level)

        assertTrue(validation.isValid, validation.errors.joinToString())
        assertEquals("B*20", level.enemies)
    }

    @Test
    fun `a full brush stamps a two by two block`() {
        val map = EditorMap.template()

        val changed = map.stamp(Brush.BRICK_FULL, 2, 2)

        assertTrue(changed)
        for (y in 2..3) for (x in 2..3) assertEquals('B', map.get(x, y))
        assertEquals('.', map.get(4, 2))
        assertEquals('.', map.get(2, 4))
    }

    @Test
    fun `half and quarter brushes keep their shapes`() {
        val map = EditorMap.template()

        map.stamp(Brush.STEEL_WIDE, 2, 2)
        map.stamp(Brush.STEEL_TALL, 8, 8)
        map.stamp(Brush.STEEL_QUARTER, 12, 12)

        assertEquals('S', map.get(2, 2))
        assertEquals('S', map.get(3, 2))
        assertEquals('.', map.get(2, 3))
        assertEquals('S', map.get(8, 8))
        assertEquals('S', map.get(8, 9))
        assertEquals('.', map.get(9, 8))
        assertEquals('S', map.get(12, 12))
        assertEquals('.', map.get(13, 12))
        assertEquals('.', map.get(12, 13))
    }

    @Test
    fun `water trees ice and the eraser replace terrain`() {
        val map = EditorMap.template()

        map.stamp(Brush.WATER, 4, 4)
        map.stamp(Brush.TREES, 8, 4)
        map.stamp(Brush.ICE, 12, 4)
        assertEquals('W', map.get(5, 5))
        assertEquals('T', map.get(9, 5))
        assertEquals('I', map.get(13, 5))

        map.stamp(Brush.ERASER, 4, 4)
        map.stamp(Brush.ERASER, 8, 4)
        map.stamp(Brush.ERASER, 12, 4)
        assertEquals('.', map.get(5, 5))
        assertEquals('.', map.get(9, 5))
        assertEquals('.', map.get(13, 5))
    }

    @Test
    fun `brushes are clipped at the field border`() {
        val map = EditorMap.template()

        assertTrue(map.stamp(Brush.BRICK_FULL, 25, 25))
        assertEquals('B', map.get(25, 25))
        assertEquals(26, map.rows().size)
        assertTrue(map.rows().all { it.length == 26 })

        assertTrue(map.stamp(Brush.BRICK_FULL, -1, -1))
        assertEquals('B', map.get(0, 0))
    }

    @Test
    fun `stamping the same terrain twice reports no change`() {
        val map = EditorMap.template()

        assertTrue(map.stamp(Brush.BRICK_FULL, 2, 2))
        assertFalse(map.stamp(Brush.BRICK_FULL, 2, 2))
        assertTrue(map.stamp(Brush.ERASER, 2, 2))
    }

    @Test
    fun `a loaded level can be edited and copied`() {
        val original = EditorMap.template().toLevel("One", "B*20")
        val map = EditorMap.fromLevel(original)
        val copy = map.copy()

        map.stamp(Brush.WATER, 0, 0)

        assertEquals('W', map.get(0, 0))
        assertEquals('.', copy.get(0, 0))
        assertEquals(original.map, copy.rows())
    }
}
