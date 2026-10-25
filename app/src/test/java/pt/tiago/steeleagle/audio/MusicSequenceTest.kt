package pt.tiago.steeleagle.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MusicSequenceTest {

    @Test
    fun `note frequencies follow equal temperament`() {
        assertEquals(440, NoteFrequency.of("A4"))
        assertEquals(262, NoteFrequency.of("C4"))
        assertEquals(523, NoteFrequency.of("C5"))
        assertEquals(0, NoteFrequency.of("R"))
    }

    @Test
    fun `parses tempo channels notes and rests`() {
        val sequence = MusicSequence.parse(
            """
            # sample
            tempo 120
            square1 C4 4 R 4
            triangle C3 8
            """.trimIndent(),
        )

        assertEquals(120, sequence.tempo)
        assertEquals(2, sequence.channels[MusicChannel.SQUARE1]?.size)
        assertEquals(262, sequence.channels[MusicChannel.SQUARE1]?.first()?.frequency)
        assertEquals(0, sequence.channels[MusicChannel.SQUARE1]?.get(1)?.frequency)
        assertEquals(131, sequence.channels[MusicChannel.TRIANGLE]?.first()?.frequency)
    }

    @Test
    fun `rejects an unknown channel`() {
        assertThrows(IllegalArgumentException::class.java) {
            MusicSequence.parse("noise C4 4")
        }
    }

    @Test
    fun `rejects an odd number of tokens`() {
        assertThrows(IllegalArgumentException::class.java) {
            MusicSequence.parse("square1 C4")
        }
    }

    @Test
    fun `rejects a bad note name`() {
        assertThrows(IllegalArgumentException::class.java) {
            MusicSequence.parse("square1 H4 4")
        }
    }

    @Test
    fun `rejects a non positive duration`() {
        assertThrows(IllegalArgumentException::class.java) {
            MusicSequence.parse("square1 C4 0")
        }
    }
}
