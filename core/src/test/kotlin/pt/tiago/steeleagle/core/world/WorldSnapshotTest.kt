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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorldSnapshotTest {

    @Test
    fun `a captured snapshot applies back to an identical world`() {
        val host = twoPlayerWorld()
        val guest = twoPlayerWorld()
        host.player.shieldTicks = 0
        guest.player.shieldTicks = 0
        host.bullets += Bullet(x = 200, y = 300, dir = Dir.RIGHT, owner = host.player)
        host.bullets += enemyBullet(host, x = 400, y = 500)
        enemy(host, 600, 700, EnemyType.ARMORED, hp = 2)
        host.debugSpawnPowerUp(PowerUpType.STAR)
        repeat(30) { host.step(InputFrame(PlayerInput.NONE, PlayerInput.NONE)) }

        val snapshot = host.captureSnapshot()
        guest.applySnapshot(snapshot)

        assertEquals(snapshot, guest.captureSnapshot())
    }

    @Test
    fun `moving and scoring survive the snapshot`() {
        val host = twoPlayerWorld()
        val guest = twoPlayerWorld()
        host.player2!!.shieldTicks = 1_000_000
        val target = Tank(
            x = host.player2!!.x + 64,
            y = host.player2!!.y,
            isPlayer = false,
            type = EnemyType.BASIC,
            hp = 1,
        ).apply { aiEnabled = false }
        host.tanks += target
        host.bullets += Bullet(
            x = target.x - 64,
            y = target.y,
            dir = Dir.RIGHT,
            owner = host.player2!!,
        )
        host.step(InputFrame(PlayerInput(Dir.RIGHT, false), PlayerInput(Dir.LEFT, false)))
        host.step(InputFrame(PlayerInput.NONE, PlayerInput.NONE))

        guest.applySnapshot(host.captureSnapshot())

        assertEquals(host.player.x, guest.player.x)
        assertEquals(host.player2!!.x, guest.player2!!.x)
        assertEquals(100, guest.scoreOf(1))
        assertEquals(host.livesOf(1), guest.livesOf(1))
        assertEquals(host.enemiesRemaining, guest.enemiesRemaining)
        assertEquals(host.totalEnemies, guest.totalEnemies)
        assertEquals(host.state, guest.state)
        assertEquals(host.tick, guest.tick)
    }

    @Test
    fun `a dead host player arrives dead and without its bullets`() {
        val host = twoPlayerWorld()
        val guest = twoPlayerWorld()
        host.player.shieldTicks = 0
        host.bullets += enemyBullet(host, x = host.player.x - 64, y = host.player.y)

        host.step(InputFrame(PlayerInput.NONE, PlayerInput.NONE))
        assertFalse(host.player.alive)

        val snapshot = host.captureSnapshot()
        guest.applySnapshot(snapshot)

        assertFalse(guest.player.alive)
        assertTrue(guest.bullets.isEmpty())
    }

    @Test
    fun `collected clock and shovel timers arrive intact`() {
        val host = twoPlayerWorld()
        val guest = twoPlayerWorld()
        host.player.shieldTicks = 0

        host.debugSpawnPowerUp(PowerUpType.CLOCK)
        host.player.x = assertNotNull(host.powerUp).x * Fixed.SUBPIXELS
        host.player.y = host.powerUp!!.y * Fixed.SUBPIXELS
        host.step(InputFrame(PlayerInput.NONE, PlayerInput.NONE))
        assertTrue(host.freezeTicks > 0)

        host.debugSpawnPowerUp(PowerUpType.SHOVEL)
        host.player.x = assertNotNull(host.powerUp).x * Fixed.SUBPIXELS
        host.player.y = host.powerUp!!.y * Fixed.SUBPIXELS
        host.step(InputFrame(PlayerInput.NONE, PlayerInput.NONE))
        assertTrue(host.shovelTicks > 0)

        val snapshot = host.captureSnapshot()
        guest.applySnapshot(snapshot)

        assertEquals(host.freezeTicks, guest.freezeTicks)
        assertEquals(host.shovelTicks, guest.shovelTicks)
        assertEquals(snapshot.eagleAlive, guest.eagle.alive)
        assertNull(guest.powerUp)
    }

    @Test
    fun `applying a snapshot replaces stale enemies`() {
        val host = twoPlayerWorld()
        val guest = twoPlayerWorld()
        enemy(guest, 100, 100, EnemyType.BASIC, hp = 1)
        enemy(guest, 200, 100, EnemyType.BASIC, hp = 1)
        enemy(host, 300, 300, EnemyType.FAST, hp = 1)

        guest.applySnapshot(host.captureSnapshot())

        assertEquals(1, guest.tanks.count { !it.isPlayer })
        assertEquals(EnemyType.FAST, guest.tanks.first { !it.isPlayer }.type)
    }

    @Test
    fun `a snapshot for the wrong player count is refused`() {
        val one = World(TerrainGrid.empty(), playerCount = 1)
        val two = World(TerrainGrid.empty(), playerCount = 2)

        val error = runCatching { one.applySnapshot(two.captureSnapshot()) }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun `terrain changes drain once and apply to the guest`() {
        val host = twoPlayerWorld()
        val guest = twoPlayerWorld()
        host.terrain.set8(3, 4, Terrain.BRICK)
        host.terrain.set(7, 8, Terrain.STEEL)

        val changes = host.terrain.drainChanges()
        assertEquals(4, changes.size)
        assertTrue(host.terrain.drainChanges().isEmpty())

        for (index in changes) {
            guest.terrain.setAt(index, host.terrain.terrainAt(index))
        }

        assertEquals(host.terrain.contentHash(), guest.terrain.contentHash())
        assertEquals(Terrain.BRICK, guest.terrain.get(6, 8))
        assertEquals(Terrain.STEEL, guest.terrain.get(7, 8))
    }

    @Test
    fun `a destroyed brick reaches the guest as empty cells`() {
        val host = twoPlayerWorld()
        val guest = twoPlayerWorld()
        host.terrain.set8(10, 10, Terrain.BRICK)

        host.terrain.destroyBrick(20, 20, Dir.LEFT)

        for (index in host.terrain.drainChanges()) {
            guest.terrain.setAt(index, host.terrain.terrainAt(index))
        }

        assertEquals(Terrain.EMPTY, guest.terrain.get(20, 20))
        assertEquals(Terrain.EMPTY, guest.terrain.get(20, 21))
        assertEquals(Terrain.BRICK, guest.terrain.get(21, 20))
    }

    private fun twoPlayerWorld(): World = World(
        terrain = TerrainGrid.empty(),
        seed = 1,
        playerCount = 2,
        enemyRoster = listOf(
            EnemySpawn(EnemyType.BASIC, false),
            EnemySpawn(EnemyType.FAST, false),
        ),
    )

    private fun enemy(world: World, x: Int, y: Int, type: EnemyType, hp: Int): Tank =
        Tank(
            x = x,
            y = y,
            isPlayer = false,
            type = type,
            hp = hp,
        ).apply {
            aiEnabled = false
            shieldTicks = 0
        }.also { world.tanks += it }

    private fun enemyBullet(world: World, x: Int, y: Int): Bullet {
        val owner = world.tanks.firstOrNull { !it.isPlayer } ?: enemy(
            world,
            0,
            0,
            EnemyType.BASIC,
            hp = 1,
        )
        return Bullet(x, y, Dir.RIGHT, owner)
    }

}
