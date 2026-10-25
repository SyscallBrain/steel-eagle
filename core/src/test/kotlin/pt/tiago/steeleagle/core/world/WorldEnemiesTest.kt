package pt.tiago.steeleagle.core.world

import pt.tiago.steeleagle.core.model.Dir
import pt.tiago.steeleagle.core.model.Fixed
import pt.tiago.steeleagle.core.model.PlayerInput
import pt.tiago.steeleagle.core.rules.Difficulty
import pt.tiago.steeleagle.core.rules.EnemyRoster
import pt.tiago.steeleagle.core.rules.EnemySpawn
import pt.tiago.steeleagle.core.rules.EnemyType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WorldEnemiesTest {

    @Test
    fun `spawn director materialises an enemy after the animation`() {
        val world = World(
            terrain = fortifiedTerrain(),
            enemyRoster = listOf(EnemySpawn(EnemyType.BASIC, false)),
        )
        world.player.shieldTicks = 1_000_000

        repeat(260) { step(world) }

        val enemy = world.tanks.firstOrNull { !it.isPlayer && it.alive }
        assertNotNull(enemy)
        assertEquals(EnemyType.BASIC, enemy.type)
        assertEquals(96 * Fixed.SUBPIXELS, enemy.x)
    }

    @Test
    fun `at most four enemies are active with one player`() {
        val roster = List(6) { EnemySpawn(EnemyType.BASIC, false) }
        val world = World(fortifiedTerrain(), enemyRoster = roster)
        world.player.shieldTicks = 1_000_000

        repeat(2000) { step(world) }

        val active = world.tanks.count { !it.isPlayer && it.alive } +
            world.spawnAnimations.size
        assertEquals(4, active)
    }

    @Test
    fun `enemy bullet takes a life and the player respawns with a shield`() {
        val world = World(TerrainGrid.empty())
        world.player.shieldTicks = 0
        val enemy = enemyAt(0, 0)
        world.bullets += Bullet(
            x = 64 * Fixed.SUBPIXELS,
            y = 184 * Fixed.SUBPIXELS,
            dir = Dir.DOWN,
            owner = enemy,
        )

        repeat(2) { step(world) }

        assertEquals(2, world.lives)
        assertFalse(world.player.alive)
        assertTrue(world.respawnTicks > 0)

        repeat(60) { step(world) }

        assertTrue(world.player.alive)
        assertTrue(world.player.shieldTicks > 0)
        assertEquals(64 * Fixed.SUBPIXELS, world.player.x)
        assertEquals(192 * Fixed.SUBPIXELS, world.player.y)
    }

    @Test
    fun `shield absorbs an enemy bullet`() {
        val world = World(TerrainGrid.empty())
        world.player.shieldTicks = 100
        val enemy = enemyAt(0, 0)
        world.bullets += Bullet(
            x = 64 * Fixed.SUBPIXELS,
            y = 184 * Fixed.SUBPIXELS,
            dir = Dir.DOWN,
            owner = enemy,
        )

        repeat(2) { step(world) }

        assertTrue(world.player.alive)
        assertEquals(3, world.lives)
        assertTrue(world.bullets.isEmpty())
        assertTrue(world.player.shieldTicks < 100)
    }

    @Test
    fun `last life ends the game`() {
        val world = World(TerrainGrid.empty())
        world.lives = 1
        world.player.shieldTicks = 0
        val enemy = enemyAt(0, 0)
        world.bullets += Bullet(
            x = 64 * Fixed.SUBPIXELS,
            y = 184 * Fixed.SUBPIXELS,
            dir = Dir.DOWN,
            owner = enemy,
        )

        repeat(2) { step(world) }

        assertEquals(WorldState.GAME_OVER, world.state)
        assertEquals(0, world.lives)
        assertFalse(world.player.alive)
    }

    @Test
    fun `armored enemy needs four hits and clears a one enemy level`() {
        val world = World(
            TerrainGrid.empty(),
            enemyRoster = listOf(EnemySpawn(EnemyType.BASIC, false)),
        )
        world.player.shieldTicks = 1_000_000
        val armored = enemyAt(100, 100, EnemyType.ARMORED, hp = 4)
        world.tanks += armored

        repeat(4) { hit ->
            world.bullets += Bullet(
                x = 96 * Fixed.SUBPIXELS,
                y = 100 * Fixed.SUBPIXELS,
                dir = Dir.RIGHT,
                owner = world.player,
            )
            step(world)
            assertTrue(armored.alive || hit == 3)
        }

        assertFalse(armored.alive)
        assertEquals(400, world.score)
        assertEquals(WorldState.LEVEL_CLEAR, world.state)
        assertEquals(0, world.enemiesRemaining)
    }

    @Test
    fun `flashing armored drops one power up on the first hit`() {
        val world = World(TerrainGrid.empty())
        world.player.shieldTicks = 1_000_000
        val armored = enemyAt(100, 100, EnemyType.ARMORED, hp = 4, flashing = true)
        world.tanks += armored

        repeat(2) {
            world.bullets += Bullet(
                x = 96 * Fixed.SUBPIXELS,
                y = 100 * Fixed.SUBPIXELS,
                dir = Dir.RIGHT,
                owner = world.player,
            )
            step(world)
        }

        assertEquals(2, armored.hp)
        assertEquals(0, world.pendingPowerUpDrops)
        assertNotNull(world.powerUp)
        assertTrue(armored.droppedPowerUp)
        assertFalse(
            world.captureSnapshot().enemies.single().flashing,
            "a carrier stops flashing once its drop is out",
        )
    }

    @Test
    fun `an enemy turning into a corridor aligns to the grid`() {
        // A 16 px corridor at x 96..111 below the enemy, walled by steel.
        val terrain = TerrainGrid.empty().apply {
            for (cellY in 4..10) {
                set(23, cellY, Terrain.STEEL)
                set(28, cellY, Terrain.STEEL)
            }
        }
        val world = World(terrain)
        val enemy = enemyAt(98, 0, dir = Dir.RIGHT, speed = 8)
        world.tanks += enemy

        assertFalse(world.canMove(enemy, Dir.DOWN), "unaligned, the corridor is blocked")
        assertTrue(world.canTurn(enemy, Dir.DOWN))

        world.turnEnemy(enemy, Dir.DOWN)
        assertEquals(96 * Fixed.SUBPIXELS, enemy.x)
        assertEquals(Dir.DOWN, enemy.dir)
        repeat(60) { world.moveEnemy(enemy) }
        assertTrue(enemy.y > 16 * Fixed.SUBPIXELS, "the enemy drives into the corridor")
    }

    @Test
    fun `enemy bullets pass through other enemies`() {
        val world = World(TerrainGrid.empty())
        val shooter = enemyAt(100, 100)
        val target = enemyAt(116, 100)
        world.tanks += shooter
        world.tanks += target
        world.bullets += Bullet(
            x = 96 * Fixed.SUBPIXELS,
            y = 100 * Fixed.SUBPIXELS,
            dir = Dir.RIGHT,
            owner = shooter,
        )

        repeat(5) { step(world) }

        assertTrue(target.alive)
        assertEquals(1, target.hp)
        assertEquals(1, world.bullets.size)
        assertEquals(116 * Fixed.SUBPIXELS, world.bullets[0].x)
    }

    @Test
    fun `destroying all enemies clears the level`() {
        val world = World(
            TerrainGrid.empty(),
            enemyRoster = listOf(EnemySpawn(EnemyType.BASIC, false)),
        )
        world.player.shieldTicks = 1_000_000
        val enemy = enemyAt(100, 100)
        world.tanks += enemy
        world.bullets += Bullet(
            x = 96 * Fixed.SUBPIXELS,
            y = 100 * Fixed.SUBPIXELS,
            dir = Dir.RIGHT,
            owner = world.player,
        )

        step(world)

        assertFalse(enemy.alive)
        assertEquals(100, world.score)
        assertEquals(WorldState.LEVEL_CLEAR, world.state)
        assertEquals(0, world.enemiesRemaining)
    }

    @Test
    fun `extra life is awarded at twenty thousand points`() {
        val world = World(
            TerrainGrid.empty(),
            enemyRoster = listOf(EnemySpawn(EnemyType.BASIC, false)),
        )
        world.player.shieldTicks = 1_000_000
        world.score = 19_950
        val enemy = enemyAt(100, 100)
        world.tanks += enemy
        world.bullets += Bullet(
            x = 96 * Fixed.SUBPIXELS,
            y = 100 * Fixed.SUBPIXELS,
            dir = Dir.RIGHT,
            owner = world.player,
        )

        step(world)

        assertEquals(20_050, world.score)
        assertEquals(4, world.lives)
    }

    @Test
    fun `difficulty sets initial lives`() {
        assertEquals(5, World(TerrainGrid.empty(), difficulty = Difficulty.EASY).lives)
        assertEquals(3, World(TerrainGrid.empty(), difficulty = Difficulty.NORMAL).lives)
        assertEquals(2, World(TerrainGrid.empty(), difficulty = Difficulty.HARD).lives)
    }

    @Test
    fun `enemy brain moves an enemy`() {
        val world = World(TerrainGrid.empty())
        world.player.shieldTicks = 1_000_000
        val enemy = enemyAt(96, 0, speed = 8, aiEnabled = true)
        world.tanks += enemy

        step(world)

        assertTrue(enemy.x != 96 * Fixed.SUBPIXELS || enemy.y != 0)
    }

    @Test
    fun `tanks cannot move into a spawn animation`() {
        val world = World(
            fortifiedTerrain(),
            enemyRoster = listOf(EnemySpawn(EnemyType.BASIC, false)),
        )
        world.player.shieldTicks = 1_000_000
        repeat(190) { step(world) }
        val animation = world.spawnAnimations.first()
        val blocker = enemyAt(animation.blockX * 16, animation.blockY * 16 + 16, speed = 8)
        world.tanks += blocker

        assertFalse(world.canMove(blocker, Dir.UP))
    }

    @Test
    fun `spawn waits if a tank is already inside the star`() {
        val world = World(
            fortifiedTerrain(),
            enemyRoster = listOf(EnemySpawn(EnemyType.BASIC, false)),
        )
        world.player.shieldTicks = 1_000_000
        repeat(187) { step(world) }
        val animation = world.spawnAnimations.first()
        val blocker = enemyAt(animation.blockX * 16, animation.blockY * 16)
        world.tanks += blocker

        repeat(70) { step(world) }
        assertTrue(world.spawnAnimations.isNotEmpty())

        blocker.alive = false
        repeat(5) { step(world) }
        assertTrue(world.tanks.any { !it.isPlayer && it !== blocker && it.alive })
    }

    @Test
    fun `respawn waits while an enemy holds the spawn`() {
        val world = World(TerrainGrid.empty())
        world.player.shieldTicks = 0
        val enemy = enemyAt(0, 0)
        world.bullets += Bullet(
            x = 64 * Fixed.SUBPIXELS,
            y = 184 * Fixed.SUBPIXELS,
            dir = Dir.DOWN,
            owner = enemy,
        )
        repeat(2) { step(world) }
        assertFalse(world.player.alive)

        val blocker = enemyAt(64, 192)
        world.tanks += blocker
        repeat(60) { step(world) }
        assertFalse(world.player.alive)

        blocker.alive = false
        step(world)
        assertTrue(world.player.alive)
    }

    @Test
    fun `spawn director waits when all spawn points are blocked`() {
        val world = World(
            TerrainGrid.empty(),
            enemyRoster = listOf(EnemySpawn(EnemyType.BASIC, false)),
        )
        world.player.shieldTicks = 1_000_000
        world.tanks += enemyAt(0, 0)
        world.tanks += enemyAt(6 * 16, 0)
        world.tanks += enemyAt(12 * 16, 0)

        repeat(200) { step(world) }
        assertEquals(0, world.spawnDirector.spawnedCount)
        assertTrue(world.spawnAnimations.isEmpty())

        world.tanks[1].alive = false
        step(world)
        assertEquals(1, world.spawnDirector.spawnedCount)
    }

    @Test
    fun `spawn animation lasts sixty ticks`() {
        val world = World(
            fortifiedTerrain(),
            enemyRoster = listOf(EnemySpawn(EnemyType.BASIC, false)),
        )
        world.player.shieldTicks = 1_000_000
        repeat(187) { step(world) }
        assertEquals(59, world.spawnAnimations.first().ticksRemaining)

        repeat(59) { step(world) }
        assertTrue(world.spawnAnimations.isEmpty())
        assertTrue(world.tanks.any { !it.isPlayer && it.alive })
    }

    @Test
    fun `spawn points rotate centre left right`() {
        val roster = List(3) { EnemySpawn(EnemyType.BASIC, false) }
        val world = World(fortifiedTerrain(), enemyRoster = roster)
        world.player.shieldTicks = 1_000_000

        val first = waitForNextSpawn(world, previous = null)
        val second = waitForNextSpawn(world, previous = first)
        val third = waitForNextSpawn(world, previous = second)

        assertEquals(6, first.blockX)
        assertEquals(0, second.blockX)
        assertEquals(12, third.blockX)
    }

    @Test
    fun `no spawns after the roster is exhausted`() {
        val roster = List(2) { EnemySpawn(EnemyType.BASIC, false) }
        val world = World(fortifiedTerrain(), enemyRoster = roster)
        world.player.shieldTicks = 1_000_000

        val first = waitForNextSpawn(world, previous = null)
        waitForNextSpawn(world, previous = first)
        repeat(500) { step(world) }

        assertEquals(2, world.spawnDirector.spawnedCount)
        assertEquals(2, world.tanks.count { !it.isPlayer && it.alive })
    }

    @Test
    fun `player bullets do not hurt the player`() {
        val world = World(TerrainGrid.empty())
        world.player.shieldTicks = 0
        world.bullets += Bullet(
            x = world.player.x,
            y = world.player.y,
            dir = Dir.RIGHT,
            owner = world.player,
        )

        step(world)

        assertTrue(world.player.alive)
        assertEquals(3, world.lives)
        assertEquals(1, world.bullets.size)
    }

    @Test
    fun `enemy bullets from different enemies annul`() {
        val world = World(TerrainGrid.empty())
        val first = enemyAt(0, 0)
        val second = enemyAt(0, 16)
        world.tanks += first
        world.tanks += second
        world.bullets += Bullet(
            x = 100 * Fixed.SUBPIXELS,
            y = 100 * Fixed.SUBPIXELS,
            dir = Dir.RIGHT,
            owner = first,
        )
        world.bullets += Bullet(
            x = 108 * Fixed.SUBPIXELS,
            y = 100 * Fixed.SUBPIXELS,
            dir = Dir.LEFT,
            owner = second,
        )

        step(world)

        assertTrue(world.bullets.isEmpty())
    }

    @Test
    fun `flashing basic drops one power up on death`() {
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

        assertFalse(enemy.alive)
        assertEquals(0, world.pendingPowerUpDrops)
        assertNotNull(world.powerUp)
    }

    @Test
    fun `player bullets disappear when the player dies`() {
        val world = World(TerrainGrid.empty())
        world.player.shieldTicks = 0
        world.bullets += Bullet(
            x = 100 * Fixed.SUBPIXELS,
            y = 100 * Fixed.SUBPIXELS,
            dir = Dir.RIGHT,
            owner = world.player,
        )
        val enemy = enemyAt(0, 0)
        world.bullets += Bullet(
            x = 64 * Fixed.SUBPIXELS,
            y = 184 * Fixed.SUBPIXELS,
            dir = Dir.DOWN,
            owner = enemy,
        )

        repeat(2) { step(world) }

        assertFalse(world.player.alive)
        assertTrue(world.bullets.none { it.owner === world.player })
    }

    @Test
    fun `cycle increases enemy speed`() {
        val world = World(
            fortifiedTerrain(),
            enemyRoster = listOf(EnemySpawn(EnemyType.BASIC, false)),
            cycle = 1,
        )
        world.player.shieldTicks = 1_000_000

        repeat(260) { step(world) }

        val enemy = world.tanks.first { !it.isPlayer && it.alive }
        assertEquals(9, enemy.speed)
    }

    @Test
    fun `enemy world is deterministic with the same seed`() {
        val roster = EnemyRoster.parse("B*14 F*4 P*2", seed = 42)
        val first = World(fortifiedTerrain(), seed = 7, enemyRoster = roster)
        val second = World(fortifiedTerrain(), seed = 7, enemyRoster = roster)
        first.player.shieldTicks = 1_000_000
        second.player.shieldTicks = 1_000_000
        val inputs = listOf(
            PlayerInput.NONE,
            PlayerInput(Dir.LEFT, fire = false),
            PlayerInput(Dir.UP, fire = true),
            PlayerInput(Dir.RIGHT, fire = false),
        )

        repeat(500) { index ->
            val input = InputFrame(inputs[index % inputs.size])
            first.step(input)
            second.step(input)
        }

        assertEquals(first.snapshot(), second.snapshot())
    }

    @Test
    fun `enemy brain eventually fires`() {
        val world = World(TerrainGrid.empty())
        world.player.shieldTicks = 1_000_000
        val enemy = enemyAt(64, 0, speed = 0, aiEnabled = true)
        world.tanks += enemy

        var fired = false
        repeat(1000) {
            step(world)
            if (world.bullets.any { !it.owner.isPlayer }) fired = true
        }

        assertTrue(fired)
    }

    private fun waitForNextSpawn(world: World, previous: SpawnAnimation?): SpawnAnimation {
        var guard = 0
        if (previous != null) {
            while (world.spawnAnimations.contains(previous) && guard < 2000) {
                step(world)
                guard++
            }
        }
        while (world.spawnAnimations.isEmpty() && guard < 2000) {
            step(world)
            guard++
        }
        return world.spawnAnimations.first()
    }

    private fun step(world: World, input: PlayerInput = PlayerInput.NONE) {
        world.step(InputFrame(input))
    }

    private fun enemyAt(
        x: Int,
        y: Int,
        type: EnemyType = EnemyType.BASIC,
        hp: Int = type.hitPoints,
        flashing: Boolean = false,
        dir: Dir = Dir.DOWN,
        speed: Int = 0,
        aiEnabled: Boolean = false,
    ): Tank = Tank(
        x = x * Fixed.SUBPIXELS,
        y = y * Fixed.SUBPIXELS,
        dir = dir,
        isPlayer = false,
        speed = speed,
        type = type,
        hp = hp,
        flashing = flashing,
    ).apply {
        this.aiEnabled = aiEnabled
    }

    private fun fortifiedTerrain(): TerrainGrid = TerrainGrid.empty().apply {
        for (cellX in 22..29) {
            set(cellX, 46, Terrain.STEEL)
            set(cellX, 47, Terrain.STEEL)
        }
        for (cellY in 48..51) {
            set(22, cellY, Terrain.STEEL)
            set(23, cellY, Terrain.STEEL)
            set(28, cellY, Terrain.STEEL)
            set(29, cellY, Terrain.STEEL)
        }
    }
}
