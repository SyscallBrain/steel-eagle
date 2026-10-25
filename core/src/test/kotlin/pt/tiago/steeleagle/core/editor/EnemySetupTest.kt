package pt.tiago.steeleagle.core.editor

import pt.tiago.steeleagle.core.rules.EnemyType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EnemySetupTest {

    @Test
    fun `a new setup has twenty basic enemies`() {
        val setup = EnemySetup.allBasic()

        assertEquals(20, setup.total)
        assertEquals(0, setup.remaining)
        assertEquals(20, setup.count(EnemyType.BASIC))
        assertEquals("B*20", setup.spec())
    }

    @Test
    fun `the roster always adds up to twenty`() {
        val setup = EnemySetup.allBasic()

        setup.adjust(EnemyType.BASIC, -14)
        assertTrue(setup.adjust(EnemyType.FAST, 4))
        assertTrue(setup.adjust(EnemyType.POWER, 2))
        assertTrue(setup.adjust(EnemyType.ARMORED, 8))

        assertEquals(20, setup.total)
        assertEquals("B*6 F*4 P*2 A*8", setup.spec())
        assertFalse(setup.adjust(EnemyType.FAST, 1))
        assertEquals(20, setup.total)
    }

    @Test
    fun `counts never go negative or above twenty`() {
        val setup = EnemySetup.allBasic()

        assertTrue(setup.adjust(EnemyType.BASIC, -20))
        assertEquals(0, setup.count(EnemyType.BASIC))
        assertFalse(setup.adjust(EnemyType.BASIC, -1))

        assertTrue(setup.adjust(EnemyType.ARMORED, 20))
        assertEquals(20, setup.count(EnemyType.ARMORED))
        assertFalse(setup.adjust(EnemyType.ARMORED, 1))
        assertEquals(20, setup.total)
    }

    @Test
    fun `a spec can be parsed back into counts`() {
        val setup = EnemySetup.fromSpec("B*14 F*4 P*2")

        assertEquals(14, setup.count(EnemyType.BASIC))
        assertEquals(4, setup.count(EnemyType.FAST))
        assertEquals(2, setup.count(EnemyType.POWER))
        assertEquals(0, setup.count(EnemyType.ARMORED))
        assertEquals(20, setup.total)
        assertEquals("B*14 F*4 P*2", setup.spec())
    }

    @Test
    fun `an explicit roster is accepted too`() {
        val setup = EnemySetup.fromSpec("B" + "F".repeat(19))

        assertEquals(1, setup.count(EnemyType.BASIC))
        assertEquals(19, setup.count(EnemyType.FAST))
        assertEquals(20, setup.total)
        assertEquals("B*1 F*19", setup.spec())
    }

    @Test
    fun `a partial roster keeps its counts for repair`() {
        val setup = EnemySetup.fromSpec("BFF")

        assertEquals(1, setup.count(EnemyType.BASIC))
        assertEquals(2, setup.count(EnemyType.FAST))
        assertEquals(3, setup.total)
        assertEquals(17, setup.remaining)
    }
}
