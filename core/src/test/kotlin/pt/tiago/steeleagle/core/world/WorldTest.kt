package pt.tiago.steeleagle.core.world

import pt.tiago.steeleagle.core.model.Dir
import pt.tiago.steeleagle.core.model.Fixed
import pt.tiago.steeleagle.core.model.PlayerInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorldTest {

    @Test
    fun `player moves in the input direction`() {
        val world = emptyWorld()
        val startX = world.player.x

        step(world, PlayerInput(Dir.RIGHT, fire = false))

        assertEquals(startX + 12, world.player.x)
        assertEquals(Dir.RIGHT, world.player.dir)
    }

    @Test
    fun `player stops at the field edge`() {
        val world = emptyWorld(spawnPixelX = 192)

        repeat(5) { step(world, PlayerInput(Dir.RIGHT, fire = false)) }

        assertEquals(192 * Fixed.SUBPIXELS, world.player.x)
    }

    @Test
    fun `turning 90 degrees snaps to the nearest 8 px`() {
        val world = emptyWorld()
        world.player.x = 100
        world.player.dir = Dir.RIGHT

        step(world, PlayerInput(Dir.UP, fire = false))

        assertEquals(128, world.player.x)
        assertEquals(Dir.UP, world.player.dir)
    }

    @Test
    fun `reversing does not snap`() {
        val world = emptyWorld()
        world.player.x = 100
        world.player.dir = Dir.RIGHT

        step(world, PlayerInput(Dir.LEFT, fire = false))

        assertEquals(88, world.player.x)
    }

    @Test
    fun `tanks block each other`() {
        val world = emptyWorld()
        world.tanks += Tank(
            x = 80 * Fixed.SUBPIXELS,
            y = 192 * Fixed.SUBPIXELS,
        )

        step(world, PlayerInput(Dir.RIGHT, fire = false))

        assertEquals(64 * Fixed.SUBPIXELS, world.player.x)
    }

    @Test
    fun `water blocks tanks`() {
        val terrain = TerrainGrid.empty()
        terrain.set8(3, 24, Terrain.WATER)
        val world = World(terrain, spawnPixelX = 40, spawnPixelY = 192)

        repeat(20) { step(world, PlayerInput(Dir.LEFT, fire = false)) }

        assertEquals(32 * Fixed.SUBPIXELS, world.player.x)
    }

    @Test
    fun `brick hit removes 8 by 4 and stops the bullet`() {
        val terrain = TerrainGrid.empty()
        terrain.set8(10, 10, Terrain.BRICK)
        val world = World(terrain)
        world.bullets += Bullet(
            x = 76 * Fixed.SUBPIXELS,
            y = 80 * Fixed.SUBPIXELS,
            dir = Dir.RIGHT,
            owner = world.player,
        )

        step(world)

        assertEquals(Terrain.EMPTY, terrain.get(20, 20))
        assertEquals(Terrain.EMPTY, terrain.get(20, 21))
        assertEquals(Terrain.BRICK, terrain.get(21, 20))
        assertEquals(Terrain.BRICK, terrain.get(21, 21))
        assertTrue(world.bullets.isEmpty())
    }

    @Test
    fun `steel is indestructible without star three`() {
        val terrain = TerrainGrid.empty()
        terrain.set8(10, 10, Terrain.STEEL)
        val world = World(terrain)
        world.bullets += Bullet(
            x = 76 * Fixed.SUBPIXELS,
            y = 80 * Fixed.SUBPIXELS,
            dir = Dir.RIGHT,
            owner = world.player,
        )

        step(world)

        assertEquals(Terrain.STEEL, terrain.get(20, 20))
        assertTrue(world.bullets.isEmpty())
    }

    @Test
    fun `star three destroys steel`() {
        val terrain = TerrainGrid.empty()
        terrain.set8(10, 10, Terrain.STEEL)
        val world = World(terrain)
        world.player.weaponLevel = 3
        world.bullets += Bullet(
            x = 76 * Fixed.SUBPIXELS,
            y = 80 * Fixed.SUBPIXELS,
            dir = Dir.RIGHT,
            owner = world.player,
        )

        step(world)

        for (y in 20..21) {
            for (x in 20..21) {
                assertEquals(Terrain.EMPTY, terrain.get(x, y))
            }
        }
    }

    @Test
    fun `bullets pass trees without star three`() {
        val terrain = TerrainGrid.empty()
        terrain.set8(10, 10, Terrain.TREES)
        val world = World(terrain)
        world.bullets += Bullet(
            x = 76 * Fixed.SUBPIXELS,
            y = 80 * Fixed.SUBPIXELS,
            dir = Dir.RIGHT,
            owner = world.player,
        )

        step(world)

        assertEquals(Terrain.TREES, terrain.get(20, 20))
        assertEquals(1, world.bullets.size)
        assertEquals(80 * Fixed.SUBPIXELS, world.bullets[0].x)
    }

    @Test
    fun `star three destroys trees`() {
        val terrain = TerrainGrid.empty()
        terrain.set8(10, 10, Terrain.TREES)
        val world = World(terrain)
        world.player.weaponLevel = 3
        world.bullets += Bullet(
            x = 76 * Fixed.SUBPIXELS,
            y = 80 * Fixed.SUBPIXELS,
            dir = Dir.RIGHT,
            owner = world.player,
        )

        step(world)

        assertEquals(Terrain.EMPTY, terrain.get(20, 20))
        assertTrue(world.bullets.isEmpty())
    }

    @Test
    fun `bullets annul each other`() {
        val world = emptyWorld()
        val enemy = Tank(x = 0, y = 0, isPlayer = false)
        world.bullets += Bullet(
            x = 100 * Fixed.SUBPIXELS,
            y = 100 * Fixed.SUBPIXELS,
            dir = Dir.RIGHT,
            owner = world.player,
        )
        world.bullets += Bullet(
            x = 108 * Fixed.SUBPIXELS,
            y = 100 * Fixed.SUBPIXELS,
            dir = Dir.LEFT,
            owner = enemy,
        )

        step(world)

        assertTrue(world.bullets.isEmpty())
    }

    @Test
    fun `ice keeps the tank sliding for one block after release`() {
        val terrain = TerrainGrid.empty()
        for (blockY in 8..9) {
            for (blockX in 0 until 26) {
                terrain.set8(blockX, blockY, Terrain.ICE)
            }
        }
        val world = World(terrain, spawnPixelX = 64, spawnPixelY = 64)

        repeat(5) { step(world, PlayerInput(Dir.RIGHT, fire = false)) }
        val beforeRelease = world.player.x

        repeat(40) { step(world) }

        assertEquals(16 * Fixed.SUBPIXELS, world.player.x - beforeRelease)
        assertEquals(0, world.player.slideRemaining)
    }

    @Test
    fun `shooting the eagle ends the game`() {
        val world = emptyWorld()
        world.bullets += Bullet(
            x = 96 * Fixed.SUBPIXELS,
            y = 184 * Fixed.SUBPIXELS,
            dir = Dir.DOWN,
            owner = world.player,
        )

        repeat(2) { step(world) }

        assertFalse(world.eagle.alive)
        assertEquals(WorldState.GAME_OVER, world.state)
        assertTrue(world.bullets.isEmpty())
    }

    @Test
    fun `only one player bullet exists at a time`() {
        val world = emptyWorld()

        repeat(3) { step(world, PlayerInput(Dir.UP, fire = true)) }

        assertEquals(1, world.bullets.size)
    }

    @Test
    fun `flush shot destroys the near brick layer`() {
        val terrain = TerrainGrid.empty()
        terrain.set8(11, 24, Terrain.BRICK)
        val world = World(terrain)
        world.player.x = 72 * Fixed.SUBPIXELS
        world.player.y = 192 * Fixed.SUBPIXELS
        world.player.dir = Dir.RIGHT

        step(world, PlayerInput(Dir.RIGHT, fire = true))

        assertEquals(Terrain.EMPTY, terrain.get(22, 48))
        assertEquals(Terrain.EMPTY, terrain.get(22, 49))
        assertEquals(Terrain.BRICK, terrain.get(23, 48))
        assertEquals(Terrain.BRICK, terrain.get(23, 49))
        assertTrue(world.bullets.isEmpty())
    }

    @Test
    fun `annulling bullets do not destroy the eagle`() {
        val world = emptyWorld()
        val enemy = Tank(x = 0, y = 0, isPlayer = false)
        world.bullets += Bullet(
            x = 92 * Fixed.SUBPIXELS,
            y = 200 * Fixed.SUBPIXELS,
            dir = Dir.RIGHT,
            owner = world.player,
        )
        world.bullets += Bullet(
            x = 96 * Fixed.SUBPIXELS,
            y = 200 * Fixed.SUBPIXELS,
            dir = Dir.LEFT,
            owner = enemy,
        )

        step(world)

        assertTrue(world.bullets.isEmpty())
        assertTrue(world.eagle.alive)
        assertEquals(WorldState.PLAYING, world.state)
    }

    @Test
    fun `the eagle blocks tanks`() {
        val world = emptyWorld()
        world.player.x = 80 * Fixed.SUBPIXELS

        step(world, PlayerInput(Dir.RIGHT, fire = false))

        assertEquals(80 * Fixed.SUBPIXELS, world.player.x)
    }

    @Test
    fun `alignment is reverted when the snapped position is blocked`() {
        val terrain = TerrainGrid.empty()
        terrain.set(5, 48, Terrain.BRICK)
        val world = World(terrain)
        world.player.x = 4 * Fixed.SUBPIXELS
        world.player.y = 192 * Fixed.SUBPIXELS
        world.player.dir = Dir.RIGHT

        step(world, PlayerInput(Dir.UP, fire = false))

        assertEquals(4 * Fixed.SUBPIXELS, world.player.x)
    }

    @Test
    fun `ice slide stops at a wall`() {
        val terrain = TerrainGrid.empty()
        for (blockX in 0 until 26) {
            terrain.set8(blockX, 8, Terrain.ICE)
            terrain.set8(blockX, 9, Terrain.ICE)
        }
        terrain.set8(12, 9, Terrain.BRICK)
        val world = World(terrain, spawnPixelX = 64, spawnPixelY = 64)

        repeat(5) { step(world, PlayerInput(Dir.RIGHT, fire = false)) }
        repeat(40) { step(world) }

        assertEquals(80 * Fixed.SUBPIXELS, world.player.x)
        assertEquals(0, world.player.slideRemaining)
    }

    @Test
    fun `ice does not slide when the tank was not on ice`() {
        val world = emptyWorld()
        repeat(5) { step(world, PlayerInput(Dir.RIGHT, fire = false)) }
        val beforeRelease = world.player.x

        repeat(20) { step(world) }

        assertEquals(beforeRelease, world.player.x)
    }

    @Test
    fun `parallel bullets do not annul`() {
        val world = emptyWorld()
        val enemy = Tank(x = 0, y = 0, isPlayer = false)
        world.bullets += Bullet(
            x = 100 * Fixed.SUBPIXELS,
            y = 100 * Fixed.SUBPIXELS,
            dir = Dir.RIGHT,
            owner = world.player,
        )
        world.bullets += Bullet(
            x = 104 * Fixed.SUBPIXELS,
            y = 100 * Fixed.SUBPIXELS,
            dir = Dir.RIGHT,
            owner = enemy,
        )

        step(world)

        assertEquals(2, world.bullets.size)
        assertEquals(104 * Fixed.SUBPIXELS, world.bullets[0].x)
        assertEquals(108 * Fixed.SUBPIXELS, world.bullets[1].x)
    }

    @Test
    fun `bullet leaving the field is removed`() {
        val world = emptyWorld()
        world.bullets += Bullet(
            x = 206 * Fixed.SUBPIXELS,
            y = 100 * Fixed.SUBPIXELS,
            dir = Dir.RIGHT,
            owner = world.player,
        )

        step(world)

        assertTrue(world.bullets.isEmpty())
    }

    @Test
    fun `weapon level two allows two bullets`() {
        val world = emptyWorld()
        world.player.weaponLevel = 2

        step(world, PlayerInput(Dir.UP, fire = true))
        step(world, PlayerInput(Dir.UP, fire = true))

        assertEquals(2, world.bullets.size)
    }

    @Test
    fun `same seed and inputs give the same snapshot`() {
        val first = World(brickTerrain(), seed = 99, spawnPixelX = 16, spawnPixelY = 16)
        val second = World(brickTerrain(), seed = 99, spawnPixelX = 16, spawnPixelY = 16)
        val inputs = listOf(
            InputFrame(PlayerInput(Dir.RIGHT, fire = false)),
            InputFrame(PlayerInput(Dir.RIGHT, fire = true)),
            InputFrame(PlayerInput(Dir.UP, fire = false)),
            InputFrame(PlayerInput(Dir.LEFT, fire = true)),
            InputFrame(PlayerInput.NONE),
        )

        repeat(120) { index ->
            val input = inputs[index % inputs.size]
            first.step(input)
            second.step(input)
        }

        assertEquals(WorldState.PLAYING, first.state)
        assertEquals(first.snapshot(), second.snapshot())
        assertTrue(first.terrain.get(20, 4) == Terrain.EMPTY)
    }

    private fun brickTerrain(): TerrainGrid = TerrainGrid.empty().apply {
        set8(10, 2, Terrain.BRICK)
    }

    private fun emptyWorld(
        spawnPixelX: Int = 64,
        spawnPixelY: Int = 192,
        seed: Int = 1,
    ): World = World(
        terrain = TerrainGrid.empty(),
        seed = seed,
        spawnPixelX = spawnPixelX,
        spawnPixelY = spawnPixelY,
    )

    private fun step(world: World, input: PlayerInput = PlayerInput.NONE) {
        world.step(InputFrame(input))
    }
}
