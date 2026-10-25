package pt.tiago.steeleagle.render

import pt.tiago.steeleagle.core.rules.EnemyType

/** Tints applied to the white enemy tank sprite. */
object EnemyColors {
    fun tint(type: EnemyType?, hp: Int): Int = when (type) {
        EnemyType.BASIC -> 0xFFB8B8B8.toInt()
        EnemyType.FAST -> 0xFFF0F0F0.toInt()
        EnemyType.POWER -> 0xFF58D854.toInt()
        EnemyType.ARMORED -> when {
            hp >= 4 -> 0xFF58D854.toInt()
            hp == 3 -> 0xFFFCD800.toInt()
            hp == 2 -> 0xFFE09000.toInt()
            else -> 0xFFD82800.toInt()
        }
        null -> 0xFFB8B8B8.toInt()
    }
}
