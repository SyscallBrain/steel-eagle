package pt.tiago.steeleagle.audio

import kotlin.math.pow
import kotlin.math.roundToInt

enum class MusicChannel {
    SQUARE1,
    SQUARE2,
    TRIANGLE,
}

/** Duration is in sixteenth notes; frequency 0 is a rest. */
data class MusicNote(val frequency: Int, val duration: Int)

data class MusicSequence(
    val tempo: Int,
    val channels: Map<MusicChannel, List<MusicNote>>,
) {
    companion object {
        private val WHITESPACE = Regex("\\s+")

        fun parse(text: String): MusicSequence {
            var tempo = 120
            val channels = mutableMapOf<MusicChannel, MutableList<MusicNote>>()

            text.lines().forEachIndexed { index, raw ->
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed

                val parts = line.split(WHITESPACE)
                if (parts[0].equals("tempo", ignoreCase = true)) {
                    require(parts.size == 2) { "line ${index + 1}: bad tempo line" }
                    tempo = parts[1].toIntOrNull()
                        ?: throw IllegalArgumentException("line ${index + 1}: bad tempo")
                    require(tempo in 30..300) { "line ${index + 1}: tempo out of range" }
                    return@forEachIndexed
                }

                val channel = when (parts[0].lowercase()) {
                    "square1" -> MusicChannel.SQUARE1
                    "square2" -> MusicChannel.SQUARE2
                    "triangle" -> MusicChannel.TRIANGLE
                    else -> throw IllegalArgumentException(
                        "line ${index + 1}: unknown channel '${parts[0]}'",
                    )
                }
                require(parts.size >= 3 && (parts.size - 1) % 2 == 0) {
                    "line ${index + 1}: expected note/duration pairs"
                }

                val notes = channels.getOrPut(channel) { mutableListOf() }
                var token = 1
                while (token < parts.size) {
                    val frequency = NoteFrequency.of(parts[token])
                    val duration = parts[token + 1].toIntOrNull()
                        ?: throw IllegalArgumentException("line ${index + 1}: bad duration")
                    require(duration > 0) { "line ${index + 1}: duration must be positive" }
                    notes += MusicNote(frequency, duration)
                    token += 2
                }
            }

            require(channels.isNotEmpty()) { "music has no notes" }
            return MusicSequence(tempo, channels)
        }
    }
}

object NoteFrequency {
    private val NOTE_PATTERN = Regex("^([A-Ga-g])(#?)([0-8])$")

    fun of(name: String): Int {
        if (name == "R" || name == "-") return 0
        val match = NOTE_PATTERN.matchEntire(name)
            ?: throw IllegalArgumentException("bad note '$name'")
        val semitone = when (match.groupValues[1].first().uppercaseChar()) {
            'C' -> 0
            'D' -> 2
            'E' -> 4
            'F' -> 5
            'G' -> 7
            'A' -> 9
            'B' -> 11
            else -> error("unreachable")
        } + if (match.groupValues[2] == "#") 1 else 0
        val octave = match.groupValues[3].toInt()
        val midi = 12 * (octave + 1) + semitone
        return (440.0 * 2.0.pow((midi - 69) / 12.0)).roundToInt()
    }
}
