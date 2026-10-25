package pt.tiago.steeleagle.core.game

import pt.tiago.steeleagle.core.level.Level
import pt.tiago.steeleagle.core.model.Dir
import pt.tiago.steeleagle.core.model.PlayerInput
import pt.tiago.steeleagle.core.world.Bullet
import pt.tiago.steeleagle.core.world.InputFrame
import pt.tiago.steeleagle.core.world.Tank
import pt.tiago.steeleagle.core.world.World
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GameSessionTest {

    private fun level(name: String) = Level(
        name = name,
        enemies = "",
        map = List(26) { ".".repeat(26) },
    )

    private val level = level("test")

    @Test
    fun `next level carries score and lives`() {
        val session = GameSession(level, seed = 1)
        session.world.score = 1_234
        session.world.lives = 2

        session.nextLevel()

        assertEquals(1_234, session.score)
        assertEquals(2, session.lives)
        assertEquals(1_234, session.world.score)
        assertEquals(2, session.world.lives)
        assertEquals(1, session.cycle)
        assertEquals(1, session.levelNumber)
    }

    @Test
    fun `the star level survives a level clear`() {
        val session = GameSession(level, seed = 1)
        session.world.player.weaponLevel = 2

        session.nextLevel()

        assertEquals(2, session.weaponLevelOf(0))
        assertEquals(2, session.world.player.weaponLevel)
        assertEquals(World.FAST_BULLET_SPEED, session.world.player.bulletSpeed)
    }

    @Test
    fun `a player who died on the stage starts the next one without the star`() {
        val session = GameSession(level, seed = 1)
        session.world.player.weaponLevel = 3
        session.world.player.alive = false

        session.nextLevel()

        assertEquals(0, session.world.player.weaponLevel)
    }

    @Test
    fun `restart resets the session`() {
        val session = GameSession(level, seed = 1)
        session.world.score = 5_000
        session.world.lives = 1
        session.nextLevel()

        session.restart()

        assertEquals(0, session.score)
        assertEquals(3, session.lives)
        assertEquals(0, session.cycle)
        assertEquals(0, session.world.score)
        assertEquals(3, session.world.lives)
    }

    @Test
    fun `extra life carries across a level clear`() {
        val session = GameSession(level, seed = 1)
        session.world.score = 19_950
        val enemy = pt.tiago.steeleagle.core.world.Tank(
            x = 100 * 16,
            y = 100 * 16,
            isPlayer = false,
            type = pt.tiago.steeleagle.core.rules.EnemyType.BASIC,
            hp = 1,
        ).apply { aiEnabled = false }
        session.world.tanks += enemy
        session.world.bullets += pt.tiago.steeleagle.core.world.Bullet(
            x = 96 * 16,
            y = 100 * 16,
            dir = pt.tiago.steeleagle.core.model.Dir.RIGHT,
            owner = session.world.player,
        )

        session.world.step(
            pt.tiago.steeleagle.core.world.InputFrame(
                pt.tiago.steeleagle.core.model.PlayerInput.NONE,
            ),
        )

        assertEquals(20_050, session.world.score)
        assertEquals(4, session.world.lives)

        session.nextLevel()

        assertEquals(20_050, session.score)
        assertEquals(4, session.lives)
        assertEquals(20_050, session.world.score)
        assertEquals(4, session.world.lives)
        assertEquals(40_000, session.nextExtraLifeScore)
    }

    @Test
    fun `campaign advances levels and then cycles`() {
        val session = GameSession(
            levels = listOf(level("one"), level("two")),
            seed = 1,
            startLevelIndex = 0,
        )
        assertEquals(1, session.levelNumber)
        assertEquals("one", session.level.name)

        session.nextLevel()

        assertEquals(2, session.levelNumber)
        assertEquals("two", session.level.name)
        assertEquals(0, session.cycle)

        session.nextLevel()

        assertEquals(1, session.levelNumber)
        assertEquals("one", session.level.name)
        assertEquals(1, session.cycle)
    }

    @Test
    fun `two player session carries both scores and lives`() {
        val session = GameSession(
            levels = listOf(level("one")),
            seed = 1,
            difficulty = pt.tiago.steeleagle.core.rules.Difficulty.NORMAL,
            playerCount = 2,
        )
        session.world.score = 1_200
        session.world.addScore(1, 800)

        session.nextLevel()

        assertEquals(2, session.playerCount)
        assertEquals(1_200, session.scoreOf(0))
        assertEquals(800, session.scoreOf(1))
        assertEquals(3, session.livesOf(0))
        assertEquals(3, session.livesOf(1))
        assertEquals(2, session.world.playerCount)
        assertEquals(1_200, session.world.scoreOf(0))
        assertEquals(800, session.world.scoreOf(1))
    }

    @Test
    fun `two player session carries player two lives across levels`() {
        val session = GameSession(
            levels = listOf(level("one")),
            seed = 1,
            playerCount = 2,
        )
        session.world.player2!!.shieldTicks = 0
        val enemy = Tank(x = 0, y = 0, isPlayer = false).apply { aiEnabled = false }
        session.world.bullets += Bullet(
            x = session.world.player2!!.x - 64,
            y = session.world.player2!!.y,
            dir = Dir.RIGHT,
            owner = enemy,
        )
        session.world.step(InputFrame(PlayerInput.NONE, PlayerInput.NONE))
        assertEquals(2, session.world.livesOf(1))

        session.nextLevel()

        assertEquals(2, session.livesOf(1))
        assertEquals(2, session.world.livesOf(1))
        assertEquals(3, session.livesOf(0))
        assertTrue(session.world.player.alive)
        assertTrue(session.world.player2!!.alive)
    }

    @Test
    fun `a level clear bonus can grant an extra life and carries`() {
        val session = GameSession(
            levels = listOf(level("one")),
            seed = 1,
            playerCount = 2,
        )

        session.world.addScore(1, 20_000)
        session.syncFromWorld()
        session.nextLevel()

        assertEquals(20_000, session.scoreOf(1))
        assertEquals(4, session.livesOf(1))
        assertEquals(40_000, session.nextExtraLifeScoreOf(1))
        assertEquals(0, session.scoreOf(0))
        assertEquals(3, session.livesOf(0))
        assertEquals(20_000, session.world.scoreOf(1))
        assertEquals(4, session.world.livesOf(1))
    }
}
