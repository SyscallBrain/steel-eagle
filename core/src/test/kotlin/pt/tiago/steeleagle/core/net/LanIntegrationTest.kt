package pt.tiago.steeleagle.core.net

import pt.tiago.steeleagle.core.level.Level
import pt.tiago.steeleagle.core.model.Dir
import pt.tiago.steeleagle.core.model.PlayerInput
import pt.tiago.steeleagle.core.rules.Difficulty
import pt.tiago.steeleagle.core.world.InputFrame
import pt.tiago.steeleagle.core.world.Terrain
import pt.tiago.steeleagle.core.world.World
import pt.tiago.steeleagle.core.world.WorldState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The phase 12 acceptance test that does not need two phones: a real host and
 * guest over TCP on localhost, 600 ticks of scripted input, snapshot and
 * terrain convergence, pause and disconnect.
 */
class LanIntegrationTest {

    @Test
    fun `host and guest stay in sync over a real socket`() {
        val host = LanHost(port = 0, timeoutMs = 5_000)
        assertTrue(host.start(), "host could not listen")
        val client = LanClient(
            address = "127.0.0.1",
            port = host.localPort,
            name = "GUEST",
            timeoutMs = 5_000,
        )
        client.start()

        try {
            await(5_000) { host.state == LanHost.State.CONNECTED }
            host.sendWelcome(seed = 1, level = 1, difficulty = Difficulty.NORMAL.ordinal)
            await(5_000) { client.welcome != null }

            val welcome = assertNotNull(client.welcome)
            assertEquals(NetProtocol.VERSION, welcome.version)
            assertEquals(1, welcome.level)
            assertEquals(2, welcome.players)

            val level = level()
            val hostWorld = World.fromLevel(
                level,
                seed = 1,
                difficulty = Difficulty.NORMAL,
                playerCount = 2,
            )
            val guestWorld = World.fromLevel(
                level,
                seed = 1,
                difficulty = Difficulty.NORMAL,
                playerCount = 2,
            )
            hostWorld.terrain.set8(4, 4, Terrain.BRICK)
            guestWorld.terrain.set8(4, 4, Terrain.BRICK)

            val tracker = TerrainDeltaTracker()
            var lastSeq = 0
            var lastApplied = -1

            for (tick in 0 until 600) {
                val guestInput = host.guestInput()
                val p1 = when {
                    tick % 120 < 60 -> PlayerInput(Dir.RIGHT, fire = tick % 20 == 0)
                    else -> PlayerInput(Dir.LEFT, fire = tick % 30 == 0)
                }
                hostWorld.step(InputFrame(p1, guestInput))
                hostWorld.player.shieldTicks = 1_000
                hostWorld.player2!!.shieldTicks = 1_000
                for (event in hostWorld.events) host.sendEvent(event)
                hostWorld.events.clear()
                for (index in hostWorld.terrain.drainChanges()) {
                    tracker.add(index, hostWorld.terrain.terrainAt(index))
                }
                if (tick == 300) {
                    hostWorld.terrain.destroyBrick(8, 8, Dir.DOWN)
                }

                if (tick % 5 == 0) {
                    lastSeq++
                    host.sendSnapshot(
                        hostWorld.captureSnapshot(),
                        NetProtocol.TerrainState(
                            full = if (tick == 0) hostWorld.terrain.toBytes() else null,
                            changes = tracker.take(lastSeq),
                        ),
                    )
                }

                val p2 = PlayerInput(
                    direction = if (tick % 40 < 20) Dir.LEFT else Dir.UP,
                    fire = tick % 17 == 0,
                )
                client.sendInput(p2.direction, p2.fire)

                for (snapshot in client.drainSnapshots()) {
                    applyTerrain(guestWorld, snapshot.terrain)
                    guestWorld.applySnapshot(snapshot.state)
                    client.sendAck(snapshot.seq)
                    lastApplied = snapshot.seq
                }
                if (host.lastAck >= 0) tracker.ack(host.lastAck)
                Thread.sleep(1)
            }

            lastSeq++
            host.sendSnapshot(
                hostWorld.captureSnapshot(),
                NetProtocol.TerrainState(null, tracker.take(lastSeq)),
            )
            await(5_000) {
                for (snapshot in client.drainSnapshots()) {
                    applyTerrain(guestWorld, snapshot.terrain)
                    guestWorld.applySnapshot(snapshot.state)
                    client.sendAck(snapshot.seq)
                    lastApplied = snapshot.seq
                }
                lastApplied >= lastSeq
            }

            assertEquals(hostWorld.captureSnapshot(), guestWorld.captureSnapshot())
            assertEquals(hostWorld.terrain.contentHash(), guestWorld.terrain.contentHash())
            assertEquals(WorldState.PLAYING, guestWorld.state)
            assertEquals(hostWorld.tick, guestWorld.tick)
            assertEquals(2, guestWorld.playerCount)

            client.sendPause(true)
            await(5_000) { host.remotePaused }
            host.sendPause(true)
            await(5_000) { client.remotePaused }

            host.sendBye("HOST LEFT")
            await(5_000) { client.byeReason != null }
            assertEquals("HOST LEFT", client.byeReason)
        } finally {
            client.close()
            host.close()
        }
    }

    @Test
    fun `heartbeats keep the peer alive and frozen peers time out`() {
        val host = LanHost(port = 0, timeoutMs = 5_000)
        assertTrue(host.start())
        val client = LanClient("127.0.0.1", host.localPort, "QUIET", timeoutMs = 5_000)
        client.start()
        try {
            await(5_000) { host.state == LanHost.State.CONNECTED }
            // The client's heartbeat thread keeps sending even without gameplay.
            Thread.sleep(200)
            assertTrue(!host.isGuestTimedOut())
            assertTrue(host.isGuestTimedOut(System.currentTimeMillis() + 10_000))

            host.sendWelcome(seed = 1, level = 1, difficulty = Difficulty.NORMAL.ordinal)
            await(5_000) { client.welcome != null }
            assertTrue(!client.isTimedOut())
            assertTrue(client.isTimedOut(System.currentTimeMillis() + 10_000))
        } finally {
            client.close()
            host.close()
        }
    }

    @Test
    fun `a guest that skips snapshots still converges`() {
        val host = LanHost(port = 0, timeoutMs = 5_000)
        assertTrue(host.start())
        val client = LanClient("127.0.0.1", host.localPort, "SKIP", timeoutMs = 5_000)
        client.start()
        try {
            await(5_000) { host.state == LanHost.State.CONNECTED }
            host.sendWelcome(seed = 3, level = 1, difficulty = Difficulty.NORMAL.ordinal)
            await(5_000) { client.welcome != null }

            val level = level()
            val hostWorld = World.fromLevel(level, seed = 3, playerCount = 2)
            val guestWorld = World.fromLevel(level, seed = 3, playerCount = 2)
            hostWorld.terrain.set8(6, 6, Terrain.BRICK)
            guestWorld.terrain.set8(6, 6, Terrain.BRICK)
            val tracker = TerrainDeltaTracker()
            var lastSeq = 0
            var applied = -1

            for (tick in 0 until 240) {
                hostWorld.step(
                    InputFrame(
                        PlayerInput(Dir.RIGHT, fire = tick % 9 == 0),
                        PlayerInput(Dir.LEFT, fire = tick % 13 == 0),
                    ),
                )
                hostWorld.player.shieldTicks = 1_000
                hostWorld.player2!!.shieldTicks = 1_000
                if (tick % 30 == 0) hostWorld.terrain.destroyBrick(12, 12, Dir.DOWN)
                for (index in hostWorld.terrain.drainChanges()) {
                    tracker.add(index, hostWorld.terrain.terrainAt(index))
                }
                lastSeq++
                host.sendSnapshot(
                    hostWorld.captureSnapshot(),
                    NetProtocol.TerrainState(
                        full = if (tick == 0) hostWorld.terrain.toBytes() else null,
                        changes = tracker.take(lastSeq),
                    ),
                )
                if (host.lastAck >= 0) tracker.ack(host.lastAck)
                Thread.sleep(2)

                if (tick % 20 == 0) {
                    // Apply only the newest buffered snapshot, like a slow frame.
                    val pending = client.drainSnapshots()
                    val newest = pending.lastOrNull() ?: continue
                    applyTerrain(guestWorld, newest.terrain)
                    guestWorld.applySnapshot(newest.state)
                    client.sendAck(newest.seq)
                    applied = newest.seq
                }
            }

            lastSeq++
            host.sendSnapshot(
                hostWorld.captureSnapshot(),
                NetProtocol.TerrainState(null, tracker.take(lastSeq)),
            )
            await(5_000) {
                val newest = client.drainSnapshots().lastOrNull() ?: return@await false
                applyTerrain(guestWorld, newest.terrain)
                guestWorld.applySnapshot(newest.state)
                client.sendAck(newest.seq)
                applied = newest.seq
                applied >= lastSeq
            }

            assertEquals(hostWorld.captureSnapshot(), guestWorld.captureSnapshot())
            assertEquals(hostWorld.terrain.contentHash(), guestWorld.terrain.contentHash())
        } finally {
            client.close()
            host.close()
        }
    }

    private fun applyTerrain(world: World, terrain: NetProtocol.TerrainState) {
        terrain.full?.let { world.terrain.applyFull(it) }
        for (change in terrain.changes) {
            world.terrain.setAt(change.index, change.terrain)
        }
    }

    private fun await(timeoutMs: Long, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(5)
        }
        assertTrue(condition(), "timed out after ${timeoutMs}ms")
    }

    private fun level(): Level {
        val map = MutableList(26) { ".".repeat(26).toCharArray() }
        for (y in 22..25) {
            for (x in 10..15) map[y][x] = 'S'
        }
        return Level(
            name = "NET TEST",
            enemies = "B*20",
            map = map.map { String(it) },
        )
    }
}
