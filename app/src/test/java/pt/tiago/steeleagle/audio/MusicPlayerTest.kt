package pt.tiago.steeleagle.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicPlayerTest {

    private val sequence = MusicSequence.parse(
        """
        tempo 120
        square1 C4 4
        """.trimIndent(),
    )

    @Test
    fun `advances a quarter note for the expected samples`() {
        val player = MusicPlayer(sampleRate = 1_000)

        player.play(sequence, loop = false)

        assertTrue(player.isPlaying)
        assertEquals(262, player.frequency(MusicChannel.SQUARE1))
        assertEquals(500, player.noteRemaining(MusicChannel.SQUARE1))

        repeat(499) { player.advance() }
        assertEquals(262, player.frequency(MusicChannel.SQUARE1))

        player.advance()

        assertFalse(player.isPlaying)
        assertEquals(0, player.frequency(MusicChannel.SQUARE1))
    }

    @Test
    fun `loops the sequence when requested`() {
        val player = MusicPlayer(sampleRate = 1_000)
        player.play(sequence, loop = true)

        repeat(500) { player.advance() }

        assertTrue(player.isPlaying)
        assertEquals(262, player.frequency(MusicChannel.SQUARE1))
    }

    @Test
    fun `stop silences the channels`() {
        val player = MusicPlayer(sampleRate = 1_000)
        player.play(sequence, loop = true)

        player.stop()

        assertFalse(player.isPlaying)
        assertEquals(0, player.frequency(MusicChannel.SQUARE1))
        assertEquals(0f, player.volume(MusicChannel.SQUARE1))
    }
}
