package pt.tiago.steeleagle

import pt.tiago.steeleagle.core.level.LevelParser
import pt.tiago.steeleagle.core.model.Dir
import pt.tiago.steeleagle.core.model.PlayerInput
import pt.tiago.steeleagle.core.world.Bullet
import pt.tiago.steeleagle.core.world.InputFrame
import pt.tiago.steeleagle.core.world.Tank
import pt.tiago.steeleagle.core.world.World
import pt.tiago.steeleagle.core.world.WorldState
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelFlowTest {

    private val level = LevelParser.parse(File("src/main/assets/levels/001.txt").readText())

    @Test
    fun `level 001 can be cleared end to end`() {
        val world = World.fromLevel(level, seed = 1)
        var ticks = 0

        while (world.state == WorldState.PLAYING && ticks < 10_000) {
            for (enemy in world.tanks) {
                if (!enemy.isPlayer && enemy.alive) {
                    enemy.aiEnabled = false
                    world.bullets += Bullet(
                        x = enemy.x,
                        y = enemy.y,
                        dir = Dir.DOWN,
                        owner = world.player,
                    )
                }
            }
            world.step(InputFrame(PlayerInput.NONE))
            ticks++
        }

        assertEquals(WorldState.LEVEL_CLEAR, world.state)
        assertEquals(0, world.enemiesRemaining)
        assertTrue(world.score > 0)
        assertTrue(world.eagle.alive)
    }

    @Test
    fun `level 001 can be cleared by player two`() {
        val world = World.fromLevel(level, seed = 1, playerCount = 2)
        var ticks = 0

        while (world.state == WorldState.PLAYING && ticks < 10_000) {
            for (enemy in world.tanks) {
                if (!enemy.isPlayer && enemy.alive) {
                    enemy.aiEnabled = false
                    world.bullets += Bullet(
                        x = enemy.x,
                        y = enemy.y,
                        dir = Dir.DOWN,
                        owner = world.player2!!,
                    )
                }
            }
            world.step(InputFrame(PlayerInput.NONE, PlayerInput.NONE))
            ticks++
        }

        assertEquals(WorldState.LEVEL_CLEAR, world.state)
        assertEquals(20, world.totalKills(1))
        assertEquals(0, world.totalKills(0))
        assertTrue(world.scoreOf(1) > 0)
        assertTrue(world.eagle.alive)
    }

    @Test
    fun `enemy bullet destroys the eagle in level 001`() {
        val world = World.fromLevel(level, seed = 1)
        val enemy = Tank(x = 0, y = 0, isPlayer = false).apply { aiEnabled = false }
        world.bullets += Bullet(
            x = world.eagle.x,
            y = world.eagle.y,
            dir = Dir.DOWN,
            owner = enemy,
        )

        world.step(InputFrame(PlayerInput.NONE))

        assertEquals(WorldState.GAME_OVER, world.state)
        assertFalse(world.eagle.alive)
    }
}
