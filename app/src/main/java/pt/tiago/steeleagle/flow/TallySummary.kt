package pt.tiago.steeleagle.flow

import pt.tiago.steeleagle.core.rules.EnemyType

data class TallyRow(
    val type: EnemyType,
    val kills: Int,
    val subtotal: Int,
) {
    val points: Int = type.points
}

/** Per-level score breakdown: kills by type, power-up points and total. */
data class TallySummary(
    val rows: List<TallyRow>,
    val bonus: Int,
    val total: Int,
) {
    companion object {
        fun from(kills: IntArray, scoreByType: IntArray, levelScore: Int): TallySummary {
            val rows = EnemyType.entries.map { type ->
                TallyRow(
                    type = type,
                    kills = kills.getOrElse(type.ordinal) { 0 },
                    subtotal = scoreByType.getOrElse(type.ordinal) { 0 },
                )
            }
            val killPoints = rows.sumOf { it.subtotal }
            val cleanScore = levelScore.coerceAtLeast(0)
            return TallySummary(
                rows = rows,
                bonus = (cleanScore - killPoints).coerceAtLeast(0),
                total = cleanScore,
            )
        }
    }
}
