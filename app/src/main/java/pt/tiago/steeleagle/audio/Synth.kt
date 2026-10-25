package pt.tiago.steeleagle.audio

import kotlin.math.abs
import kotlin.math.min

/**
 * Four-voice NES-style synth: two squares, one triangle and one noise channel.
 * Music, effects and the engine share the voices additively. A short output
 * smoothing removes the hard steps that would otherwise click when a voice
 * changes source.
 */
class Synth(private val sampleRate: Int = 22_050) {

    private val voices = Array(SfxVoice.entries.size) { Voice() }
    private val sfx = Array(SfxVoice.entries.size) { mutableListOf<SfxInstance>() }
    private val music = MusicPlayer(sampleRate)
    private val musicChannels = arrayOf(
        MusicChannel.SQUARE1,
        MusicChannel.SQUARE2,
        MusicChannel.TRIANGLE,
        null,
    )

    private var engineActive = false
    private var engineMoving = false
    private var lastSample = 0f

    fun playSfx(effect: SfxEffect, volumeScale: Float = 1f) {
        val total = (effect.durationMs * sampleRate / 1000).coerceAtLeast(1)
        val effects = sfx[effect.voice.ordinal]
        if (effects.size >= MAX_EFFECTS_PER_VOICE) effects.removeAt(0)
        effects += SfxInstance(effect, volumeScale, total)
    }

    fun playMusic(sequence: MusicSequence, loop: Boolean) {
        music.play(sequence, loop)
    }

    fun stopMusic() {
        music.stop()
    }

    fun setEngine(active: Boolean, moving: Boolean) {
        engineActive = active
        engineMoving = moving
    }

    fun reset() {
        sfx.forEach { it.clear() }
        music.stop()
        engineActive = false
        engineMoving = false
        lastSample = 0f
        voices.forEach { it.reset() }
    }

    fun render(buffer: ShortArray, count: Int, sfxVolume: Float, musicVolume: Float) {
        for (index in 0 until count) {
            music.advance()
            var mix = 0f

            for (voiceIndex in voices.indices) {
                val voice = voices[voiceIndex]
                val effects = sfx[voiceIndex]

                if (effects.isNotEmpty()) {
                    val iterator = effects.iterator()
                    while (iterator.hasNext()) {
                        val instance = iterator.next()
                        mix += renderSfx(voice, instance) *
                            instance.effect.volume * instance.volumeScale * sfxVolume
                        instance.elapsed++
                        if (instance.elapsed >= instance.totalSamples) {
                            iterator.remove()
                        }
                    }
                }

                val channel = musicChannels[voiceIndex]
                val musicActive = channel != null &&
                    music.frequency(channel) > 0 &&
                    music.volume(channel) > 0f
                if (channel != null && musicActive) {
                    mix += renderTone(
                        voice,
                        music.frequency(channel).toFloat(),
                        waveformFor(channel),
                    ) * music.volume(channel) * musicEnvelope(channel) * musicVolume
                }

                if (voiceIndex == SfxVoice.TRIANGLE.ordinal &&
                    engineActive &&
                    effects.isEmpty() &&
                    !musicActive
                ) {
                    val frequency = if (engineMoving) 110f else 82f
                    mix += renderTone(voice, frequency, Waveform.TRIANGLE) *
                        ENGINE_VOLUME * sfxVolume
                }
            }

            lastSample += (mix.coerceIn(-1f, 1f) - lastSample) * SMOOTHING
            buffer[index] = (lastSample * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun renderSfx(voice: Voice, instance: SfxInstance): Float {
        val progress = instance.elapsed.toFloat() / instance.totalSamples
        val frequency = instance.effect.startFrequency +
            (instance.effect.endFrequency - instance.effect.startFrequency) * progress
        val gain = sfxEnvelope(progress)
        return renderTone(voice, frequency, instance.effect.waveform) * gain
    }

    private fun renderTone(voice: Voice, frequency: Float, waveform: Waveform): Float {
        if (frequency <= 0f) return 0f

        voice.phase += frequency / sampleRate
        if (voice.phase >= 1.0) voice.phase -= 1.0

        return when (waveform) {
            Waveform.SQUARE_12 -> if (voice.phase < 0.125) 1f else -1f
            Waveform.SQUARE_25 -> if (voice.phase < 0.25) 1f else -1f
            Waveform.SQUARE_50 -> if (voice.phase < 0.5) 1f else -1f
            Waveform.TRIANGLE -> 1f - 4f * abs(voice.phase.toFloat() - 0.5f)
            Waveform.NOISE -> renderNoise(voice, frequency)
        }
    }

    private fun renderNoise(voice: Voice, frequency: Float): Float {
        voice.noisePhase += frequency / sampleRate
        if (voice.noisePhase >= 1.0) {
            voice.noisePhase -= 1.0
            val bit = (voice.noiseRegister xor (voice.noiseRegister shr 1)) and 1
            voice.noiseRegister = (voice.noiseRegister shr 1) or (bit shl 14)
            voice.noiseValue = if (voice.noiseRegister and 1 == 1) 1f else -1f
        }
        return voice.noiseValue
    }

    private fun sfxEnvelope(progress: Float): Float {
        val attack = min(1f, progress / 0.02f)
        return attack * (1f - progress).coerceAtLeast(0f)
    }

    private fun musicEnvelope(channel: MusicChannel): Float {
        val total = music.noteTotal(channel).toFloat()
        if (total <= 0f) return 0f
        val elapsed = total - music.noteRemaining(channel)
        val attack = min(1f, elapsed / (sampleRate * 0.005f))
        val release = min(1f, music.noteRemaining(channel) / (sampleRate * 0.02f))
        return min(attack, release)
    }

    private fun waveformFor(channel: MusicChannel): Waveform = when (channel) {
        MusicChannel.SQUARE1 -> Waveform.SQUARE_50
        MusicChannel.SQUARE2 -> Waveform.SQUARE_25
        MusicChannel.TRIANGLE -> Waveform.TRIANGLE
    }

    private class Voice {
        var phase = 0.0
        var noisePhase = 0.0
        var noiseRegister = 0x7FFF
        var noiseValue = 1f

        fun reset() {
            phase = 0.0
            noisePhase = 0.0
            noiseRegister = 0x7FFF
            noiseValue = 1f
        }
    }

    private class SfxInstance(
        val effect: SfxEffect,
        val volumeScale: Float,
        val totalSamples: Int,
        var elapsed: Int = 0,
    )

    private companion object {
        const val ENGINE_VOLUME = 0.06f
        const val SMOOTHING = 0.05f
        const val MAX_EFFECTS_PER_VOICE = 4
    }
}
