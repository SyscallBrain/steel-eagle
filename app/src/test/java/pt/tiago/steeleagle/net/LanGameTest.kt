package pt.tiago.steeleagle.net

import pt.tiago.steeleagle.core.level.Level
import pt.tiago.steeleagle.core.model.Dir
import pt.tiago.steeleagle.core.model.PlayerInput
import pt.tiago.steeleagle.core.net.NetMessage
import pt.tiago.steeleagle.core.rules.Difficulty
import pt.tiago.steeleagle.core.world.Terrain
import pt.tiago.steeleagle.core.world.World
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * App-side host pacing, acknowledgement pruning and full-terrain resets over
 * real sockets (no Android dependencies in [LanGame]).
 */
class LanGameTest {

    @Test
    fun `the host sends a full terrain, then only acknowledged deltas`() {
        val host = LanGame(port = 0)
        val guest = LanGame(port = 0)
        assertTrue(host.startHost())
        val port = host.hostPort
        assertTrue("no bound port", port > 0)
        assertTrue(guest.startJoin("127.0.0.1", port))
        try {
            await { host.isHostConnected() }
            host.sendWelcome(level = 1, difficulty = Difficulty.NORMAL.ordinal)

            val world = World.fromLevel(
                Level("NET", "B*20", List(26) { ".".repeat(26) }),
                playerCount = 2,
            )

            host.publish(world)
            val first = awaitSnapshot(guest)
            assertTrue("first snapshot must carry the full terrain", first.terrain.full != null)
            assertTrue(first.terrain.changes.isEmpty())
            guest.ackGuest(first.seq)
            Thread.sleep(100)

            world.terrain.set8(4, 4, Terrain.BRICK)
            host.publish(world)
            host.publish(world)
            val second = awaitSnapshot(guest)
            assertNull("deltas only after the full state", second.terrain.full)
            assertEquals(4, second.terrain.changes.size)
            guest.ackGuest(second.seq)

            Thread.sleep(100)
            host.publish(world)
            host.publish(world)
            val third = awaitSnapshot(guest)
            assertTrue("acked changes are not resent", third.terrain.changes.isEmpty())

            world.terrain.set8(8, 8, Terrain.STEEL)
            host.requestFullTerrain()
            host.publish(world)
            host.publish(world)
            val fourth = awaitSnapshot(guest)
            assertTrue("a new stage forces a full terrain", fourth.terrain.full != null)
            assertTrue(fourth.terrain.changes.isEmpty())
        } finally {
            guest.stop()
            host.stop()
        }
    }

    @Test
    fun `a full terrain is resent until the guest acknowledges that snapshot`() {
        val host = LanGame(port = 0)
        val guest = LanGame(port = 0)
        assertTrue(host.startHost())
        assertTrue(guest.startJoin("127.0.0.1", host.hostPort))
        try {
            await { host.isHostConnected() }
            host.sendWelcome(level = 1, difficulty = Difficulty.NORMAL.ordinal)
            val world = World.fromLevel(
                Level("NET", "B*20", List(26) { ".".repeat(26) }),
                playerCount = 2,
            )

            host.publish(world)
            val first = awaitSnapshot(guest)
            guest.ackGuest(first.seq)
            Thread.sleep(100)
            host.publish(world)
            host.publish(world)
            val older = awaitSnapshot(guest)
            assertNull(older.terrain.full)

            host.requestFullTerrain()
            host.publish(world)
            host.publish(world)
            val stage = awaitSnapshot(guest)
            assertTrue(stage.terrain.full != null)

            // An ack still in flight for a snapshot sent before the full state.
            guest.ackGuest(older.seq)
            Thread.sleep(100)
            host.publish(world)
            host.publish(world)
            val resent = awaitSnapshot(guest)
            assertTrue("a stale ack must not drop the full terrain", resent.terrain.full != null)

            guest.ackGuest(resent.seq)
            Thread.sleep(100)
            host.publish(world)
            host.publish(world)
            assertNull(awaitSnapshot(guest).terrain.full)
        } finally {
            guest.stop()
            host.stop()
        }
    }

    @Test
    fun `the guest input reaches the host`() {
        val host = LanGame(port = 0)
        val guest = LanGame(port = 0)
        assertTrue(host.startHost())
        assertTrue(guest.startJoin("127.0.0.1", host.hostPort))
        try {
            await { host.isHostConnected() }
            host.sendWelcome(level = 1, difficulty = Difficulty.NORMAL.ordinal)
            await { guest.guestWelcome() != null }

            guest.sendInput(PlayerInput(Dir.LEFT, fire = true))

            await { host.guestInput().direction == Dir.LEFT && host.guestInput().fire }
            assertEquals(Dir.LEFT, host.guestInput().direction)
        } finally {
            guest.stop()
            host.stop()
        }
    }

    private fun await(timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(5)
        }
        assertTrue("timed out", condition())
    }

    private fun awaitSnapshot(guest: LanGame): NetMessage.Snapshot {
        var snapshot: NetMessage.Snapshot? = null
        await {
            snapshot = guest.drainGuestSnapshots().lastOrNull() ?: snapshot
            snapshot != null
        }
        return snapshot!!
    }
}
