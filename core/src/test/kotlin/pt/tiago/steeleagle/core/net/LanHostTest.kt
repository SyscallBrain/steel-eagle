package pt.tiago.steeleagle.core.net

import pt.tiago.steeleagle.core.model.Dir
import pt.tiago.steeleagle.core.rules.Difficulty
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Host-side socket behaviour that the full integration test does not pin. */
class LanHostTest {

    @Test
    fun `a fire tap between two host ticks still fires once`() {
        withSession { host, client ->
            client.sendInput(Dir.UP, fire = true)
            client.sendInput(Dir.LEFT, fire = false)
            await { host.guestInputSeq() >= 2 }

            val first = host.guestInput()
            assertEquals(Dir.LEFT, first.direction)
            assertTrue(first.fire, "the released tap must be latched")
            assertFalse(host.guestInput().fire, "the latch fires only once")
        }
    }

    @Test
    fun `an idle host keeps the guest connection alive`() {
        withSession(timeoutMs = 400) { _, client ->
            // Nothing is enqueued, as when the host app is in the background.
            Thread.sleep(1_000)
            assertFalse(client.isTimedOut(), "the host heartbeat must reach the guest")
            assertEquals(LanClient.State.CONNECTED, client.state)
        }
    }

    private fun withSession(
        timeoutMs: Long = 5_000,
        body: (LanHost, LanClient) -> Unit,
    ) {
        val host = LanHost(port = 0, timeoutMs = timeoutMs)
        assertTrue(host.start(), "host could not listen")
        val client = LanClient(
            address = "127.0.0.1",
            port = host.localPort,
            timeoutMs = timeoutMs,
        )
        client.start()
        try {
            await { host.state == LanHost.State.CONNECTED }
            host.sendWelcome(seed = 1, level = 1, difficulty = Difficulty.NORMAL.ordinal)
            await { client.state == LanClient.State.CONNECTED }
            body(host, client)
        } finally {
            client.close()
            host.close()
        }
    }

    private fun await(timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(5)
        }
        assertTrue(condition(), "timed out")
    }
}
