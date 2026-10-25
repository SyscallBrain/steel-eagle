package pt.tiago.steeleagle.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RngTest {

    @Test
    fun `same seed produces the same sequence`() {
        val first = Rng(1234)
        val second = Rng(1234)

        repeat(16) {
            assertEquals(first.nextInt(), second.nextInt())
        }
    }

    @Test
    fun `different seeds diverge`() {
        val first = Rng(1)
        val second = Rng(2)

        val firstValues = List(4) { first.nextInt() }
        val secondValues = List(4) { second.nextInt() }

        assertNotEquals(firstValues, secondValues)
    }

    @Test
    fun `bounded values stay in range`() {
        val rng = Rng(42)

        repeat(200) {
            val value = rng.nextInt(10)
            assertTrue(value in 0 until 10)
        }
    }

    @Test
    fun `zero seed is usable`() {
        val first = Rng(0)
        val second = Rng(0)

        assertEquals(first.nextInt(), second.nextInt())
    }
}
