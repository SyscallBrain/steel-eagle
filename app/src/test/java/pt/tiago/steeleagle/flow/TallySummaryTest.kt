package pt.tiago.steeleagle.flow

import pt.tiago.steeleagle.core.rules.EnemyType
import org.junit.Assert.assertEquals
import org.junit.Test

class TallySummaryTest {

    @Test
    fun `breaks kills into per type points and power-up bonus`() {
        val kills = IntArray(EnemyType.entries.size)
        kills[EnemyType.BASIC.ordinal] = 4
        kills[EnemyType.FAST.ordinal] = 2
        kills[EnemyType.POWER.ordinal] = 1
        kills[EnemyType.ARMORED.ordinal] = 1
        val scores = IntArray(EnemyType.entries.size)
        scores[EnemyType.BASIC.ordinal] = 400
        scores[EnemyType.FAST.ordinal] = 400
        scores[EnemyType.POWER.ordinal] = 300
        scores[EnemyType.ARMORED.ordinal] = 400

        val tally = TallySummary.from(kills, scores, levelScore = 1_600)

        assertEquals(4, tally.rows[0].kills)
        assertEquals(100, tally.rows[0].points)
        assertEquals(400, tally.rows[0].subtotal)
        assertEquals(400, tally.rows[1].subtotal)
        assertEquals(300, tally.rows[2].subtotal)
        assertEquals(400, tally.rows[3].subtotal)
        assertEquals(100, tally.bonus)
        assertEquals(1_600, tally.total)
    }

    @Test
    fun `grenade kills show kills but no points`() {
        val kills = IntArray(EnemyType.entries.size)
        kills[EnemyType.BASIC.ordinal] = 2

        val tally = TallySummary.from(
            kills,
            IntArray(EnemyType.entries.size),
            levelScore = 500,
        )

        assertEquals(2, tally.rows[0].kills)
        assertEquals(0, tally.rows[0].subtotal)
        assertEquals(500, tally.bonus)
        assertEquals(500, tally.total)
    }

    @Test
    fun `negative or low scores do not produce a negative bonus`() {
        val tally = TallySummary.from(
            IntArray(EnemyType.entries.size),
            IntArray(EnemyType.entries.size),
            levelScore = -5,
        )

        assertEquals(0, tally.total)
        assertEquals(0, tally.bonus)
        assertEquals(0, tally.rows.sumOf { it.subtotal })
    }
}
