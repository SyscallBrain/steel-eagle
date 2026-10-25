package pt.tiago.steeleagle.core.world

import pt.tiago.steeleagle.core.model.Dir
import pt.tiago.steeleagle.core.model.Fixed
import pt.tiago.steeleagle.core.model.PlayerInput
import pt.tiago.steeleagle.core.rules.EnemySpawn
import pt.tiago.steeleagle.core.rules.EnemyType
import pt.tiago.steeleagle.core.rules.PowerUpType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorldTwoPlayerTest {

    @Test
    fun `two players spawn on their own blocks with shields`() {
        val world = twoPlayerWorld()

        assertEquals(2, world.playerCount)
        assertEquals(64 * Fixed.SUBPIXELS, world.player.x)
        assertEquals(128 * Fixed.SUBPIXELS, world.player2!!.x)
        assertTrue(world.player.alive)
        assertTrue(world.player2!!.alive)
        assertTrue(world.player.shieldTicks > 0)
        assertTrue(world.player2!!.shieldTicks > 0)
    }

    @Test
    fun `inputs move each player independently`() {
        val world = twoPlayerWorld()
        world.player.shieldTicks = 0
        world.player2!!.shieldTicks = 0
        val p1Start = world.player.x
        val p2Start = world.player2!!.x

        world.step(InputFrame(PlayerInput(Dir.LEFT, fire = false), PlayerInput(Dir.RIGHT, fire = false)))

        assertTrue(world.player.x < p1Start)
        assertTrue(world.player2!!.x > p2Start)
    }

    @Test
    fun `friendly fire stuns but never costs a life`() {
        val world = twoPlayerWorld()
        world.player2!!.shieldTicks = 0
        world.bullets += Bullet(
            x = world.player2!!.x - 64,
            y = world.player2!!.y,
            dir = Dir.RIGHT,
            owner = world.player,
        )

        world.step(InputFrame(PlayerInput.NONE, PlayerInput.NONE))

        assertEquals(3, world.livesOf(1))
        assertTrue(world.player2!!.alive)
        assertEquals(World.STUN_TICKS, world.player2!!.stunTicks)

        val stunnedX = world.player2!!.x
        repeat(10) {
            world.step(InputFrame(PlayerInput.NONE, PlayerInput(Dir.RIGHT, fire = false)))
        }
        assertEquals(stunnedX, world.player2!!.x)
    }

    @Test
    fun `a stunned player moves again when the stun expires`() {
        val world = twoPlayerWorld()
        world.player2!!.shieldTicks = 0
        world.bullets += Bullet(
            x = world.player2!!.x - 64,
            y = world.player2!!.y,
            dir = Dir.RIGHT,
            owner = world.player,
        )
        world.step(InputFrame(PlayerInput.NONE, PlayerInput.NONE))
        repeat(World.STUN_TICKS) {
            world.step(InputFrame(PlayerInput.NONE, PlayerInput.NONE))
        }

        val before = world.player2!!.x
        world.step(InputFrame(PlayerInput.NONE, PlayerInput(Dir.RIGHT, fire = false)))

        assertTrue(world.player2!!.x > before)
    }

    @Test
    fun `an enemy bullet kills one player while the other continues`() {
        val world = twoPlayerWorld()
        world.player.shieldTicks = 0
        world.bullets += enemyBullet(x = world.player.x - 64, y = world.player.y)

        world.step(InputFrame(PlayerInput.NONE, PlayerInput.NONE))

        assertFalse(world.player.alive)
        assertEquals(2, world.livesOf(0))
        assertTrue(world.player2!!.alive)
        assertEquals(WorldState.PLAYING, world.state)
        assertTrue(world.respawnTicks > 0)
    }

    @Test
    fun `the surviving player respawns after the respawn delay`() {
        val world = twoPlayerWorld()
        world.player.shieldTicks = 0
        world.bullets += enemyBullet(x = world.player.x - 64, y = world.player.y)
        world.step(InputFrame(PlayerInput.NONE, PlayerInput.NONE))

        repeat(World.RESPAWN_TICKS + 1) {
            world.step(InputFrame(PlayerInput.NONE, PlayerInput.NONE))
        }

        assertTrue(world.player.alive)
        assertEquals(64 * Fixed.SUBPIXELS, world.player.x)
        assertTrue(world.player.shieldTicks > 0)
    }

    @Test
    fun `the game ends only when every player has no lives left`() {
        val world = World(
            TerrainGrid.empty(),
            playerCount = 2,
            initialLives = 1,
            initialLivesP2 = 1,
        )
        world.player.shieldTicks = 0
        world.player2!!.shieldTicks = 0
        val enemy = Tank(x = 0, y = 0, isPlayer = false).apply { aiEnabled = false }
        world.tanks += enemy
        world.bullets += enemyBullet(x = world.player.x - 64, y = world.player.y)
        world.bullets += enemyBullet(x = world.player2!!.x - 64, y = world.player2!!.y)

        world.step(InputFrame(PlayerInput.NONE, PlayerInput.NONE))

        assertFalse(world.player.alive)
        assertFalse(world.player2!!.alive)
        assertEquals(0, world.livesOf(0))
        assertEquals(0, world.livesOf(1))
        assertEquals(WorldState.GAME_OVER, world.state)
    }

    @Test
    fun `kills and points belong to the player who fired`() {
        val world = twoPlayerWorld()
        world.player2!!.shieldTicks = 1_000_000
        val enemy = Tank(
            x = world.player2!!.x + 64,
            y = world.player2!!.y,
            isPlayer = false,
            type = EnemyType.BASIC,
            hp = 1,
        ).apply { aiEnabled = false }
        world.tanks += enemy
        world.bullets += Bullet(
            x = enemy.x - 64,
            y = enemy.y,
            dir = Dir.RIGHT,
            owner = world.player2!!,
        )

        world.step(InputFrame(PlayerInput.NONE, PlayerInput.NONE))

        assertEquals(1, world.killsFor(1, EnemyType.BASIC))
        assertEquals(100, world.scoreOf(1))
        assertEquals(0, world.killsFor(0, EnemyType.BASIC))
        assertEquals(0, world.scoreOf(0))
    }

    @Test
    fun `a power up collected by player two boosts player two`() {
        val world = twoPlayerWorld()
        world.debugSpawnPowerUp(PowerUpType.STAR)
        val powerUp = assertNotNull(world.powerUp)

        world.player2!!.x = powerUp.x * Fixed.SUBPIXELS
        world.player2!!.y = powerUp.y * Fixed.SUBPIXELS
        world.step(InputFrame(PlayerInput.NONE, PlayerInput.NONE))

        assertEquals(1, world.player2!!.weaponLevel)
        assertEquals(500, world.scoreOf(1))
        assertEquals(0, world.scoreOf(0))
    }

    @Test
    fun `a player with no lives starts the new level out`() {
        val world = World(
            TerrainGrid.empty(),
            playerCount = 2,
            initialLives = 0,
            initialLivesP2 = 3,
        )

        assertFalse(world.player.alive)
        assertEquals(0, world.livesOf(0))
        assertTrue(world.player2!!.alive)
        assertEquals(3, world.livesOf(1))
    }

    @Test
    fun `friendly fire also works from player two to player one`() {
        val world = twoPlayerWorld()
        world.player.shieldTicks = 0
        world.bullets += Bullet(
            x = world.player.x - 64,
            y = world.player.y,
            dir = Dir.RIGHT,
            owner = world.player2!!,
        )

        world.step(InputFrame(PlayerInput.NONE, PlayerInput.NONE))

        assertTrue(world.player.alive)
        assertEquals(3, world.livesOf(0))
        assertEquals(World.STUN_TICKS, world.player.stunTicks)
    }

    @Test
    fun `player two grenade credits kills without enemy points`() {
        val world = twoPlayerWorld()
        world.tanks += Tank(160 * 16, 128 * 16, isPlayer = false).apply { aiEnabled = false }
        world.tanks += Tank(176 * 16, 128 * 16, isPlayer = false).apply { aiEnabled = false }
        world.debugSpawnPowerUp(PowerUpType.GRENADE)
        val powerUp = assertNotNull(world.powerUp)
        world.player2!!.x = powerUp.x * Fixed.SUBPIXELS
        world.player2!!.y = powerUp.y * Fixed.SUBPIXELS

        world.step(InputFrame(PlayerInput.NONE, PlayerInput.NONE))

        assertEquals(2, world.totalKills(1))
        assertEquals(0, world.totalKills(0))
        assertEquals(500, world.scoreOf(1))
        assertEquals(0, world.scoreOf(0))
    }

    @Test
    fun `player two tank power up awards the extra life to player two`() {
        val world = twoPlayerWorld()
        world.debugSpawnPowerUp(PowerUpType.TANK)
        val powerUp = assertNotNull(world.powerUp)
        world.player2!!.x = powerUp.x * Fixed.SUBPIXELS
        world.player2!!.y = powerUp.y * Fixed.SUBPIXELS

        world.step(InputFrame(PlayerInput.NONE, PlayerInput.NONE))

        assertEquals(4, world.livesOf(1))
        assertEquals(3, world.livesOf(0))
    }

    @Test
    fun `player two boat absorbs one enemy hit`() {
        val world = twoPlayerWorld()
        world.player2!!.hasBoat = true
        world.player2!!.shieldTicks = 0
        world.bullets += enemyBullet(x = world.player2!!.x - 64, y = world.player2!!.y)

        world.step(InputFrame(PlayerInput.NONE, PlayerInput.NONE))

        assertTrue(world.player2!!.alive)
        assertFalse(world.player2!!.hasBoat)
        assertEquals(3, world.livesOf(1))
    }

    @Test
    fun `helmet blocks a friendly hit without stunning`() {
        val world = twoPlayerWorld()
        world.player2!!.shieldTicks = 100
        world.bullets += Bullet(
            x = world.player2!!.x - 64,
            y = world.player2!!.y,
            dir = Dir.RIGHT,
            owner = world.player,
        )

        world.step(InputFrame(PlayerInput.NONE, PlayerInput.NONE))

        assertEquals(0, world.player2!!.stunTicks)
        assertEquals(3, world.livesOf(1))
    }

    @Test
    fun `player two blocks an enemy spawn area`() {
        val world = twoPlayerWorld()
        world.player2!!.x = 0
        world.player2!!.y = 0

        assertFalse(world.isSpawnAreaFree(0, 0))
    }

    @Test
    fun `both players can be dead and respawn together`() {
        val world = twoPlayerWorld()
        world.player.shieldTicks = 0
        world.player2!!.shieldTicks = 0
        world.bullets += enemyBullet(x = world.player.x - 64, y = world.player.y)
        world.bullets += enemyBullet(x = world.player2!!.x - 64, y = world.player2!!.y)

        world.step(InputFrame(PlayerInput.NONE, PlayerInput.NONE))

        assertFalse(world.player.alive)
        assertFalse(world.player2!!.alive)
        assertEquals(WorldState.PLAYING, world.state)
        assertNull(world.nearestPlayer(Tank(x = 0, y = 0, isPlayer = false)))

        repeat(World.RESPAWN_TICKS + 1) {
            world.step(InputFrame(PlayerInput.NONE, PlayerInput.NONE))
        }

        assertTrue(world.player.alive)
        assertTrue(world.player2!!.alive)
        assertEquals(64 * Fixed.SUBPIXELS, world.player.x)
        assertEquals(128 * Fixed.SUBPIXELS, world.player2!!.x)
    }

    @Test
    fun `snapshot records per-player kills and points`() {
        val world = twoPlayerWorld()
        world.player2!!.shieldTicks = 1_000_000
        val enemy = Tank(
            x = world.player2!!.x + 64,
            y = world.player2!!.y,
            isPlayer = false,
            type = EnemyType.BASIC,
            hp = 1,
        ).apply { aiEnabled = false }
        world.tanks += enemy
        world.bullets += Bullet(
            x = enemy.x - 64,
            y = enemy.y,
            dir = Dir.RIGHT,
            owner = world.player2!!,
        )

        world.step(InputFrame(PlayerInput.NONE, PlayerInput.NONE))

        val snapshot = world.snapshot()
        assertTrue(snapshot.contains(";kills0=0,0,0,0,"))
        assertTrue(snapshot.contains(";kills1=1,0,0,0,"))
        assertTrue(snapshot.contains(";points1=100,0,0,0,"))
    }

    @Test
    fun `snapshot tells the two players' bullets apart`() {
        fun worldWithShooter(shooter: Int): World {
            val world = twoPlayerWorld()
            world.players.forEach { it.shieldTicks = 1_000_000 }
            world.bullets += Bullet(
                x = 64 * Fixed.SUBPIXELS,
                y = 64 * Fixed.SUBPIXELS,
                dir = Dir.RIGHT,
                owner = world.players[shooter],
            )
            return world
        }

        assertNotEquals(
            worldWithShooter(0).snapshot(),
            worldWithShooter(1).snapshot(),
        )
    }

    @Test
    fun `snapshot records player two movement and weapon state`() {
        fun worldWithPlayerTwo(mutate: (Tank) -> Unit): World {
            val world = twoPlayerWorld()
            mutate(world.player2!!)
            return world
        }

        val base = worldWithPlayerTwo { }
        val weapon = worldWithPlayerTwo { it.weaponLevel = 2 }
        val sliding = worldWithPlayerTwo { it.slideRemaining = 12 }
        val moving = worldWithPlayerTwo { it.wasMoving = true }

        assertNotEquals(base.snapshot(), weapon.snapshot())
        assertNotEquals(base.snapshot(), sliding.snapshot())
        assertNotEquals(base.snapshot(), moving.snapshot())
    }

    @Test
    fun `score entries reject an unknown player index`() {
        val world = twoPlayerWorld()

        assertFailsWith<IllegalArgumentException> { world.addScore(2, 100) }
        assertFailsWith<IllegalArgumentException> { world.addScore(-1, 100) }
    }

    @Test
    fun `two player simulation is deterministic with scripted input`() {
        fun build(): World {
            val world = World(
                terrain = TerrainGrid.empty(),
                seed = 42,
                playerCount = 2,
                enemyRoster = listOf(
                    EnemySpawn(EnemyType.BASIC, false),
                    EnemySpawn(EnemyType.FAST, false),
                    EnemySpawn(EnemyType.BASIC, false),
                ),
            )
            world.player2!!.shieldTicks = 0
            val enemy = Tank(
                x = world.player2!!.x + 256,
                y = world.player2!!.y,
                isPlayer = false,
                type = EnemyType.BASIC,
                hp = 1,
            ).apply { aiEnabled = false }
            world.tanks += enemy
            world.bullets += Bullet(
                x = enemy.x - 64,
                y = enemy.y,
                dir = Dir.RIGHT,
                owner = world.player2!!,
            )
            world.bullets += Bullet(
                x = world.player2!!.x - 64,
                y = world.player2!!.y,
                dir = Dir.RIGHT,
                owner = world.player,
            )
            return world
        }

        val first = build()
        val second = build()

        val setupInput = InputFrame(PlayerInput.NONE, PlayerInput.NONE)
        first.step(setupInput)
        second.step(setupInput)

        assertEquals(1, first.killsFor(1, EnemyType.BASIC))
        assertEquals(100, first.scoreOf(1))
        assertEquals(0, first.scoreOf(0))
        assertTrue(first.player2!!.stunTicks > 0)
        assertEquals(first.snapshot(), second.snapshot())

        repeat(500) { tick ->
            val input = InputFrame(
                PlayerInput(
                    direction = if (tick % 3 == 0) Dir.RIGHT else null,
                    fire = tick % 7 == 0,
                ),
                PlayerInput(
                    direction = if (tick % 5 == 0) Dir.DOWN else null,
                    fire = tick % 11 == 0,
                ),
            )
            first.step(input)
            second.step(input)
        }

        assertEquals(first.snapshot(), second.snapshot())
    }

    @Test
    fun `player two extra life is independent`() {
        val world = twoPlayerWorld()

        world.addScore(1, 20_000)

        assertEquals(4, world.livesOf(1))
        assertEquals(3, world.livesOf(0))
        assertEquals(40_000, world.nextExtraLifeScoreOf(1))
        assertEquals(20_000, world.scoreOf(1))
        assertEquals(0, world.scoreOf(0))
    }

    @Test
    fun `a player with no lives left stops respawning`() {
        val world = World(
            TerrainGrid.empty(),
            playerCount = 2,
            initialLives = 1,
        )
        world.player.shieldTicks = 0
        world.bullets += enemyBullet(x = world.player.x - 64, y = world.player.y)

        world.step(InputFrame(PlayerInput(Dir.RIGHT, fire = false), PlayerInput.NONE))

        assertEquals(0, world.livesOf(0))
        assertEquals(0, world.respawnTicks)
        assertFalse(world.player.wasMoving)
        repeat(World.RESPAWN_TICKS * 2) {
            world.step(InputFrame(PlayerInput.NONE, PlayerInput.NONE))
        }
        assertFalse(world.player.alive)
        assertTrue(world.player2!!.alive)
        assertEquals(WorldState.PLAYING, world.state)
    }

    @Test
    fun `two players raise the on-screen enemy cap`() {
        val roster = List(20) { EnemySpawn(EnemyType.BASIC, false) }
        val one = World(TerrainGrid.empty(), enemyRoster = roster, playerCount = 1)
        val two = World(TerrainGrid.empty(), enemyRoster = roster, playerCount = 2)

        assertEquals(4, one.spawnDirector.maxOnScreen)
        assertEquals(6, two.spawnDirector.maxOnScreen)
        assertTrue(two.spawnDirector.spawnInterval < one.spawnDirector.spawnInterval)

        repeat(1_000) {
            two.step(InputFrame(PlayerInput.NONE, PlayerInput.NONE))
            val active = two.tanks.count { !it.isPlayer && it.alive } +
                two.spawnAnimations.size
            assertTrue(active <= 6, "active enemies: $active")
        }
    }

    private fun enemyBullet(x: Int, y: Int): Bullet {
        val enemy = Tank(x = 0, y = 0, isPlayer = false).apply { aiEnabled = false }
        return Bullet(x, y, Dir.RIGHT, enemy)
    }

    private fun twoPlayerWorld(): World = World(
        terrain = TerrainGrid.empty(),
        playerCount = 2,
    )
}
