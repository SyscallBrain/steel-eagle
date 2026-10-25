package pt.tiago.steeleagle.core.ai

import pt.tiago.steeleagle.core.model.Dir
import pt.tiago.steeleagle.core.model.Fixed
import pt.tiago.steeleagle.core.rules.Difficulty
import pt.tiago.steeleagle.core.world.Tank
import pt.tiago.steeleagle.core.world.World
import kotlin.math.abs

/**
 * Deterministic enemy AI. It moves in its current direction,
 * picks a new one when blocked or after a random interval, and fires with a
 * higher chance when aligned with the player or the eagle.
 */
class EnemyBrain(
    private val difficulty: Difficulty,
    private val cycle: Int,
) {
    fun update(world: World, enemy: Tank) {
        if (!enemy.alive) return

        if (enemy.aiTimer <= 0 || !world.canMove(enemy, enemy.dir)) {
            world.turnEnemy(enemy, chooseDirection(world, enemy))
            enemy.aiTimer = MIN_THINK_TICKS + world.rng.nextInt(THINK_TICK_RANGE)
        }

        world.moveEnemy(enemy)
        enemy.aiTimer--
        maybeFire(world, enemy)
    }

    private fun chooseDirection(world: World, enemy: Tank): Dir {
        val eagleWeight = (difficulty.eagleWeight + cycle * 4).coerceAtMost(60)
        val playerWeight = (20 + cycle * 3).coerceAtMost(35)
        val randomWeight = (100 - eagleWeight - playerWeight).coerceAtLeast(10)
        val roll = world.rng.nextInt(randomWeight + eagleWeight + playerWeight)

        val target = world.nearestPlayer(enemy)
        val preferred = when {
            roll < randomWeight -> Dir.entries[world.rng.nextInt(Dir.entries.size)]
            roll < randomWeight + eagleWeight ->
                directionTo(enemy, world.eagle.x, world.eagle.y)
            target != null -> directionTo(enemy, target.x, target.y)
            else -> directionTo(enemy, world.eagle.x, world.eagle.y)
        }

        if (world.canTurn(enemy, preferred)) return preferred

        for (direction in FALLBACK_DIRECTIONS) {
            if (world.canTurn(enemy, direction)) return direction
        }
        return preferred
    }

    private fun directionTo(enemy: Tank, targetX: Int, targetY: Int): Dir {
        val direction = Dir.fromVector(targetX - enemy.x, targetY - enemy.y)
        return direction ?: enemy.dir
    }

    private fun maybeFire(world: World, enemy: Tank) {
        if (world.bullets.any { it.owner === enemy }) return

        val type = enemy.type ?: return
        val aligned = world.players.any { player ->
            player.alive && isAligned(enemy, player.x, player.y)
        } || isAligned(enemy, world.eagle.x, world.eagle.y)
        val baseChance = if (aligned) {
            type.alignedFireChancePerMille
        } else {
            type.fireChancePerMille
        }
        val cycleBonus = 1.0f + cycle * 0.2f
        val chance = (baseChance * difficulty.fireChanceMultiplier * cycleBonus)
            .toInt()
            .coerceAtLeast(1)

        if (world.rng.nextInt(1000) < chance) {
            world.fire(enemy)
        }
    }

    private fun isAligned(enemy: Tank, targetX: Int, targetY: Int): Boolean {
        val sameColumn = abs(enemy.x - targetX) < ALIGN_TOLERANCE
        val sameRow = abs(enemy.y - targetY) < ALIGN_TOLERANCE
        return (sameColumn && enemy.dir.dy != 0) || (sameRow && enemy.dir.dx != 0)
    }

    private companion object {
        const val MIN_THINK_TICKS = 45
        const val THINK_TICK_RANGE = 90
        const val ALIGN_TOLERANCE = Fixed.SUBPIXELS * 8
        val FALLBACK_DIRECTIONS = listOf(Dir.DOWN, Dir.LEFT, Dir.RIGHT, Dir.UP)
    }
}
