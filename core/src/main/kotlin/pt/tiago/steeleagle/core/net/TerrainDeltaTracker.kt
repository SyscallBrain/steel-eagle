package pt.tiago.steeleagle.core.net

import pt.tiago.steeleagle.core.world.Terrain

/**
 * Terrain changes that have not been acknowledged yet. Every snapshot carries
 * all unacknowledged changes, so a guest that skips intermediate snapshots
 * still converges; an acknowledgement only drops the batches it covers.
 */
class TerrainDeltaTracker {

    private data class Batch(
        val seq: Int,
        val changes: Map<Int, Terrain>,
    )

    private val pending = LinkedHashMap<Int, Terrain>()
    private val unacked = ArrayDeque<Batch>()

    fun add(index: Int, terrain: Terrain) {
        pending[index] = terrain
    }

    fun addAll(changes: List<Pair<Int, Terrain>>) {
        for ((index, terrain) in changes) pending[index] = terrain
    }

    /** Moves pending changes into a batch and returns everything unacked. */
    fun take(seq: Int): List<NetProtocol.TerrainChange> {
        if (pending.isNotEmpty()) {
            unacked.addLast(Batch(seq, LinkedHashMap(pending)))
            pending.clear()
        }
        if (unacked.isEmpty()) return emptyList()
        val merged = LinkedHashMap<Int, Terrain>()
        for (batch in unacked) {
            for ((index, terrain) in batch.changes) merged[index] = terrain
        }
        return merged.map { NetProtocol.TerrainChange(it.key, it.value) }
    }

    fun ack(seq: Int) {
        while (unacked.isNotEmpty() && unacked.first().seq <= seq) {
            unacked.removeFirst()
        }
    }

    val size: Int
        get() = pending.size + unacked.sumOf { it.changes.size }

    fun reset() {
        pending.clear()
        unacked.clear()
    }
}
