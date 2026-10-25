package pt.tiago.steeleagle.core.world

import pt.tiago.steeleagle.core.model.Dir
import pt.tiago.steeleagle.core.model.Fixed
import pt.tiago.steeleagle.core.model.PlayerInput
import pt.tiago.steeleagle.core.rules.Difficulty
import pt.tiago.steeleagle.core.rules.EnemySpawn
import pt.tiago.steeleagle.core.rules.EnemyType
import pt.tiago.steeleagle.core.rules.PowerUpType
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorldPowerUpTest {

    @Test
    fun `star raises weapon level and bullet speed`() {
        val world = World(TerrainGrid.empty())

        collect(world, PowerUpType.STAR)
        assertEquals(1, world.player.weaponLevel)
        assertEquals(96, world.player.bulletSpeed)

        collect(world, PowerUpType.STAR)
        assertEquals(2, world.player.weaponLevel)

        collect(world, PowerUpType.STAR)
        assertEquals(3, world.player.weaponLevel)

        collect(world, PowerUpType.STAR)
        assertEquals(3, world.player.weaponLevel)
        assertEquals(2_000, world.score)
    }

    @Test
    fun `gun jumps straight to level three`() {
        val world = World(TerrainGrid.empty())

        collect(world, PowerUpType.GUN)

        assertEquals(3, world.player.weaponLevel)
        assertEquals(96, world.player.bulletSpeed)
    }

    @Test
    fun `grenade destroys enemies without points`() {
        val roster = List(2) { EnemySpawn(EnemyType.BASIC, false) }
        val world = World(TerrainGrid.empty(), enemyRoster = roster)
        world.player.shieldTicks = 1_000_000
        world.tanks += enemyAt(100, 100)
        world.tanks += enemyAt(140, 100)

        collect(world, PowerUpType.GRENADE)

        assertEquals(500, world.score)
        assertTrue(world.tanks.filter { !it.isPlayer }.none { it.alive })
        assertEquals(2, world.spawnDirector.defeated)
        assertEquals(WorldState.LEVEL_CLEAR, world.state)
    }

    @Test
    fun `helmet gives ten seconds of shield scaled by difficulty`() {
        val normal = World(TerrainGrid.empty())
        collect(normal, PowerUpType.HELMET)
        assertTrue(normal.player.shieldTicks in 599..600)

        val easy = World(TerrainGrid.empty(), difficulty = Difficulty.EASY)
        collect(easy, PowerUpType.HELMET)
        assertTrue(easy.player.shieldTicks in 719..720)
    }

    @Test
    fun `shovel steels the eagle wall and restores brick`() {
        val world = World(TerrainGrid.empty())

        collect(world, PowerUpType.SHOVEL)

        assertTrue(world.shovelTicks in 1_199..1_200)
        assertWall(world, Terrain.STEEL)

        repeat(1_200) { step(world) }

        assertWall(world, Terrain.BRICK)
        assertEquals(0, world.shovelTicks)
    }

    @Test
    fun `clock freezes enemies and spawns`() {
        val roster = List(1) { EnemySpawn(EnemyType.BASIC, false) }
        val world = World(TerrainGrid.empty(), enemyRoster = roster)
        world.player.shieldTicks = 1_000_000
        val enemy = enemyAt(96, 0, speed = 8, aiEnabled = true)
        world.tanks += enemy

        collect(world, PowerUpType.CLOCK)
        assertEquals(600, world.freezeTicks)
        val frozenY = enemy.y

        repeat(100) { step(world) }
        assertEquals(frozenY, enemy.y)
        assertEquals(0, world.spawnDirector.spawnedCount)

        repeat(600) { step(world) }
        assertTrue(
            world.spawnDirector.spawnedCount >= 1 ||
                world.tanks.any { !it.isPlayer && it !== enemy && it.alive },
        )
    }

    @Test
    fun `tank power up grants a life`() {
        val world = World(TerrainGrid.empty())
        val before = world.lives

        collect(world, PowerUpType.TANK)

        assertEquals(before + 1, world.lives)
    }

    @Test
    fun `boat lets the player cross water`() {
        val terrain = TerrainGrid.empty()
        terrain.set8(3, 24, Terrain.WATER)
        val world = World(terrain, spawnPixelX = 40, spawnPixelY = 192)
        world.player.shieldTicks = 0

        collect(world, PowerUpType.BOAT)
        assertTrue(world.player.hasBoat)

        world.player.x = 40 * Fixed.SUBPIXELS
        world.player.y = 192 * Fixed.SUBPIXELS
        repeat(20) { step(world, PlayerInput(Dir.LEFT, fire = false)) }

        assertTrue(world.player.x < 32 * Fixed.SUBPIXELS)
    }

    @Test
    fun `boat lost on water still lets the player reach land`() {
        val terrain = TerrainGrid.empty()
        terrain.set8(3, 24, Terrain.WATER)
        val world = World(terrain, spawnPixelX = 28, spawnPixelY = 192)
        world.player.hasBoat = true
        world.player.shieldTicks = 0
        val enemy = enemyAt(0, 0)
        world.bullets += Bullet(
            x = 28 * Fixed.SUBPIXELS,
            y = 184 * Fixed.SUBPIXELS,
            dir = Dir.DOWN,
            owner = enemy,
        )
        repeat(2) { step(world) }

        assertTrue(world.player.alive)
        assertFalse(world.player.hasBoat)
        assertTrue(world.player.escapingWater)

        repeat(10) { step(world, PlayerInput(Dir.RIGHT, fire = false)) }
        assertFalse(world.player.escapingWater)
        assertTrue(world.player.x >= 32 * Fixed.SUBPIXELS)

        repeat(10) { step(world, PlayerInput(Dir.LEFT, fire = false)) }
        assertEquals(32 * Fixed.SUBPIXELS, world.player.x)
    }

    @Test
    fun `boat absorbs one hit`() {
        val world = World(TerrainGrid.empty())
        world.player.shieldTicks = 0

        collect(world, PowerUpType.BOAT)
        world.player.x = 64 * Fixed.SUBPIXELS
        world.player.y = 192 * Fixed.SUBPIXELS
        world.player.shieldTicks = 0

        val enemy = enemyAt(0, 0)
        world.bullets += Bullet(
            x = 64 * Fixed.SUBPIXELS,
            y = 184 * Fixed.SUBPIXELS,
            dir = Dir.DOWN,
            owner = enemy,
        )
        repeat(2) { step(world) }

        assertTrue(world.player.alive)
        assertFalse(world.player.hasBoat)
        assertEquals(3, world.lives)

        world.bullets += Bullet(
            x = 64 * Fixed.SUBPIXELS,
            y = 184 * Fixed.SUBPIXELS,
            dir = Dir.DOWN,
            owner = enemy,
        )
        repeat(2) { step(world) }

        assertFalse(world.player.alive)
        assertEquals(2, world.lives)
    }

    @Test
    fun `shovel does not encase a tank in the wall`() {
        val world = World(TerrainGrid.empty())
        world.player.shieldTicks = 1_000_000
        world.debugSpawnPowerUp(PowerUpType.SHOVEL)
        val powerUp = requireNotNull(world.powerUp)
        powerUp.x = 88
        powerUp.y = 176
        world.player.x = 88 * Fixed.SUBPIXELS
        world.player.y = 176 * Fixed.SUBPIXELS

        step(world)

        assertTrue(world.canMove(world.player, Dir.UP))
        assertEquals(Terrain.EMPTY, world.terrain.get(22, 46))
        assertEquals(Terrain.EMPTY, world.terrain.get(23, 46))
    }

    @Test
    fun `shovel blink is visual only`() {
        val world = World(TerrainGrid.empty())

        collect(world, PowerUpType.SHOVEL)
        repeat(1_100) { step(world) }

        assertTrue(world.shovelBlinking)
        assertWall(world, Terrain.STEEL)
    }

    @Test
    fun `shield keeps the boat`() {
        val world = World(TerrainGrid.empty())
        collect(world, PowerUpType.BOAT)
        world.player.x = 64 * Fixed.SUBPIXELS
        world.player.y = 192 * Fixed.SUBPIXELS
        world.player.shieldTicks = 600
        val enemy = enemyAt(0, 0)
        world.bullets += Bullet(
            x = 64 * Fixed.SUBPIXELS,
            y = 184 * Fixed.SUBPIXELS,
            dir = Dir.DOWN,
            owner = enemy,
        )

        repeat(2) { step(world) }

        assertTrue(world.player.alive)
        assertTrue(world.player.hasBoat)
        assertTrue(world.player.shieldTicks in 597..599)
    }

    @Test
    fun `star and boat reset on death`() {
        val world = World(TerrainGrid.empty())
        collect(world, PowerUpType.STAR)
        collect(world, PowerUpType.GUN)
        collect(world, PowerUpType.BOAT)
        assertEquals(3, world.player.weaponLevel)
        assertTrue(world.player.hasBoat)

        world.player.x = 64 * Fixed.SUBPIXELS
        world.player.y = 192 * Fixed.SUBPIXELS
        world.player.shieldTicks = 0
        val enemy = enemyAt(0, 0)
        world.bullets += Bullet(64 * Fixed.SUBPIXELS, 184 * Fixed.SUBPIXELS, Dir.DOWN, enemy)
        repeat(2) { step(world) }
        world.bullets += Bullet(64 * Fixed.SUBPIXELS, 184 * Fixed.SUBPIXELS, Dir.DOWN, enemy)
        repeat(2) { step(world) }

        assertFalse(world.player.alive)
        assertEquals(0, world.player.weaponLevel)
        assertEquals(Fixed.BULLET_SPEED, world.player.bulletSpeed)
        assertFalse(world.player.hasBoat)

        repeat(60) { step(world) }
        assertTrue(world.player.alive)
        assertEquals(0, world.player.weaponLevel)
        assertFalse(world.player.hasBoat)
    }

    @Test
    fun `clock does not freeze the player`() {
        val world = World(TerrainGrid.empty())
        collect(world, PowerUpType.CLOCK)
        val before = world.player.x

        repeat(10) { step(world, PlayerInput(Dir.RIGHT, fire = false)) }

        assertTrue(world.player.x > before)
    }

    @Test
    fun `power up is not collected while dead`() {
        val world = World(TerrainGrid.empty())
        world.player.shieldTicks = 0
        val enemy = enemyAt(0, 0)
        world.bullets += Bullet(64 * Fixed.SUBPIXELS, 184 * Fixed.SUBPIXELS, Dir.DOWN, enemy)
        repeat(2) { step(world) }
        assertFalse(world.player.alive)

        world.debugSpawnPowerUp(PowerUpType.STAR)
        val powerUp = requireNotNull(world.powerUp)
        powerUp.x = 64
        powerUp.y = 192
        step(world)

        assertNotNull(world.powerUp)
        assertEquals(0, world.player.weaponLevel)
    }

    @Test
    fun `hard difficulty scales power up durations`() {
        val world = World(TerrainGrid.empty(), difficulty = Difficulty.HARD)

        world.debugSpawnPowerUp(PowerUpType.STAR)
        assertEquals(765, requireNotNull(world.powerUp).ticksRemaining)

        collect(world, PowerUpType.HELMET)
        assertTrue(world.player.shieldTicks in 509..510)

        collect(world, PowerUpType.CLOCK)
        assertEquals(510, world.freezeTicks)

        collect(world, PowerUpType.SHOVEL)
        assertTrue(world.shovelTicks in 1_019..1_020)
    }

    @Test
    fun `power up gives five hundred points and can grant an extra life`() {
        val world = World(TerrainGrid.empty())
        world.score = 19_600

        collect(world, PowerUpType.STAR)

        assertEquals(20_100, world.score)
        assertEquals(4, world.lives)
    }

    @Test
    fun `power up expires after its lifetime`() {
        val world = World(TerrainGrid.empty())
        world.debugSpawnPowerUp(PowerUpType.STAR)
        val powerUp = requireNotNull(world.powerUp)
        assertEquals(900, powerUp.ticksRemaining)

        powerUp.ticksRemaining = 1
        step(world)

        assertNull(world.powerUp)
    }

    @Test
    fun `easy difficulty extends the power up lifetime`() {
        val world = World(TerrainGrid.empty(), difficulty = Difficulty.EASY)

        world.debugSpawnPowerUp(PowerUpType.STAR)

        assertEquals(1_080, requireNotNull(world.powerUp).ticksRemaining)
    }

    @Test
    fun `a new power up replaces the old one`() {
        val world = World(TerrainGrid.empty())

        world.debugSpawnPowerUp(PowerUpType.STAR)
        world.debugSpawnPowerUp(PowerUpType.GUN)

        assertEquals(PowerUpType.GUN, requireNotNull(world.powerUp).type)
    }

    @Test
    fun `power up blinks faster near expiry`() {
        val powerUp = PowerUp(PowerUpType.STAR, 0, 0, 900)

        assertTrue(powerUp.isVisible(0))
        assertFalse(powerUp.isVisible(8))
        assertTrue(powerUp.isVisible(16))

        powerUp.ticksRemaining = 100
        assertTrue(powerUp.isVisible(0))
        assertFalse(powerUp.isVisible(4))
        assertTrue(powerUp.isVisible(8))
    }

    @Test
    fun `debug spawn places a power up near the player`() {
        val world = World(TerrainGrid.empty())

        world.debugSpawnPowerUp(PowerUpType.STAR)
        val powerUp = requireNotNull(world.powerUp)

        assertTrue(abs(powerUp.x - 64) <= 32)
        assertTrue(abs(powerUp.y - 192) <= 32)
    }

    @Test
    fun `power ups avoid trees and blocking terrain`() {
        val terrain = TerrainGrid.empty()
        for (blockY in 0 until 26) {
            for (blockX in 0 until 26) {
                terrain.set8(blockX, blockY, Terrain.TREES)
            }
        }
        val world = World(terrain)

        world.debugSpawnPowerUp(PowerUpType.STAR)

        assertNull(world.powerUp)
    }

    @Test
    fun `flashing enemy spawns a power up`() {
        val world = World(TerrainGrid.empty())
        world.player.shieldTicks = 1_000_000
        val enemy = enemyAt(100, 100, EnemyType.BASIC, hp = 1, flashing = true)
        world.tanks += enemy
        world.bullets += Bullet(
            x = 96 * Fixed.SUBPIXELS,
            y = 100 * Fixed.SUBPIXELS,
            dir = Dir.RIGHT,
            owner = world.player,
        )

        step(world)

        assertNotNull(world.powerUp)
        assertEquals(0, world.pendingPowerUpDrops)
    }

    private fun collect(world: World, type: PowerUpType) {
        world.debugSpawnPowerUp(type)
        val powerUp = requireNotNull(world.powerUp)
        world.player.x = powerUp.x * Fixed.SUBPIXELS
        world.player.y = powerUp.y * Fixed.SUBPIXELS
        step(world)
    }

    private fun step(world: World, input: PlayerInput = PlayerInput.NONE) {
        world.step(InputFrame(input))
    }

    private fun assertWall(world: World, terrain: Terrain) {
        for ((blockX, blockY) in World.EAGLE_WALL_BLOCKS) {
            for (dy in 0..1) {
                for (dx in 0..1) {
                    assertEquals(
                        terrain,
                        world.terrain.get(blockX * 2 + dx, blockY * 2 + dy),
                    )
                }
            }
        }
    }

    private fun enemyAt(
        x: Int,
        y: Int,
        type: EnemyType = EnemyType.BASIC,
        hp: Int = type.hitPoints,
        flashing: Boolean = false,
        speed: Int = 0,
        aiEnabled: Boolean = false,
    ): Tank = Tank(
        x = x * Fixed.SUBPIXELS,
        y = y * Fixed.SUBPIXELS,
        dir = Dir.DOWN,
        isPlayer = false,
        speed = speed,
        type = type,
        hp = hp,
        flashing = flashing,
    ).apply {
        this.aiEnabled = aiEnabled
    }

}
