package pt.tiago.steeleagle.core.model

import kotlin.math.abs

/** The four movement directions, with screen-space deltas. */
enum class Dir(val dx: Int, val dy: Int) {
    UP(0, -1),
    RIGHT(1, 0),
    DOWN(0, 1),
    LEFT(-1, 0),
    ;

    fun opposite(): Dir = when (this) {
        UP -> DOWN
        RIGHT -> LEFT
        DOWN -> UP
        LEFT -> RIGHT
    }

    companion object {
        /** Quantizes a vector to the dominant axis; horizontal wins ties. */
        fun fromVector(dx: Int, dy: Int): Dir? = when {
            dx == 0 && dy == 0 -> null
            abs(dx) >= abs(dy) -> if (dx > 0) RIGHT else LEFT
            else -> if (dy > 0) DOWN else UP
        }
    }
}
