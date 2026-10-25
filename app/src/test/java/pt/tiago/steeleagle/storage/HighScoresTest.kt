package pt.tiago.steeleagle.storage

import pt.tiago.steeleagle.core.rules.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HighScoresTest {

    @Test
    fun `empty table qualifies positive scores`() {
        val scores = HighScores()

        assertTrue(scores.qualifies(Difficulty.NORMAL, 100))
        assertFalse(scores.qualifies(Difficulty.NORMAL, 0))
    }

    @Test
    fun `adds entries sorted and per difficulty`() {
        val scores = HighScores()

        assertTrue(scores.add(Difficulty.NORMAL, "abc", 500))
        assertTrue(scores.add(Difficulty.NORMAL, "def", 900))
        assertTrue(scores.add(Difficulty.EASY, "ghi", 100))

        assertEquals(
            listOf(900, 500),
            scores.entries(Difficulty.NORMAL).map { it.score },
        )
        assertEquals(listOf(100), scores.entries(Difficulty.EASY).map { it.score })
        assertEquals("ABC", scores.entries(Difficulty.NORMAL).last().display)
        assertEquals(900, scores.bestScore())
    }

    @Test
    fun `keeps only the top ten`() {
        val scores = HighScores()
        for (score in 1..12) {
            scores.add(Difficulty.NORMAL, "AAA", score * 100)
        }

        val table = scores.entries(Difficulty.NORMAL)
        assertEquals(10, table.size)
        assertEquals(1_200, table.first().score)
        assertEquals(300, table.last().score)
        assertFalse(scores.qualifies(Difficulty.NORMAL, 300))
        assertTrue(scores.qualifies(Difficulty.NORMAL, 301))
    }

    @Test
    fun `serializes and parses the tables`() {
        val scores = HighScores()
        scores.add(Difficulty.HARD, "xyz", 4_200)
        scores.add(Difficulty.EASY, "abc", 100)

        val restored = HighScores.parse(scores.serialize())

        assertEquals(
            scores.entries(Difficulty.HARD).map { it.display to it.score },
            restored.entries(Difficulty.HARD).map { it.display to it.score },
        )
        assertEquals(
            scores.entries(Difficulty.EASY).map { it.display to it.score },
            restored.entries(Difficulty.EASY).map { it.display to it.score },
        )
    }

    @Test
    fun `blank or damaged text parses to empty tables`() {
        assertEquals(0, HighScores.parse("").bestScore())
        assertEquals(0, HighScores.parse("garbage||NOPE:1,2").bestScore())
    }
}
