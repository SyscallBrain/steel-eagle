package pt.tiago.steeleagle.core.world

import pt.tiago.steeleagle.core.model.Dir
import pt.tiago.steeleagle.core.rules.Difficulty
import pt.tiago.steeleagle.core.rules.EnemyType
import pt.tiago.steeleagle.core.rules.PowerUpType
import kotlin.math.abs
import kotlin.math.roundToInt

/** One tank as sent over the network. Positions are 1/16 px units. */
data class TankState(
    val x: Int,
    val y: Int,
    val dir: Dir,
    val trackFrame: Int,
    val weaponLevel: Int,
    val alive: Boolean,
    val shieldTicks: Int,
    val stunTicks: Int,
    val hasBoat: Boolean,
    val escapingWater: Boolean,
    val slideRemaining: Int,
    val wasMoving: Boolean,
    val bulletSpeed: Int,
    val type: EnemyType? = null,
    val hp: Int = 1,
    val flashing: Boolean = false,
)

/** One bullet; exactly one of the owner fields is set. */
data class BulletState(
    val x: Int,
    val y: Int,
    val dir: Dir,
    val ownerPlayer: Int?,
    val ownerEnemy: Int?,
    val weaponLevel: Int,
    val speed: Int,
)

data class PowerUpState(
    val type: PowerUpType,
    val x: Int,
    val y: Int,
    val ticksRemaining: Int,
)

/**
 * Complete visual and HUD state of a [World] at one tick. The network layer
 * sends it to the guest; terrain travels separately as full/delta cells so a
 * snapshot stays small.
 */
data class WorldSnapshot(
    val tick: Long,
    val state: WorldState,
    val levelNumber: Int,
    val difficulty: Difficulty,
    val playerCount: Int,
    val lives: List<Int>,
    val scores: List<Int>,
    val enemiesRemaining: Int,
    val totalEnemies: Int,
    val eagleAlive: Boolean,
    val players: List<TankState>,
    val enemies: List<TankState>,
    val bullets: List<BulletState>,
    val powerUp: PowerUpState?,
    val freezeTicks: Int,
    val shovelTicks: Int,
)

/** Blends the last two snapshots for the guest; teleports are not blended. */
fun interpolateSnapshots(
    previous: WorldSnapshot,
    next: WorldSnapshot,
    alpha: Float,
): WorldSnapshot {
    if (previous.levelNumber != next.levelNumber) return next
    val t = alpha.coerceIn(0f, 1f)
    return next.copy(
        players = interpolateTanks(previous.players, next.players, t),
        enemies = interpolateTanks(previous.enemies, next.enemies, t),
        bullets = interpolateBullets(previous.bullets, next.bullets, t),
    )
}

private fun interpolateTanks(
    previous: List<TankState>,
    next: List<TankState>,
    t: Float,
): List<TankState> {
    if (previous.size != next.size) return next
    return next.mapIndexed { index, state ->
        val before = previous[index]
        state.copy(
            x = blend(before.x, state.x, t, MAX_TANK_BLEND_STEP),
            y = blend(before.y, state.y, t, MAX_TANK_BLEND_STEP),
        )
    }
}

private fun interpolateBullets(
    previous: List<BulletState>,
    next: List<BulletState>,
    t: Float,
): List<BulletState> {
    if (previous.size != next.size) return next
    return next.mapIndexed { index, state ->
        val before = previous[index]
        state.copy(
            x = blend(before.x, state.x, t, MAX_BULLET_BLEND_STEP),
            y = blend(before.y, state.y, t, MAX_BULLET_BLEND_STEP),
        )
    }
}

private fun blend(from: Int, to: Int, t: Float, maxStep: Int): Int {
    val delta = to - from
    if (abs(delta) > maxStep) return to
    return (from + delta * t).roundToInt()
}

private const val MAX_TANK_BLEND_STEP = 64
private const val MAX_BULLET_BLEND_STEP = 256
