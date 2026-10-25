package pt.tiago.steeleagle.core.net

import pt.tiago.steeleagle.core.game.GameEvent
import pt.tiago.steeleagle.core.model.Dir
import pt.tiago.steeleagle.core.world.Terrain
import pt.tiago.steeleagle.core.world.TerrainGrid
import pt.tiago.steeleagle.core.world.WorldSnapshot

/** Wire protocol constants. */
object NetProtocol {
    const val VERSION = 1
    const val DEFAULT_PORT = 47601
    const val SERVICE_TYPE = "_steeleagle._tcp."
    const val HEARTBEAT_MS = 100L
    const val TIMEOUT_MS = 3_000L
    const val STAGE_TIMEOUT_MS = 60_000L

    /** One changed 4 px terrain cell. */
    data class TerrainChange(val index: Int, val terrain: Terrain)

    /** Terrain is sent whole when a stage starts, then only as deltas. */
    data class TerrainState(
        val full: ByteArray?,
        val changes: List<TerrainChange>,
    )

    val TERRAIN_CELLS: Int = TerrainGrid.SIZE * TerrainGrid.SIZE
    const val PLAYER_NONE = 255
}

/** Messages exchanged by the host and the guest. */
sealed interface NetMessage {
    data class Hello(val version: Int, val name: String) : NetMessage
    data class Welcome(
        val version: Int,
        val seed: Int,
        val level: Int,
        val difficulty: Int,
        val players: Int,
    ) : NetMessage

    data class Input(val seq: Int, val direction: Dir?, val fire: Boolean) : NetMessage

    /** A full world snapshot plus the terrain the guest is missing. */
    data class Snapshot(
        val seq: Int,
        val ackInput: Int,
        val state: WorldSnapshot,
        val terrain: NetProtocol.TerrainState,
    ) : NetMessage

    data class Event(val event: GameEvent) : NetMessage
    data class Pause(val paused: Boolean) : NetMessage
    data class Bye(val reason: String) : NetMessage
    data class Ack(val snapshotSeq: Int) : NetMessage
}
