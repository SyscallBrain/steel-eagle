package pt.tiago.steeleagle.core.editor

import pt.tiago.steeleagle.core.rules.EnemyRoster
import pt.tiago.steeleagle.core.rules.EnemyType

/**
 * The 20 enemy slots of a level, split by type. The editor keeps the total at
 * [TOTAL]; a full roster is required before a level can be saved.
 */
class EnemySetup private constructor(
    private val counts: IntArray,
) {
    init {
        require(counts.size == EnemyType.entries.size) { "one count per enemy type" }
    }

    fun count(type: EnemyType): Int = counts[type.ordinal]

    val total: Int
        get() = counts.sum()

    val remaining: Int
        get() = (TOTAL - total).coerceAtLeast(0)

    /**
     * Adds [delta] to one type. Counts never go negative and the roster never
     * exceeds 20; returns true when something changed.
     */
    fun adjust(type: EnemyType, delta: Int): Boolean {
        if (delta == 0) return false
        val current = counts[type.ordinal]
        val next = if (delta > 0) {
            minOf(current + delta, current + remaining)
        } else {
            (current + delta).coerceAtLeast(0)
        }
        if (next == current) return false
        counts[type.ordinal] = next
        return true
    }

    /** Compact `enemies:` spec, e.g. `B*14 F*4 P*2`. */
    fun spec(): String = EnemyType.entries
        .mapNotNull { type ->
            val count = count(type)
            if (count == 0) null else "${codeOf(type)}*$count"
        }
        .joinToString(" ")

    fun copy(): EnemySetup = EnemySetup(counts.copyOf())

    companion object {
        const val TOTAL = EnemyRoster.TOTAL

        fun allBasic(): EnemySetup = EnemySetup(
            IntArray(EnemyType.entries.size) { index ->
                if (index == EnemyType.BASIC.ordinal) TOTAL else 0
            },
        )

        /** Accepts a partial spec so a damaged file can be repaired in the editor. */
        fun fromSpec(spec: String): EnemySetup {
            val counts = IntArray(EnemyType.entries.size)
            EnemyRoster.expandTypes(spec).forEach { counts[it.ordinal]++ }
            return EnemySetup(counts)
        }

        /** Level-format code, matching [EnemyRoster.typeFor]. */
        fun codeOf(type: EnemyType): Char = when (type) {
            EnemyType.BASIC -> 'B'
            EnemyType.FAST -> 'F'
            EnemyType.POWER -> 'P'
            EnemyType.ARMORED -> 'A'
        }
    }
}
