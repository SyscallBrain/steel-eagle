package pt.tiago.steeleagle.input

import pt.tiago.steeleagle.core.model.Dir
import pt.tiago.steeleagle.core.model.PlayerInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CombinedInputTest {

    private class FixedSource(private val input: PlayerInput) : InputSource {
        override fun sample(): PlayerInput = input
    }

    @Test
    fun `first source with a direction wins`() {
        val combined = CombinedInput(
            listOf(
                FixedSource(PlayerInput.NONE),
                FixedSource(PlayerInput(Dir.LEFT, fire = false)),
                FixedSource(PlayerInput(Dir.RIGHT, fire = false)),
            ),
        )

        assertEquals(Dir.LEFT, combined.sample().direction)
    }

    @Test
    fun `fire is true when any source fires`() {
        val combined = CombinedInput(
            listOf(
                FixedSource(PlayerInput.NONE),
                FixedSource(PlayerInput(null, fire = true)),
            ),
        )

        assertTrue(combined.sample().fire)
    }

    @Test
    fun `no input stays idle`() {
        val combined = CombinedInput(listOf(FixedSource(PlayerInput.NONE)))

        assertEquals(PlayerInput.NONE, combined.sample())
        assertFalse(combined.sample().fire)
    }
}
