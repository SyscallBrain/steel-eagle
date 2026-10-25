package pt.tiago.steeleagle.core.rules

import pt.tiago.steeleagle.core.world.SpawnAnimation
import pt.tiago.steeleagle.core.world.World

/**
 * Spawn pacing and rotation. The world drives [update] once per
 * tick; the pure static helpers are unit-tested directly.
 */
class SpawnDirector(
    internal val roster: List<EnemySpawn>,
    private val difficulty: Difficulty,
    level: Int,
    players: Int = 1,
) {
    val total: Int = roster.size
    val maxOnScreen: Int = maxOnScreen(players)
    val spawnInterval: Int = spawnInterval(level, players, difficulty)

    var defeated: Int = 0
        private set

    internal var spawned: Int = 0
    internal var rotation: Int = 0
    internal var nextSpawnTick: Long = spawnInterval.toLong()

    val remaining: Int
        get() = (total - defeated).coerceAtLeast(0)

    val spawnedCount: Int
        get() = spawned

    fun registerDefeat() {
        if (defeated < total) defeated++
    }

    fun update(world: World) {
        if (spawned >= total) return
        if (world.tick < nextSpawnTick) return

        val active = world.tanks.count { !it.isPlayer && it.alive } +
            world.spawnAnimations.size
        if (active >= maxOnScreen) return

        val (point, pointIndex) = nextFreePoint(world) ?: return
        val spawn = roster[spawned]
        world.spawnAnimations += SpawnAnimation(
            type = spawn.type,
            flashing = spawn.flashing,
            blockX = point.first,
            blockY = point.second,
            ticksRemaining = SPAWN_TICKS,
        )
        spawned++
        rotation = (pointIndex + 1) % SPAWN_POINTS.size
        nextSpawnTick = world.tick + spawnInterval
    }

    private fun nextFreePoint(world: World): Pair<Pair<Int, Int>, Int>? {
        for (offset in SPAWN_POINTS.indices) {
            val index = (rotation + offset) % SPAWN_POINTS.size
            val point = SPAWN_POINTS[index]
            if (world.isSpawnAreaFree(point.first, point.second)) {
                return point to index
            }
        }
        return null
    }

    companion object {
        const val SPAWN_TICKS = 60
        private val SPAWN_POINTS = listOf(6 to 0, 0 to 0, 12 to 0)

        fun spawnInterval(level: Int, players: Int, difficulty: Difficulty): Int {
            val base = maxOf(60, 190 - level * 4 - (players - 1) * 20)
            return (base * difficulty.spawnIntervalMultiplier).toInt()
        }

        fun maxOnScreen(players: Int): Int = if (players >= 2) 6 else 4

        fun spawnPoint(index: Int): Pair<Int, Int> =
            SPAWN_POINTS[index % SPAWN_POINTS.size]
    }
}
