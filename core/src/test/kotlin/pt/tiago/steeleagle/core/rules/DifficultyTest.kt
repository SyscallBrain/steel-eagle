package pt.tiago.steeleagle.core.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DifficultyTest {

    @Test
    fun `initial lives match the plan`() {
        assertEquals(5, Difficulty.EASY.initialLives)
        assertEquals(3, Difficulty.NORMAL.initialLives)
        assertEquals(2, Difficulty.HARD.initialLives)
    }

    @Test
    fun `enemy speed multipliers match the plan`() {
        assertEquals(0.85f, Difficulty.EASY.enemySpeedMultiplier, 0.0001f)
        assertEquals(1.0f, Difficulty.NORMAL.enemySpeedMultiplier, 0.0001f)
        assertEquals(1.15f, Difficulty.HARD.enemySpeedMultiplier, 0.0001f)
    }

    @Test
    fun `fire multipliers match the plan`() {
        assertEquals(0.7f, Difficulty.EASY.fireChanceMultiplier, 0.0001f)
        assertEquals(1.0f, Difficulty.NORMAL.fireChanceMultiplier, 0.0001f)
        assertEquals(1.4f, Difficulty.HARD.fireChanceMultiplier, 0.0001f)
    }

    @Test
    fun `eagle weight rises with difficulty`() {
        assertTrue(Difficulty.EASY.eagleWeight < Difficulty.NORMAL.eagleWeight)
        assertTrue(Difficulty.NORMAL.eagleWeight < Difficulty.HARD.eagleWeight)
    }

    @Test
    fun `spawn interval multipliers match the plan`() {
        assertEquals(1.25f, Difficulty.EASY.spawnIntervalMultiplier, 0.0001f)
        assertEquals(1.0f, Difficulty.NORMAL.spawnIntervalMultiplier, 0.0001f)
        assertEquals(0.8f, Difficulty.HARD.spawnIntervalMultiplier, 0.0001f)
    }

    @Test
    fun `power up duration multipliers match the plan`() {
        assertEquals(1.2f, Difficulty.EASY.powerUpDurationMultiplier, 0.0001f)
        assertEquals(1.0f, Difficulty.NORMAL.powerUpDurationMultiplier, 0.0001f)
        assertEquals(0.85f, Difficulty.HARD.powerUpDurationMultiplier, 0.0001f)
    }
}
