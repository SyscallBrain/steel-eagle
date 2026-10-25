package pt.tiago.steeleagle.input

import pt.tiago.steeleagle.core.model.Dir
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TouchDirectionTest {

    @Test
    fun `inside the deadzone there is no direction`() {
        assertNull(TouchDirection.quantize(5, -5, deadzone = 10, current = Dir.UP))
    }

    @Test
    fun `without a held direction the dominant axis wins`() {
        assertEquals(Dir.RIGHT, TouchDirection.quantize(30, 20, 10, null))
        assertEquals(Dir.UP, TouchDirection.quantize(20, -30, 10, null))
    }

    @Test
    fun `the held axis is kept until the other is clearly stronger`() {
        assertEquals(Dir.RIGHT, TouchDirection.quantize(40, 48, 10, Dir.RIGHT))
        assertEquals(Dir.DOWN, TouchDirection.quantize(40, 52, 10, Dir.RIGHT))
        assertEquals(Dir.LEFT, TouchDirection.quantize(-40, 48, 10, Dir.RIGHT))
        assertEquals(Dir.UP, TouchDirection.quantize(45, -40, 10, Dir.UP))
    }
}
