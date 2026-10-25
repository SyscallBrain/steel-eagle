package pt.tiago.steeleagle.core.net

import pt.tiago.steeleagle.core.world.Terrain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TerrainDeltaTrackerTest {

    @Test
    fun `changes are sent until acknowledged`() {
        val tracker = TerrainDeltaTracker()
        tracker.add(10, Terrain.BRICK)
        tracker.add(11, Terrain.STEEL)

        val first = tracker.take(1)
        assertEquals(2, first.size)
        assertEquals(2, tracker.size)

        val second = tracker.take(2)
        assertEquals(first, second)

        tracker.ack(2)
        assertEquals(0, tracker.size)
        assertTrue(tracker.take(3).isEmpty())
    }

    @Test
    fun `a late ack only drops the batches it covers`() {
        val tracker = TerrainDeltaTracker()
        tracker.add(10, Terrain.BRICK)
        tracker.take(1)
        tracker.add(20, Terrain.WATER)
        tracker.take(2)
        tracker.add(30, Terrain.ICE)

        tracker.ack(1)

        val remaining = tracker.take(3)
        assertEquals(setOf(20, 30), remaining.map { it.index }.toSet())
        assertEquals(Terrain.WATER, remaining.first { it.index == 20 }.terrain)
    }

    @Test
    fun `the latest terrain for a cell wins`() {
        val tracker = TerrainDeltaTracker()
        tracker.add(10, Terrain.BRICK)
        tracker.add(10, Terrain.EMPTY)

        val changes = tracker.take(1)

        assertEquals(1, changes.size)
        assertEquals(Terrain.EMPTY, changes[0].terrain)
    }

    @Test
    fun `pending and unacked changes merge without duplicates`() {
        val tracker = TerrainDeltaTracker()
        tracker.add(1, Terrain.BRICK)
        tracker.take(1)
        tracker.add(1, Terrain.EMPTY)
        tracker.add(2, Terrain.ICE)

        val changes = tracker.take(2)

        assertEquals(2, changes.size)
        assertEquals(Terrain.EMPTY, changes.first { it.index == 1 }.terrain)
        assertEquals(Terrain.ICE, changes.first { it.index == 2 }.terrain)
    }
}
