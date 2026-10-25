package pt.tiago.steeleagle.core.rules

import pt.tiago.steeleagle.core.model.Fixed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EnemyTypeTest {

    @Test
    fun `points match the plan`() {
        assertEquals(100, EnemyType.BASIC.points)
        assertEquals(200, EnemyType.FAST.points)
        assertEquals(300, EnemyType.POWER.points)
        assertEquals(400, EnemyType.ARMORED.points)
    }

    @Test
    fun `armored has four hit points`() {
        assertEquals(4, EnemyType.ARMORED.hitPoints)
        assertEquals(1, EnemyType.BASIC.hitPoints)
    }

    @Test
    fun `fast is faster than basic`() {
        assertTrue(EnemyType.FAST.speed > EnemyType.BASIC.speed)
    }

    @Test
    fun `power fires faster bullets`() {
        assertTrue(EnemyType.POWER.bulletSpeed > EnemyType.BASIC.bulletSpeed)
        assertEquals(Fixed.BULLET_SPEED, EnemyType.BASIC.bulletSpeed)
    }

    @Test
    fun `speedFor applies the difficulty multiplier`() {
        assertEquals(7, EnemyType.BASIC.speedFor(Difficulty.EASY))
        assertEquals(8, EnemyType.BASIC.speedFor(Difficulty.NORMAL))
        assertEquals(9, EnemyType.BASIC.speedFor(Difficulty.HARD))
        assertEquals(18, EnemyType.FAST.speedFor(Difficulty.HARD))
    }

    @Test
    fun `aligned fire chance is higher`() {
        for (type in EnemyType.entries) {
            assertTrue(type.alignedFireChancePerMille > type.fireChancePerMille)
        }
    }
}
