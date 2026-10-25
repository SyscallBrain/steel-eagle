package pt.tiago.steeleagle.core.rules

import kotlin.test.Test
import kotlin.test.assertEquals

class SpawnDirectorTest {

    @Test
    fun `spawn interval follows level players and difficulty`() {
        assertEquals(186, SpawnDirector.spawnInterval(1, 1, Difficulty.NORMAL))
        assertEquals(232, SpawnDirector.spawnInterval(1, 1, Difficulty.EASY))
        assertEquals(148, SpawnDirector.spawnInterval(1, 1, Difficulty.HARD))
        assertEquals(60, SpawnDirector.spawnInterval(50, 1, Difficulty.NORMAL))
    }

    @Test
    fun `two players reduce the interval by twenty ticks`() {
        assertEquals(166, SpawnDirector.spawnInterval(1, 2, Difficulty.NORMAL))
    }

    @Test
    fun `max on screen is four for one player and six for two`() {
        assertEquals(4, SpawnDirector.maxOnScreen(1))
        assertEquals(6, SpawnDirector.maxOnScreen(2))
    }

    @Test
    fun `spawn points rotate starting at the centre`() {
        assertEquals(6 to 0, SpawnDirector.spawnPoint(0))
        assertEquals(0 to 0, SpawnDirector.spawnPoint(1))
        assertEquals(12 to 0, SpawnDirector.spawnPoint(2))
        assertEquals(6 to 0, SpawnDirector.spawnPoint(3))
    }

    @Test
    fun `director exposes the roster totals`() {
        val roster = EnemyRoster.parse("B*20", seed = 1)
        val director = SpawnDirector(roster, Difficulty.NORMAL, level = 1)

        assertEquals(20, director.total)
        assertEquals(20, director.remaining)
        assertEquals(0, director.defeated)
        assertEquals(4, director.maxOnScreen)
        assertEquals(186, director.spawnInterval)
    }
}
