package pt.tiago.steeleagle.flow

import pt.tiago.steeleagle.core.rules.EnemyType

data class PlayerTally2P(
    val rows: List<TallyRow>,
    val other: Int,
    val levelScore: Int,
    val bonus: Int,
) {
    val total: Int
        get() = levelScore + bonus

    val totalKills: Int
        get() = rows.sumOf { it.kills }
}

/** Two-column level tally, including the 1000-kill bonus. */
data class TallySummary2P(
    val players: List<PlayerTally2P>,
    val winner: Int?,
) {
    init {
        require(players.size == 2) { "two-player tally needs two columns" }
    }

    companion object {
        const val BONUS = 1_000

        fun from(
            killsP1: IntArray,
            scoreP1: IntArray,
            killsP2: IntArray,
            scoreP2: IntArray,
            levelScoreP1: Int,
            levelScoreP2: Int,
        ): TallySummary2P {
            val p1 = column(killsP1, scoreP1, levelScoreP1)
            val p2 = column(killsP2, scoreP2, levelScoreP2)
            val winner = when {
                p1.totalKills > p2.totalKills -> 0
                p2.totalKills > p1.totalKills -> 1
                else -> null
            }
            return TallySummary2P(
                players = listOf(
                    p1.copy(bonus = if (winner == 0) BONUS else 0),
                    p2.copy(bonus = if (winner == 1) BONUS else 0),
                ),
                winner = winner,
            )
        }

        private fun column(
            kills: IntArray,
            scoreByType: IntArray,
            levelScore: Int,
        ): PlayerTally2P {
            val rows = EnemyType.entries.map { type ->
                TallyRow(
                    type = type,
                    kills = kills.getOrElse(type.ordinal) { 0 },
                    subtotal = scoreByType.getOrElse(type.ordinal) { 0 },
                )
            }
            val cleanScore = levelScore.coerceAtLeast(0)
            return PlayerTally2P(
                rows = rows,
                other = (cleanScore - rows.sumOf { it.subtotal }).coerceAtLeast(0),
                levelScore = cleanScore,
                bonus = 0,
            )
        }
    }
}
