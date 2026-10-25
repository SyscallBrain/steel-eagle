package pt.tiago.steeleagle.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import pt.tiago.steeleagle.core.game.GameEvent
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Owns the AudioTrack and the synth thread. Commands from the render/UI threads
 * are queued; only this thread touches the Synth. Pause stops and releases the
 * track so no audio survives onPause.
 */
class AudioEngine(context: Context) {

    private val synth = Synth(SAMPLE_RATE)
    private val music = MusicAssets.load(context.applicationContext)
    private val haptics = Haptics(context.applicationContext)
    private val commands = ConcurrentLinkedQueue<AudioCommand>()

    @Volatile
    var sfxVolume: Float = 0.7f

    @Volatile
    var musicVolume: Float = 0.5f

    @Volatile
    var vibrationEnabled: Boolean = true

    @Volatile
    private var engineActive = false

    @Volatile
    private var engineMoving = false

    @Volatile
    private var running = false

    @Volatile
    private var track: AudioTrack? = null

    private var thread: Thread? = null

    fun start() {
        if (running || thread?.isAlive == true) return
        track?.release()
        track = null
        val newTrack = createTrack() ?: return
        track = newTrack
        running = true
        thread = Thread(::audioLoop, "SteelEagleAudio").apply { start() }
    }

    fun pause() {
        if (!running && track == null) return
        running = false
        val current = track
        current?.pause()
        current?.flush()
        val audioThread = thread
        thread = null
        if (audioThread != null && audioThread !== Thread.currentThread()) {
            try {
                audioThread.join()
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        current?.release()
        track = null
        commands.clear()
        engineActive = false
        engineMoving = false
        synth.reset()
    }

    fun release() {
        pause()
    }

    fun playLevelStart() = queueMusic(MusicId.LEVEL_START, loop = false)

    fun playTitle() = queueMusic(MusicId.TITLE, loop = true)

    fun stopMusic() {
        commands += AudioCommand.StopMusic
    }

    fun playPause() = queueSfx(Sfx.PAUSE)

    fun onEvent(event: GameEvent) {
        when (event) {
            is GameEvent.ShotFired ->
                queueSfx(Sfx.SHOT, if (event.isPlayer) 1f else 0.6f)
            is GameEvent.SteelHit -> queueSfx(Sfx.STEEL_HIT)
            is GameEvent.BrickBroken -> queueSfx(Sfx.BRICK_BREAK)
            is GameEvent.EnemyExploded -> queueSfx(Sfx.ENEMY_EXPLODE)
            is GameEvent.PlayerExploded -> {
                queueSfx(Sfx.PLAYER_EXPLODE)
                if (vibrationEnabled) haptics.playerDeath()
            }
            is GameEvent.EagleDestroyed -> {
                queueSfx(Sfx.PLAYER_EXPLODE)
                if (vibrationEnabled) haptics.playerDeath()
            }
            is GameEvent.PowerUpAppeared -> queueSfx(Sfx.POWER_UP_APPEAR)
            is GameEvent.PowerUpPicked -> {
                queueSfx(Sfx.POWER_UP_PICK)
                if (vibrationEnabled) haptics.powerUp()
            }
            GameEvent.ExtraLife -> queueSfx(Sfx.EXTRA_LIFE)
            GameEvent.LevelClear -> queueMusic(MusicId.TALLY, loop = false)
            GameEvent.GameOver -> queueMusic(MusicId.GAME_OVER, loop = false)
        }
    }

    fun setEngine(active: Boolean, moving: Boolean) {
        engineActive = active
        engineMoving = moving
    }

    private fun queueSfx(effect: SfxEffect, volumeScale: Float = 1f) {
        commands += AudioCommand.Sfx(effect, volumeScale)
    }

    private fun queueMusic(id: MusicId, loop: Boolean) {
        music[id]?.let { commands += AudioCommand.Music(it, loop) }
    }

    private fun audioLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        val buffer = ShortArray(BUFFER_SAMPLES)

        try {
            while (running) {
                while (true) {
                    val command = commands.poll() ?: break
                    when (command) {
                        is AudioCommand.Sfx -> synth.playSfx(command.effect, command.scale)
                        is AudioCommand.Music ->
                            synth.playMusic(command.sequence, command.loop)
                        AudioCommand.StopMusic -> synth.stopMusic()
                    }
                }

                synth.setEngine(engineActive, engineMoving)
                synth.render(buffer, buffer.size, sfxVolume, musicVolume)

                val current = track ?: break
                var offset = 0
                while (offset < buffer.size && running) {
                    val written = current.write(
                        buffer,
                        offset,
                        buffer.size - offset,
                        AudioTrack.WRITE_BLOCKING,
                    )
                    if (written <= 0) return
                    offset += written
                }
            }
        } catch (_: Exception) {
            // Audio is best-effort; a broken track must never crash the game.
        } finally {
            running = false
        }
    }

    private fun createTrack(): AudioTrack? = runCatching {
        val minimum = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val bufferBytes = maxOf(minimum, BUFFER_SAMPLES * 2 * 2)

        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(bufferBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
            .also { it.play() }
    }.getOrNull()

    private sealed interface AudioCommand {
        data class Sfx(
            val effect: SfxEffect,
            val scale: Float,
        ) : AudioCommand

        data class Music(
            val sequence: MusicSequence,
            val loop: Boolean,
        ) : AudioCommand

        data object StopMusic : AudioCommand
    }

    private companion object {
        const val SAMPLE_RATE = 22_050
        const val BUFFER_SAMPLES = 256
    }
}
