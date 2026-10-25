package pt.tiago.steeleagle.net

import pt.tiago.steeleagle.core.game.GameEvent
import pt.tiago.steeleagle.core.model.PlayerInput
import pt.tiago.steeleagle.core.net.LanClient
import pt.tiago.steeleagle.core.net.LanHost
import pt.tiago.steeleagle.core.net.NetMessage
import pt.tiago.steeleagle.core.net.NetProtocol
import pt.tiago.steeleagle.core.net.TerrainDeltaTracker
import pt.tiago.steeleagle.core.world.TerrainGrid
import pt.tiago.steeleagle.core.world.World

enum class LanRole {
    NONE,
    HOST,
    GUEST,
}

/**
 * App-side plumbing around the socket classes: host snapshot pacing, terrain
 * deltas with acknowledgements, guest snapshot/event delivery and timers.
 */
class LanGame(private val port: Int = NetProtocol.DEFAULT_PORT) {

    @Volatile
    var role: LanRole = LanRole.NONE
        private set

    var lastError: String? = null
        private set

    private var host: LanHost? = null
    private var client: LanClient? = null
    private val tracker = TerrainDeltaTracker()
    private var lastAck = -1
    private var ticks = 0
    private var needsFullTerrain = true
    private var fullTerrain: ByteArray? = null
    private var fullTerrainSeq = NO_SEQ
    private var lastConnection = 0

    val hostPort: Int
        get() = host?.localPort ?: port

    fun startHost(): Boolean {
        stop()
        role = LanRole.HOST
        var server = LanHost(port)
        var started = server.start()
        if (!started) {
            // A previous session may still be closing its socket on a worker.
            server.close()
            Thread.sleep(REBIND_DELAY_MS)
            server = LanHost(port)
            started = server.start()
        }
        if (!started) {
            lastError = server.failure ?: "PORT BUSY"
            server.close()
            role = LanRole.NONE
            return false
        }
        host = server
        needsFullTerrain = true
        fullTerrain = null
        fullTerrainSeq = NO_SEQ
        lastConnection = 0
        lastError = null
        return true
    }

    fun startJoin(address: String, targetPort: Int = port): Boolean {
        stop()
        role = LanRole.GUEST
        val guest = LanClient(address = address, port = targetPort, name = "PLAYER 2")
        client = guest
        guest.start()
        lastError = null
        return true
    }

    /**
     * Closes the session on a worker thread so a stalled peer can never block
     * the render thread; the Bye is written before the sockets close.
     */
    fun end(reason: String) {
        val server = host
        val guest = client
        host = null
        client = null
        role = LanRole.NONE
        lastAck = -1
        ticks = 0
        needsFullTerrain = true
        fullTerrain = null
        fullTerrainSeq = NO_SEQ
        lastConnection = 0
        if (server == null && guest == null) return
        Thread({
            // Never let a stalled write keep the port or threads alive.
            val watchdog = Thread({
                try {
                    Thread.sleep(END_WATCHDOG_MS)
                } catch (_: InterruptedException) {
                    return@Thread
                }
                server?.close()
                guest?.close()
            }, "steeleagle-lan-close").apply {
                isDaemon = true
                start()
            }
            try {
                server?.sendByeNow(reason)
            } catch (_: Exception) {
                Unit
            }
            try {
                guest?.sendByeNow(reason)
            } catch (_: Exception) {
                Unit
            }
            watchdog.interrupt()
            server?.close()
            guest?.close()
        }, "steeleagle-lan-end").apply {
            isDaemon = true
            start()
        }
    }

    fun sendEvent(event: GameEvent) {
        host?.sendEvent(event)
    }

    fun stop() {
        host?.close()
        client?.close()
        host = null
        client = null
        role = LanRole.NONE
        lastAck = -1
        ticks = 0
        needsFullTerrain = true
        fullTerrain = null
        fullTerrainSeq = NO_SEQ
        lastConnection = 0
    }

    // ------------------------------------------------------------------ host

    fun isHostConnected(): Boolean = host?.state == LanHost.State.CONNECTED

    fun hostTimedOut(): Boolean = host?.isGuestTimedOut() == true

    fun hostRemotePaused(): Boolean = host?.remotePaused == true

    fun hostByeReason(): String? = host?.byeReason

    fun hostConnectionCount(): Int = host?.connectionCount ?: 0

    /** Restarts both silence windows after the app returns from the background. */
    fun resetTimeouts() {
        host?.resetTimeout()
        client?.resetTimeout()
    }

    fun guestInput(): PlayerInput = host?.guestInput() ?: PlayerInput.NONE

    fun sendWelcome(level: Int, difficulty: Int) {
        host?.sendWelcome(seed = HOST_SEED, level = level, difficulty = difficulty)
    }

    /** The next snapshot carries the full terrain (new stage or new guest). */
    fun requestFullTerrain() {
        needsFullTerrain = true
    }

    fun sendPause(paused: Boolean) {
        host?.sendPause(paused)
        client?.sendPause(paused)
    }

    /** Sends a snapshot every second tick (30 Hz) while the guest is connected. */
    fun publish(world: World) {
        val server = host ?: return
        if (server.state != LanHost.State.CONNECTED) return
        if (server.connectionCount != lastConnection) {
            lastConnection = server.connectionCount
            tracker.reset()
            lastAck = -1
            needsFullTerrain = true
        }
        if (server.lastAck != lastAck) {
            tracker.ack(server.lastAck)
            lastAck = server.lastAck
            // Only an ack of the snapshot that carried the full state (or a
            // later one) proves the guest has it; older acks are in flight.
            if (fullTerrainSeq != NO_SEQ && lastAck >= fullTerrainSeq) {
                fullTerrain = null
                fullTerrainSeq = NO_SEQ
            }
        }
        for (index in world.terrain.drainChanges()) {
            tracker.add(index, world.terrain.terrainAt(index))
        }
        if (ticks++ % SNAPSHOT_EVERY_TICKS != 0) return
        if (needsFullTerrain) {
            // Capture once, keep it until acknowledged, and drop stale deltas:
            // the full state already contains every cell of the new stage.
            fullTerrain = world.terrain.toBytes()
            fullTerrainSeq = server.nextSnapshotSeq
            tracker.reset()
            needsFullTerrain = false
        }
        val full = fullTerrain
        val changes = tracker.take(server.nextSnapshotSeq)
        server.sendSnapshot(
            world.captureSnapshot(),
            NetProtocol.TerrainState(full, changes),
        )
    }

    // ----------------------------------------------------------------- guest

    fun guestState(): LanClient.State? = client?.state

    fun guestWelcome(): NetMessage.Welcome? = client?.welcome

    fun guestFailure(): String? = client?.failure ?: lastError

    fun guestBye(): String? = client?.byeReason

    fun guestTimedOut(): Boolean = client?.isTimedOut() == true

    fun guestRemotePaused(): Boolean = client?.remotePaused == true

    fun sendInput(input: PlayerInput) {
        client?.sendInput(input.direction, input.fire)
    }

    fun drainGuestSnapshots(): List<NetMessage.Snapshot> =
        client?.drainSnapshots().orEmpty()

    fun drainGuestEvents(): List<GameEvent> = client?.drainEvents().orEmpty()

    fun ackGuest(snapshotSeq: Int) {
        client?.sendAck(snapshotSeq)
    }

    fun applyTerrain(grid: TerrainGrid, terrain: NetProtocol.TerrainState) {
        terrain.full?.let { grid.applyFull(it) }
        for (change in terrain.changes) {
            grid.setAt(change.index, change.terrain)
        }
    }

    private companion object {
        const val NO_SEQ = Int.MAX_VALUE
        const val SNAPSHOT_EVERY_TICKS = 2
        const val HOST_SEED = 1
        const val REBIND_DELAY_MS = 150L
        const val END_WATCHDOG_MS = 250L
    }
}
