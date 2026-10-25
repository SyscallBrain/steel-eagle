package pt.tiago.steeleagle.flow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IpEntryTest {

    @Test
    fun `an address can be typed with the on-screen keys`() {
        val entry = IpEntry()

        entry.setText("192.168.1.5")
        entry.move(3, 3) // OK

        assertEquals("OK", entry.cells[entry.cursor])
        assertEquals(IpEntry.Action.CONFIRM, entry.select())
        assertEquals("192.168.1.5", entry.result())
    }

    @Test
    fun `digits and dots are appended and deleted`() {
        val entry = IpEntry()
        entry.setText("")

        entry.select() // 1
        entry.move(1, 0)
        entry.select() // 2
        entry.move(0, 2) // row 2, column 1 -> 0
        entry.select()
        assertEquals("120", entry.text)

        entry.move(1, 0) // .
        assertEquals(".", entry.cells[entry.cursor])
        entry.select()
        assertEquals("120.", entry.text)

        entry.move(1, 0) // DEL
        assertEquals(IpEntry.Action.DELETE, entry.select())
        assertEquals("120", entry.text)
    }

    @Test
    fun `the cursor stays inside the keypad`() {
        val entry = IpEntry()

        entry.move(-1, 0)
        assertEquals(0, entry.cursor)
        entry.move(0, -1)
        assertEquals(0, entry.cursor)

        repeat(10) { entry.move(1, 0) }
        assertEquals(3, entry.cursor)

        entry.move(0, 3)
        assertEquals(12, entry.cursor)
        entry.move(1, 0)
        assertEquals(12, entry.cursor)
    }

    @Test
    fun `only valid IPv4 literals are accepted`() {
        assertNull(IpEntry("192.168.1").result())
        assertNull(IpEntry("192.168.1.256").result())
        assertNull(IpEntry("192.168.1.a").result())
        assertNull(IpEntry("").result())
        assertEquals("10.0.0.2", IpEntry("10.0.0.2").result())
    }

    @Test
    fun `the text is limited to fifteen characters`() {
        val entry = IpEntry()

        entry.setText("1234567890123456789012345")

        assertEquals(15, entry.text.length)
    }
}
