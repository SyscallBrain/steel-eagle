package pt.tiago.steeleagle.storage

import pt.tiago.steeleagle.core.rules.Difficulty

data class HighScoreEntry(
    val initials: String,
    val score: Int,
) {
    init {
        require(initials.length == 3) { "initials must be three letters" }
        require(score >= 0) { "score must be non-negative" }
    }

    val display: String
        get() = initials.uppercase()
}

/** Top ten per difficulty, persisted as a compact text table. */
class HighScores(
    initial: Map<Difficulty, List<HighScoreEntry>> = emptyMap(),
) {
    private val tables: MutableMap<Difficulty, MutableList<HighScoreEntry>> =
        Difficulty.entries.associateWith { difficulty ->
            initial[difficulty].orEmpty()
                .sortedByDescending { it.score }
                .take(MAX)
                .toMutableList()
        }.toMutableMap()

    fun entries(difficulty: Difficulty): List<HighScoreEntry> =
        tables.getValue(difficulty).toList()

    fun qualifies(difficulty: Difficulty, score: Int): Boolean {
        if (score <= 0) return false
        val table = tables.getValue(difficulty)
        return table.size < MAX || score > table.last().score
    }

    fun add(difficulty: Difficulty, initials: String, score: Int): Boolean {
        if (!qualifies(difficulty, score)) return false
        val clean = initials.uppercase().padEnd(3, 'A').take(3)
        val table = tables.getValue(difficulty)
        table += HighScoreEntry(clean, score)
        table.sortByDescending { it.score }
        while (table.size > MAX) table.removeAt(table.lastIndex)
        return true
    }

    fun bestScore(): Int =
        tables.values.flatten().maxOfOrNull { it.score } ?: 0

    fun serialize(): String = buildString {
        Difficulty.entries.forEachIndexed { index, difficulty ->
            if (index > 0) append('|')
            append(difficulty.name).append(':')
            tables.getValue(difficulty).forEachIndexed { entryIndex, entry ->
                if (entryIndex > 0) append(';')
                append(entry.display).append(',').append(entry.score)
            }
        }
    }

    companion object {
        const val MAX = 10

        fun parse(text: String): HighScores {
            val result = mutableMapOf<Difficulty, List<HighScoreEntry>>()
            if (text.isBlank()) return HighScores(result)

            for (part in text.split('|')) {
                val colon = part.indexOf(':')
                if (colon <= 0) continue
                val difficulty = runCatching {
                    Difficulty.valueOf(part.substring(0, colon))
                }.getOrNull() ?: continue

                val entries = mutableListOf<HighScoreEntry>()
                for (item in part.substring(colon + 1).split(';')) {
                    val comma = item.indexOf(',')
                    if (comma <= 0) continue
                    val initials = item.substring(0, comma).trim().uppercase()
                    val score = item.substring(comma + 1).trim().toIntOrNull() ?: continue
                    if (initials.length == 3 && score >= 0) {
                        entries += HighScoreEntry(initials, score)
                    }
                }
                result[difficulty] = entries
            }
            return HighScores(result)
        }
    }
}
