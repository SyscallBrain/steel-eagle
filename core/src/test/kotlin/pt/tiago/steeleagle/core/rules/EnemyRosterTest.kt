package pt.tiago.steeleagle.core.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class EnemyRosterTest {

    @Test
    fun `expands the weighted spec into twenty enemies`() {
        val roster = EnemyRoster.parse("B*14 F*4 P*2", seed = 7)

        assertEquals(20, roster.size)
        assertEquals(14, roster.count { it.type == EnemyType.BASIC })
        assertEquals(4, roster.count { it.type == EnemyType.FAST })
        assertEquals(2, roster.count { it.type == EnemyType.POWER })
    }

    @Test
    fun `accepts the explicit format`() {
        val spec = "B".repeat(10) + "F".repeat(5) + "P".repeat(3) + "A".repeat(2)

        val roster = EnemyRoster.parse(spec, seed = 3)

        assertEquals(20, roster.size)
        assertEquals(10, roster.count { it.type == EnemyType.BASIC })
        assertEquals(2, roster.count { it.type == EnemyType.ARMORED })
    }

    @Test
    fun `marks enemies four eleven and eighteen as flashing`() {
        val roster = EnemyRoster.parse("B*20", seed = 1)

        assertEquals(
            listOf(3, 10, 17),
            roster.indices.filter { roster[it].flashing },
        )
        assertEquals(3, roster.count { it.flashing })
    }

    @Test
    fun `same seed gives the same order`() {
        val first = EnemyRoster.parse("B*10 F*5 P*3 A*2", seed = 99)
        val second = EnemyRoster.parse("B*10 F*5 P*3 A*2", seed = 99)

        assertEquals(first, second)
    }

    @Test
    fun `different seeds can shuffle differently`() {
        val first = EnemyRoster.parse("B*10 F*5 P*3 A*2", seed = 1)
        val second = EnemyRoster.parse("B*10 F*5 P*3 A*2", seed = 2)

        assertTrue(first != second)
    }

    @Test
    fun `rejects a roster that is not twenty enemies`() {
        assertFailsWith<IllegalArgumentException> {
            EnemyRoster.parse("B*19", seed = 1)
        }
    }

    @Test
    fun `rejects unknown enemy types`() {
        assertFailsWith<IllegalArgumentException> {
            EnemyRoster.parse("X*20", seed = 1)
        }
    }

    @Test
    fun `rejects malformed weighted tokens`() {
        assertFailsWith<IllegalArgumentException> {
            EnemyRoster.parse("*20", seed = 1)
        }
        assertFailsWith<IllegalArgumentException> {
            EnemyRoster.parse("B*2*3", seed = 1)
        }
    }

    @Test
    fun `rejects twenty one enemies`() {
        assertFailsWith<IllegalArgumentException> {
            EnemyRoster.parse("B*21", seed = 1)
        }
    }
}
