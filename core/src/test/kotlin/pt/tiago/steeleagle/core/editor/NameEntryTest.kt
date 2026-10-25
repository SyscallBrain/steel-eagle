package pt.tiago.steeleagle.core.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NameEntryTest {

    @Test
    fun `letters and digits are appended and deleted`() {
        val entry = NameEntry()

        entry.select() // A
        entry.move(1, 0)
        entry.select() // B
        entry.move(-1, 0)
        entry.move(0, 3) // row 3, column 0: 4
        entry.select()
        assertEquals("AB4", entry.text)

        entry.move(5, 0) // 9
        entry.select()
        assertEquals("AB49", entry.text)

        entry.move(1, 0) // SPACE
        assertEquals("SPACE", entry.cells[entry.cursor])
        entry.select()
        assertEquals("AB49 ", entry.text)

        entry.move(1, 0) // DEL
        assertEquals(NameEntry.Action.DELETE, entry.select())
        assertEquals("AB49", entry.text)
        assertEquals(NameEntry.Action.DELETE, entry.select())
        assertEquals("AB4", entry.text)
    }

    @Test
    fun `the cursor stays inside the keyboard`() {
        val entry = NameEntry()

        entry.move(-1, 0)
        assertEquals(0, entry.cursor)
        entry.move(0, -1)
        assertEquals(0, entry.cursor)

        repeat(40) { entry.move(1, 0) }
        assertEquals(9, entry.cursor)
        repeat(5) { entry.move(0, 1) }
        assertEquals(38, entry.cursor)
        entry.move(0, 1)
        assertEquals(38, entry.cursor)

        entry.move(0, -1)
        assertEquals(28, entry.cursor)
        entry.move(1, 0)
        assertEquals(29, entry.cursor)
    }

    @Test
    fun `names stop at twelve characters`() {
        val entry = NameEntry("ABCDEFGHIJKL")

        assertEquals("ABCDEFGHIJKL", entry.text)

        entry.select() // A at the cursor
        assertEquals(12, entry.text.length)
    }

    @Test
    fun `OK confirms and blank names return null`() {
        val entry = NameEntry("  ")

        entry.move(8, 3)
        assertEquals("OK", entry.cells[entry.cursor])
        assertEquals(NameEntry.Action.CONFIRM, entry.select())
        assertEquals(null, entry.result())

        entry.setText("  Steel  ")
        assertEquals("STEEL", entry.result())
    }
}
