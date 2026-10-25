package pt.tiago.steeleagle.render

import pt.tiago.steeleagle.core.rules.EnemyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class EnemyColorsTest {

    @Test
    fun `armored tint changes with hit points`() {
        val four = EnemyColors.tint(EnemyType.ARMORED, 4)
        val three = EnemyColors.tint(EnemyType.ARMORED, 3)
        val two = EnemyColors.tint(EnemyType.ARMORED, 2)
        val one = EnemyColors.tint(EnemyType.ARMORED, 1)

        assertNotEquals(four, three)
        assertNotEquals(three, two)
        assertNotEquals(two, one)
    }

    @Test
    fun `enemy types are visually distinct`() {
        assertNotEquals(
            EnemyColors.tint(EnemyType.BASIC, 1),
            EnemyColors.tint(EnemyType.FAST, 1),
        )
        assertNotEquals(
            EnemyColors.tint(EnemyType.BASIC, 1),
            EnemyColors.tint(EnemyType.POWER, 1),
        )
    }

    @Test
    fun `unknown type falls back to the basic tint`() {
        assertEquals(
            EnemyColors.tint(EnemyType.BASIC, 1),
            EnemyColors.tint(null, 1),
        )
    }
}
