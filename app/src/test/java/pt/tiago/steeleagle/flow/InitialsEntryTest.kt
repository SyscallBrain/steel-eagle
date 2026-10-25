package pt.tiago.steeleagle.flow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InitialsEntryTest {

    @Test
    fun `changes letters with wrap-around`() {
        val entry = InitialsEntry()

        entry.changeLetter(-1)
        assertEquals("ZAA", entry.text)
        entry.changeLetter(1)
        assertEquals("AAA", entry.text)
        entry.changeLetter(2)
        assertEquals("CAA", entry.text)
    }

    @Test
    fun `confirm advances and finishes on the last slot`() {
        val entry = InitialsEntry("XYZ")

        assertFalse(entry.confirm())
        assertEquals(1, entry.slot)
        assertFalse(entry.confirm())
        assertEquals(2, entry.slot)
        assertTrue(entry.confirm())
    }

    @Test
    fun `slot movement wraps and letter edits target the slot`() {
        val entry = InitialsEntry()

        entry.moveSlot(-1)
        assertEquals(2, entry.slot)
        entry.changeLetter(1)
        assertEquals("AAB", entry.text)
        entry.selectSlot(0)
        entry.changeLetter(1)
        assertEquals("BAB", entry.text)
    }
}
