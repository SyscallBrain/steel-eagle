package pt.tiago.steeleagle.audio

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicAssetsTest {

    private val musicDir = File("src/main/assets/music")

    @Test
    fun `all music assets parse with notes and a tempo`() {
        assertTrue("missing $musicDir", musicDir.isDirectory)
        val files = musicDir.listFiles { file -> file.extension == "txt" }.orEmpty()
        assertEquals(4, files.size)

        for (file in files) {
            val sequence = MusicSequence.parse(file.readText())
            assertTrue("${file.name} tempo", sequence.tempo in 30..300)
            assertTrue("${file.name} channels", sequence.channels.isNotEmpty())
            assertTrue(
                "${file.name} notes",
                sequence.channels.values.any { notes -> notes.any { it.frequency > 0 } },
            )
        }
    }

    @Test
    fun `title theme has three channels`() {
        val sequence = MusicSequence.parse(File(musicDir, "title.txt").readText())

        assertEquals(3, sequence.channels.size)
        assertTrue(sequence.channels.containsKey(MusicChannel.SQUARE1))
        assertTrue(sequence.channels.containsKey(MusicChannel.SQUARE2))
        assertTrue(sequence.channels.containsKey(MusicChannel.TRIANGLE))
    }
}
