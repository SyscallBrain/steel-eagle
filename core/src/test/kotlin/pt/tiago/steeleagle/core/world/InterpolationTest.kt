package pt.tiago.steeleagle.core.world

import pt.tiago.steeleagle.core.model.Dir
import pt.tiago.steeleagle.core.rules.Difficulty
import pt.tiago.steeleagle.core.rules.EnemyType
import pt.tiago.steeleagle.core.rules.PowerUpType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class InterpolationTest {

    @Test
    fun `tank and bullet positions are blended`() {
        val previous = snapshot(
            players = listOf(tank(0, 0), tank(100, 100)),
            enemies = listOf(tank(0, 0)),
            bullets = listOf(bullet(0, 0)),
        )
        val next = snapshot(
            players = listOf(tank(32, 16), tank(100, 100)),
            enemies = listOf(tank(16, 8)),
            bullets = listOf(bullet(8, 4)),
        )

        val blended = interpolateSnapshots(previous, next, 0.5f)

        assertEquals(16, blended.players[0].x)
        assertEquals(8, blended.players[0].y)
        assertEquals(100, blended.players[1].x)
        assertEquals(8, blended.enemies[0].x)
        assertEquals(4, blended.enemies[0].y)
        assertEquals(4, blended.bullets[0].x)
        assertEquals(2, blended.bullets[0].y)
    }

    @Test
    fun `bullets blend at their real speed`() {
        val previous = snapshot(bullets = listOf(bullet(100, 100)))
        val next = snapshot(bullets = listOf(bullet(228, 100)))

        val blended = interpolateSnapshots(previous, next, 0.5f)

        assertEquals(164, blended.bullets[0].x)
    }

    @Test
    fun `a bullet jump larger than two snapshots is not blended`() {
        val previous = snapshot(bullets = listOf(bullet(0, 0)))
        val next = snapshot(bullets = listOf(bullet(400, 0)))

        assertEquals(400, interpolateSnapshots(previous, next, 0.5f).bullets[0].x)
    }

    @Test
    fun `alpha is clamped`() {
        val previous = snapshot(players = listOf(tank(0, 0)))
        val next = snapshot(players = listOf(tank(20, 20)))

        assertEquals(20, interpolateSnapshots(previous, next, 2f).players[0].x)
        assertEquals(0, interpolateSnapshots(previous, next, -1f).players[0].x)
    }

    @Test
    fun `respawns and teleports are not blended`() {
        val previous = snapshot(players = listOf(tank(0, 0)))
        val next = snapshot(players = listOf(tank(1000, 1000)))

        assertEquals(1000, interpolateSnapshots(previous, next, 0.5f).players[0].x)
    }

    @Test
    fun `lists of different sizes use the new snapshot`() {
        val previous = snapshot(bullets = listOf(bullet(0, 0)))
        val next = snapshot(bullets = listOf(bullet(10, 10), bullet(20, 20)))

        val blended = interpolateSnapshots(previous, next, 0.5f)

        assertEquals(2, blended.bullets.size)
        assertEquals(10, blended.bullets[0].x)
    }

    @Test
    fun `a stage change is never blended`() {
        val previous = snapshot(players = listOf(tank(0, 0)), level = 1)
        val next = snapshot(players = listOf(tank(50, 50)), level = 2)

        assertSame(next, interpolateSnapshots(previous, next, 0.5f))
    }

    private fun tank(x: Int, y: Int) = TankState(
        x = x,
        y = y,
        dir = Dir.UP,
        trackFrame = 0,
        weaponLevel = 0,
        alive = true,
        shieldTicks = 0,
        stunTicks = 0,
        hasBoat = false,
        escapingWater = false,
        slideRemaining = 0,
        wasMoving = true,
        bulletSpeed = 128,
        type = null,
        hp = 1,
    )

    private fun bullet(x: Int, y: Int) = BulletState(
        x = x,
        y = y,
        dir = Dir.RIGHT,
        ownerPlayer = 0,
        ownerEnemy = null,
        weaponLevel = 0,
        speed = 128,
    )

    private fun snapshot(
        players: List<TankState> = listOf(tank(0, 0), tank(0, 0)),
        enemies: List<TankState> = emptyList(),
        bullets: List<BulletState> = emptyList(),
        level: Int = 1,
    ) = WorldSnapshot(
        tick = 1,
        state = WorldState.PLAYING,
        levelNumber = level,
        difficulty = Difficulty.NORMAL,
        playerCount = 2,
        lives = listOf(3, 3),
        scores = listOf(0, 0),
        enemiesRemaining = 20,
        totalEnemies = 20,
        eagleAlive = true,
        players = players,
        enemies = enemies,
        bullets = bullets,
        powerUp = PowerUpState(PowerUpType.STAR, 0, 0, 100),
        freezeTicks = 0,
        shovelTicks = 0,
    )
}
