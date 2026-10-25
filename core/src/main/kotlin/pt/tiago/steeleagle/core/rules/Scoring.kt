package pt.tiago.steeleagle.core.rules

/** Points and extra-life threshold. */
object Scoring {
    const val EXTRA_LIFE_STEP = 20_000
    const val POWER_UP = 500

    fun pointsFor(type: EnemyType): Int = type.points
}
