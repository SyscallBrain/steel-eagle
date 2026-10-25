package pt.tiago.steeleagle.flow

import org.junit.Assert.assertEquals
import org.junit.Test

class MenuTest {

    private val menu = Menu(listOf("ONE", "TWO", "THREE"))

    @Test
    fun `moves with wrap-around`() {
        assertEquals("ONE", menu.current)
        menu.move(-1)
        assertEquals("THREE", menu.current)
        menu.move(1)
        assertEquals("ONE", menu.current)
        menu.move(1)
        assertEquals("TWO", menu.current)
    }

    @Test
    fun `selects an explicit item`() {
        menu.select(2)
        assertEquals("THREE", menu.current)
    }
}
