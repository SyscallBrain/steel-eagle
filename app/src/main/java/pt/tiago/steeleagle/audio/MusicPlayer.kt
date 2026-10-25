package pt.tiago.steeleagle.audio

/**
 * Sample-accurate sequencer for the three tonal channels. Pure Kotlin so the
 * note timing can be tested without AudioTrack.
 */
class MusicPlayer(private val sampleRate: Int) {
    private var sequence: MusicSequence? = null
    private var loop = false
    private var samplesPerSixteenth = 0.0
    private var playing = false

    private val noteIndices = IntArray(MusicChannel.entries.size)
    private val remaining = LongArray(MusicChannel.entries.size)
    private val total = LongArray(MusicChannel.entries.size)
    private val frequencies = IntArray(MusicChannel.entries.size)
    private val volumes = FloatArray(MusicChannel.entries.size)

    val isPlaying: Boolean
        get() = playing

    fun play(sequence: MusicSequence, loop: Boolean) {
        this.sequence = sequence
        this.loop = loop
        samplesPerSixteenth = sampleRate * 60.0 / sequence.tempo / 4.0
        resetChannels()
        playing = true
        for (index in MusicChannel.entries.indices) startNextNote(index)
        if (MusicChannel.entries.indices.all { remaining[it] == 0L }) playing = false
    }

    fun stop() {
        sequence = null
        playing = false
        resetChannels()
    }

    fun advance() {
        if (!playing) return

        var any = false
        for (index in MusicChannel.entries.indices) {
            if (remaining[index] > 0) remaining[index]--
            if (remaining[index] == 0L) startNextNote(index)
            if (remaining[index] > 0) any = true
        }

        if (!any) {
            if (loop) restart() else playing = false
        }
    }

    fun frequency(channel: MusicChannel): Int = frequencies[channel.ordinal]

    fun volume(channel: MusicChannel): Float = volumes[channel.ordinal]

    fun noteRemaining(channel: MusicChannel): Long = remaining[channel.ordinal]

    fun noteTotal(channel: MusicChannel): Long = total[channel.ordinal]

    private fun startNextNote(index: Int) {
        val current = sequence ?: return
        val channel = MusicChannel.entries[index]
        val notes = current.channels[channel].orEmpty()
        val noteIndex = noteIndices[index]

        if (noteIndex >= notes.size) {
            remaining[index] = 0
            total[index] = 0
            frequencies[index] = 0
            volumes[index] = 0f
            return
        }

        val note = notes[noteIndex]
        noteIndices[index]++
        val samples = (note.duration * samplesPerSixteenth).toLong().coerceAtLeast(1)
        remaining[index] = samples
        total[index] = samples
        frequencies[index] = note.frequency
        volumes[index] = if (note.frequency == 0) 0f else MUSIC_VOLUME
    }

    private fun restart() {
        resetChannels()
        playing = true
        for (index in MusicChannel.entries.indices) startNextNote(index)
        if (MusicChannel.entries.indices.all { remaining[it] == 0L }) playing = false
    }

    private fun resetChannels() {
        noteIndices.fill(0)
        remaining.fill(0)
        total.fill(0)
        frequencies.fill(0)
        volumes.fill(0f)
    }

    private companion object {
        const val MUSIC_VOLUME = 0.22f
    }
}
