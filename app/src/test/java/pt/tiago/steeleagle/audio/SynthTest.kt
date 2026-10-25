package pt.tiago.steeleagle.audio

import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Test

class SynthTest {

    private val sampleRate = 22_050

    @Test
    fun `silent synth renders zeros`() {
        val synth = Synth(sampleRate)
        val buffer = ShortArray(512)

        synth.render(buffer, buffer.size, sfxVolume = 1f, musicVolume = 1f)

        assertTrue(buffer.all { it.toInt() == 0 })
    }

    @Test
    fun `sound effect renders non zero samples`() {
        val synth = Synth(sampleRate)
        val buffer = ShortArray(512)
        synth.playSfx(Sfx.SHOT)

        synth.render(buffer, buffer.size, sfxVolume = 1f, musicVolume = 1f)

        assertTrue(buffer.any { it.toInt() != 0 })
    }

    @Test
    fun `music renders non zero samples`() {
        val synth = Synth(sampleRate)
        val buffer = ShortArray(512)
        synth.playMusic(
            MusicSequence.parse(
                """
                tempo 120
                square1 C4 4
                triangle C3 8
                """.trimIndent(),
            ),
            loop = false,
        )

        synth.render(buffer, buffer.size, sfxVolume = 1f, musicVolume = 1f)

        assertTrue(buffer.any { it.toInt() != 0 })
    }

    @Test
    fun `engine renders non zero samples`() {
        val synth = Synth(sampleRate)
        val buffer = ShortArray(512)
        synth.setEngine(active = true, moving = true)

        synth.render(buffer, buffer.size, sfxVolume = 1f, musicVolume = 1f)

        assertTrue(buffer.any { it.toInt() != 0 })
    }

    @Test
    fun `zero volume mutes effects and engine`() {
        val synth = Synth(sampleRate)
        val buffer = ShortArray(512)
        synth.playSfx(Sfx.SHOT)
        synth.setEngine(active = true, moving = true)

        synth.render(buffer, buffer.size, sfxVolume = 0f, musicVolume = 0f)

        assertTrue(buffer.all { it.toInt() == 0 })
    }

    @Test
    fun `zero sfx volume keeps the music on the shared voice`() {
        val synth = Synth(sampleRate)
        synth.playMusic(
            MusicSequence.parse("tempo 120\nsquare1 C4 8"),
            loop = true,
        )
        synth.playSfx(Sfx.SHOT)
        val buffer = ShortArray(512)

        synth.render(buffer, buffer.size, sfxVolume = 0f, musicVolume = 1f)

        assertTrue(buffer.any { it.toInt() != 0 })
    }

    @Test
    fun `source transitions are smoothed`() {
        val synth = Synth(sampleRate)
        synth.playMusic(
            MusicSequence.parse("tempo 120\nsquare1 C4 8"),
            loop = true,
        )
        val warmup = ShortArray(512)
        synth.render(warmup, warmup.size, sfxVolume = 1f, musicVolume = 1f)

        synth.playSfx(Sfx.SHOT)
        val transition = ShortArray(512)
        synth.render(transition, transition.size, sfxVolume = 1f, musicVolume = 1f)

        var maxDelta = abs(warmup.last().toInt() - transition.first().toInt())
        for (index in 1 until transition.size) {
            maxDelta = maxOf(
                maxDelta,
                abs(transition[index].toInt() - transition[index - 1].toInt()),
            )
        }

        assertTrue("max delta $maxDelta", maxDelta < 2_000)
    }
}
