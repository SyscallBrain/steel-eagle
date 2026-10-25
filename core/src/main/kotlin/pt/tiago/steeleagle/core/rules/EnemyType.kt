package pt.tiago.steeleagle.core.rules

import pt.tiago.steeleagle.core.model.Fixed
import kotlin.math.roundToInt

/**
 * Enemy stats. Speeds are in 1/16 px per tick; fire chances are
 * per mille per tick.
 */
enum class EnemyType(
    val speed: Int,
    val bulletSpeed: Int,
    val hitPoints: Int,
    val points: Int,
    val fireChancePerMille: Int,
    val alignedFireChancePerMille: Int,
) {
    BASIC(
        speed = 8,
        bulletSpeed = Fixed.BULLET_SPEED,
        hitPoints = 1,
        points = 100,
        fireChancePerMille = 6,
        alignedFireChancePerMille = 25,
    ),
    FAST(
        speed = 16,
        bulletSpeed = Fixed.BULLET_SPEED,
        hitPoints = 1,
        points = 200,
        fireChancePerMille = 8,
        alignedFireChancePerMille = 30,
    ),
    POWER(
        speed = 12,
        bulletSpeed = 96,
        hitPoints = 1,
        points = 300,
        fireChancePerMille = 10,
        alignedFireChancePerMille = 35,
    ),
    ARMORED(
        speed = 12,
        bulletSpeed = Fixed.BULLET_SPEED,
        hitPoints = 4,
        points = 400,
        fireChancePerMille = 6,
        alignedFireChancePerMille = 25,
    ),
    ;

    fun speedFor(difficulty: Difficulty): Int =
        (speed * difficulty.enemySpeedMultiplier).roundToInt().coerceAtLeast(1)
}
