package pt.tiago.steeleagle.core.world

import pt.tiago.steeleagle.core.rules.EnemyType

/** An enemy materialising at a spawn point. */
data class SpawnAnimation(
    val type: EnemyType,
    val flashing: Boolean,
    val blockX: Int,
    val blockY: Int,
    var ticksRemaining: Int,
)
