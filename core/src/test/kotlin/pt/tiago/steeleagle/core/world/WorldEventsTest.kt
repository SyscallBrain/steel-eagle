package pt.tiago.steeleagle.core.world

import pt.tiago.steeleagle.core.game.GameEvent
import pt.tiago.steeleagle.core.model.Dir
import pt.tiago.steeleagle.core.model.Fixed
import pt.tiago.steeleagle.core.model.PlayerInput
import pt.tiago.steeleagle.core.rules.EnemySpawn
import pt.tiago.steeleagle.core.rules.EnemyType
import pt.tiago.steeleagle.core.rules.PowerUpType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorldEventsTest {

    @Test
    fun `player shot emits ShotFired`() {
        val world = World(TerrainGrid.empty())

        world.step(InputFrame(PlayerInput(Dir.UP, fire = true)))

        val event = world.events.filterIsInstance<GameEvent.ShotFired>().single()
        assertTrue(event.isPlayer)
    }

    @Test
    fun `brick hit emits BrickBroken`() {
        val terrain = TerrainGrid.empty()
        terrain.set8(10, 10, Terrain.BRICK)
        val world = World(terrain)
        world.bullets += Bullet(76 * 16, 80 * 16, Dir.RIGHT, world.player)

        world.step(InputFrame(PlayerInput.NONE))

        assertTrue(world.events.any { it is GameEvent.BrickBroken })
    }

    @Test
    fun `steel hit emits SteelHit`() {
        val terrain = TerrainGrid.empty()
        terrain.set8(10, 10, Terrain.STEEL)
        val world = World(terrain)
        world.bullets += Bullet(76 * 16, 80 * 16, Dir.RIGHT, world.player)

        world.step(InputFrame(PlayerInput.NONE))

        assertTrue(world.events.any { it is GameEvent.SteelHit })
    }

    @Test
    fun `enemy death emits EnemyExploded and scores`() {
        val world = World(TerrainGrid.empty())
        world.player.shieldTicks = 1_000_000
        val enemy = Tank(
            x = 100 * 16,
            y = 100 * 16,
            isPlayer = false,
            type = EnemyType.BASIC,
            hp = 1,
        ).apply { aiEnabled = false }
        world.tanks += enemy
        world.bullets += Bullet(96 * 16, 100 * 16, Dir.RIGHT, world.player)

        world.step(InputFrame(PlayerInput.NONE))

        assertTrue(world.events.any { it is GameEvent.EnemyExploded })
        assertEquals(100, world.score)
        assertEquals(1, world.killsFor(EnemyType.BASIC))
        assertEquals(100, world.scoreFor(EnemyType.BASIC))
    }

    @Test
    fun `player death emits PlayerExploded and GameOver on last life`() {
        val world = World(TerrainGrid.empty())
        world.lives = 1
        world.player.shieldTicks = 0
        val enemy = Tank(x = 0, y = 0, isPlayer = false).apply { aiEnabled = false }
        world.bullets += Bullet(64 * 16, 184 * 16, Dir.DOWN, enemy)

        repeat(2) { world.step(InputFrame(PlayerInput.NONE)) }

        assertTrue(world.events.any { it is GameEvent.PlayerExploded })
        assertTrue(world.events.any { it == GameEvent.GameOver })
        assertEquals(WorldState.GAME_OVER, world.state)
    }

    @Test
    fun `power up emits appeared and picked events`() {
        val world = World(TerrainGrid.empty())
        world.debugSpawnPowerUp(PowerUpType.STAR)
        assertTrue(world.events.any { it is GameEvent.PowerUpAppeared })

        val powerUp = requireNotNull(world.powerUp)
        world.player.x = powerUp.x * Fixed.SUBPIXELS
        world.player.y = powerUp.y * Fixed.SUBPIXELS
        world.events.clear()
        world.step(InputFrame(PlayerInput.NONE))

        assertTrue(world.events.any { it is GameEvent.PowerUpPicked })
        assertEquals(500, world.score)
    }

    @Test
    fun `extra life emits ExtraLife`() {
        val world = World(TerrainGrid.empty())
        world.score = 19_600
        world.debugSpawnPowerUp(PowerUpType.STAR)
        val powerUp = requireNotNull(world.powerUp)
        world.player.x = powerUp.x * Fixed.SUBPIXELS
        world.player.y = powerUp.y * Fixed.SUBPIXELS
        world.events.clear()

        world.step(InputFrame(PlayerInput.NONE))

        assertTrue(world.events.any { it == GameEvent.ExtraLife })
        assertEquals(4, world.lives)
    }

    @Test
    fun `grenade emits explosions without enemy points`() {
        val world = World(
            TerrainGrid.empty(),
            enemyRoster = List(2) { EnemySpawn(EnemyType.BASIC, false) },
        )
        world.player.shieldTicks = 1_000_000
        world.tanks += Tank(100 * 16, 100 * 16, isPlayer = false).apply { aiEnabled = false }
        world.tanks += Tank(140 * 16, 100 * 16, isPlayer = false).apply { aiEnabled = false }
        world.debugSpawnPowerUp(PowerUpType.GRENADE)
        val powerUp = requireNotNull(world.powerUp)
        world.player.x = powerUp.x * Fixed.SUBPIXELS
        world.player.y = powerUp.y * Fixed.SUBPIXELS
        world.events.clear()

        world.step(InputFrame(PlayerInput.NONE))

        assertEquals(2, world.events.count { it is GameEvent.EnemyExploded })
        assertEquals(500, world.score)
        assertEquals(2, world.killsFor(EnemyType.BASIC))
        assertEquals(0, world.scoreFor(EnemyType.BASIC))
    }

    @Test
    fun `tank power up emits ExtraLife`() {
        val world = World(TerrainGrid.empty())
        world.debugSpawnPowerUp(PowerUpType.TANK)
        val powerUp = requireNotNull(world.powerUp)
        world.player.x = powerUp.x * Fixed.SUBPIXELS
        world.player.y = powerUp.y * Fixed.SUBPIXELS
        world.events.clear()

        world.step(InputFrame(PlayerInput.NONE))

        assertEquals(4, world.lives)
        assertTrue(world.events.any { it == GameEvent.ExtraLife })
    }

    @Test
    fun `level clear emits LevelClear`() {
        val world = World(
            TerrainGrid.empty(),
            enemyRoster = listOf(EnemySpawn(EnemyType.BASIC, false)),
        )
        world.player.shieldTicks = 1_000_000
        val enemy = Tank(
            x = 100 * 16,
            y = 100 * 16,
            isPlayer = false,
            type = EnemyType.BASIC,
            hp = 1,
        ).apply { aiEnabled = false }
        world.tanks += enemy
        world.bullets += Bullet(96 * 16, 100 * 16, Dir.RIGHT, world.player)

        world.step(InputFrame(PlayerInput.NONE))

        assertTrue(world.events.any { it == GameEvent.LevelClear })
        assertEquals(WorldState.LEVEL_CLEAR, world.state)
    }

    @Test
    fun `eagle destruction emits EagleDestroyed and GameOver`() {
        val world = World(TerrainGrid.empty())
        val enemy = Tank(x = 0, y = 0, isPlayer = false).apply { aiEnabled = false }
        world.bullets += Bullet(world.eagle.x, world.eagle.y, Dir.DOWN, enemy)

        world.step(InputFrame(PlayerInput.NONE))

        assertTrue(world.events.any { it is GameEvent.EagleDestroyed })
        assertTrue(world.events.any { it == GameEvent.GameOver })
    }
}
