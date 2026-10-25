package pt.tiago.steeleagle.flow

import pt.tiago.steeleagle.core.rules.EnemyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TallySummary2PTest {

    @Test
    fun `breaks kills into two columns and bonuses the kill leader`() {
        val killsP1 = IntArray(EnemyType.entries.size)
        val scoresP1 = IntArray(EnemyType.entries.size)
        killsP1[EnemyType.BASIC.ordinal] = 4
        scoresP1[EnemyType.BASIC.ordinal] = 400

        val killsP2 = IntArray(EnemyType.entries.size)
        val scoresP2 = IntArray(EnemyType.entries.size)
        killsP2[EnemyType.BASIC.ordinal] = 2
        scoresP2[EnemyType.BASIC.ordinal] = 200

        val tally = TallySummary2P.from(
            killsP1, scoresP1, killsP2, scoresP2,
            levelScoreP1 = 400,
            levelScoreP2 = 200,
        )

        assertEquals(0, tally.winner)
        assertEquals(4, tally.players[0].totalKills)
        assertEquals(1_400, tally.players[0].total)
        assertEquals(2, tally.players[1].totalKills)
        assertEquals(200, tally.players[1].total)
        assertEquals(TallySummary2P.BONUS, tally.players[0].bonus)
        assertEquals(0, tally.players[1].bonus)
    }

    @Test
    fun `a kill tie gives no bonus`() {
        val kills = IntArray(EnemyType.entries.size)
        kills[EnemyType.BASIC.ordinal] = 2
        val scores = IntArray(EnemyType.entries.size)
        scores[EnemyType.BASIC.ordinal] = 200

        val tally = TallySummary2P.from(
            kills, scores, kills, scores,
            levelScoreP1 = 200,
            levelScoreP2 = 200,
        )

        assertNull(tally.winner)
        assertEquals(0, tally.players[0].bonus)
        assertEquals(0, tally.players[1].bonus)
    }

    @Test
    fun `power-up points appear as other`() {
        val kills = IntArray(EnemyType.entries.size)
        val scores = IntArray(EnemyType.entries.size)
        kills[EnemyType.BASIC.ordinal] = 1
        scores[EnemyType.BASIC.ordinal] = 100

        val tally = TallySummary2P.from(
            kills, scores, kills, scores,
            levelScoreP1 = 600,
            levelScoreP2 = 100,
        )

        assertEquals(500, tally.players[0].other)
        assertEquals(0, tally.players[1].other)
    }

    @Test
    fun `negative level scores are clamped`() {
        val empty = IntArray(EnemyType.entries.size)

        val tally = TallySummary2P.from(
            empty, empty, empty, empty,
            levelScoreP1 = -10,
            levelScoreP2 = 0,
        )

        assertEquals(0, tally.players[0].total)
        assertEquals(0, tally.players[0].other)
    }
}
