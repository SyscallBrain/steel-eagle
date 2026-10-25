package pt.tiago.steeleagle.core.rules

import kotlin.test.Test
import kotlin.test.assertEquals

class ScoringTest {

    @Test
    fun `points match the enemy type`() {
        assertEquals(100, Scoring.pointsFor(EnemyType.BASIC))
        assertEquals(200, Scoring.pointsFor(EnemyType.FAST))
        assertEquals(300, Scoring.pointsFor(EnemyType.POWER))
        assertEquals(400, Scoring.pointsFor(EnemyType.ARMORED))
    }

    @Test
    fun `extra life step is twenty thousand`() {
        assertEquals(20_000, Scoring.EXTRA_LIFE_STEP)
    }

    @Test
    fun `power up is worth five hundred`() {
        assertEquals(500, Scoring.POWER_UP)
    }
}
